package com.kybers.play.data.repository

import android.util.Log
import com.google.gson.Gson
import com.kybers.play.BuildConfig
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.MovieDetailsCacheDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.model.MovieWithDetails
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.kybers.play.util.XmlTvParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CleanedTitle(val title: String, val year: String?)

data class ActorFilmography(
    val biography: String?,
    val availableMovies: List<Movie>,
    val unavailableMovies: List<TMDbMovieResult>
)

// Regex mejoradas para una limpieza más precisa
private val yearRegex = """\b((19|20)\d{2})\b""".toRegex()
private val parensRegex = """\s*\(.*?\)""".toRegex()
private val bracketsRegex = """\s*\[.*?]""".toRegex()
private val tagsRegex = """\b(HD|4K|FHD|HDTS|1080p|720p|DUAL|LAT(INO)?|SUB(TITULADO)?|VOS|ES|ENG|HEVC|X265|X264)\b""".toRegex(RegexOption.IGNORE_CASE)
private val spacesRegex = """\s+""".toRegex()

open class ContentRepository(
    private val xtreamApiService: XtreamApiService,
    private val tmdbApiService: ExternalApiService,
    private val omdbApiService: ExternalApiService,
    private val liveStreamDao: LiveStreamDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val epgEventDao: EpgEventDao,
    private val movieDetailsCacheDao: MovieDetailsCacheDao,
    private val baseUrl: String
) {
    private val gson = Gson()

    suspend fun getMovieDetails(movie: Movie): MovieWithDetails {
        val cacheExpiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val cachedDetails = movieDetailsCacheDao.getByStreamId(movie.streamId)

        if (cachedDetails != null && cachedDetails.lastUpdated > cacheExpiry) {
            return MovieWithDetails(movie, cachedDetails)
        }

        val cleaned = cleanMovieTitle(movie.name)
        val tmdbId = findTMDbId(cleaned.title, cleaned.year)

        val newDetails = if (tmdbId != null) {
            fetchFromTMDbById(movie.streamId, tmdbId)
        } else {
            null
        }

        newDetails?.let { movieDetailsCacheDao.insertOrUpdate(it) }
        return MovieWithDetails(movie, newDetails)
    }

    fun cleanMovieTitle(title: String): CleanedTitle {
        val yearMatch = yearRegex.find(title)
        val year = yearMatch?.value

        var tempTitle = title

        if (year != null) {
            tempTitle = tempTitle.replace(year, "")
        }

        var cleanedTitle = tempTitle
            .replace(parensRegex, "")
            .replace(bracketsRegex, "")
            .replace(tagsRegex, "")

        if (cleanedTitle.contains(": ")) {
            cleanedTitle = cleanedTitle.substringBefore(": ")
        }

        cleanedTitle = cleanedTitle.trim().replace(spacesRegex, " ")

        return CleanedTitle(cleanedTitle, year)
    }


    private suspend fun findTMDbId(cleanedTitle: String, year: String?): Int? {
        try {
            val response = tmdbApiService.searchMovieTMDb(
                apiKey = BuildConfig.TMDB_API_KEY,
                movieTitle = cleanedTitle,
                year = year
            )
            if (response.isSuccessful) {
                return response.body()?.results?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo en la búsqueda de TMDb para '$cleanedTitle': ${e.message}")
        }
        return null
    }

    private suspend fun fetchFromTMDbById(streamId: Int, tmdbId: Int): MovieDetailsCache? {
        try {
            val response = tmdbApiService.getMovieDetailsTMDb(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) {
                val details = response.body()
                if (details != null) {
                    val castList: List<TMDbCastMember> = details.credits?.cast?.take(10) ?: emptyList()
                    val recommendations: List<TMDbMovieResult> = details.recommendations?.results?.take(10) ?: emptyList()

                    return MovieDetailsCache(
                        streamId = streamId, tmdbId = tmdbId, plot = details.overview,
                        posterUrl = details.getFullPosterUrl(), backdropUrl = details.getFullBackdropUrl(),
                        releaseYear = details.releaseDate?.substringBefore("-"), rating = details.voteAverage,
                        castJson = gson.toJson(castList), recommendationsJson = gson.toJson(recommendations),
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo al obtener detalles de TMDb para ID $tmdbId: ${e.message}")
        }
        return null
    }

    suspend fun findMoviesByTMDbResults(tmdbMovies: List<TMDbMovieResult>, allLocalMovies: List<Movie>): List<Movie> = coroutineScope {
        val localMoviesCleaned = allLocalMovies.map { it to cleanMovieTitle(it.name) }
        val matchJobs = tmdbMovies.map { tmdbMovie ->
            async {
                findLocalMovieMatches(tmdbMovie, localMoviesCleaned)
            }
        }
        // ¡NUEVO! Filtramos por link único (streamId + extension)
        matchJobs.awaitAll().flatten().distinctBy { "${it.streamId}.${it.containerExtension}" }
    }

    private suspend fun findLocalMovieMatches(tmdbMovie: TMDbMovieResult, localMoviesCleaned: List<Pair<Movie, CleanedTitle>>): List<Movie> {
        try {
            val detailsResponse = tmdbApiService.getMovieDetailsTMDb(
                movieId = tmdbMovie.id,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (detailsResponse.isSuccessful) {
                val details = detailsResponse.body() ?: return emptyList()
                val allPossibleTitles = mutableSetOf<String>()
                details.title?.let { allPossibleTitles.add(cleanMovieTitle(it).title) }
                details.alternativeTitles?.titles?.forEach { altTitle ->
                    allPossibleTitles.add(cleanMovieTitle(altTitle.title).title)
                }
                val tmdbYear = details.releaseDate?.substringBefore("-")
                return localMoviesCleaned.filter { (_, cleanedLocal) ->
                    val titleMatch = allPossibleTitles.any { it.equals(cleanedLocal.title, ignoreCase = true) }
                    val yearMatch = (tmdbYear == null || cleanedLocal.year == null || cleanedLocal.year == tmdbYear)
                    titleMatch && yearMatch
                }.map { it.first }
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Error buscando coincidencias para TMDb ID ${tmdbMovie.id}: ${e.message}")
        }
        return emptyList()
    }

    suspend fun getActorFilmography(actorId: Int, allLocalMovies: List<Movie>): ActorFilmography = coroutineScope {
        val biography = try {
            tmdbApiService.getPersonDetails(actorId, BuildConfig.TMDB_API_KEY).body()?.biography
        } catch (e: Exception) { null }

        val filmographyResponse = try {
            tmdbApiService.getPersonMovieCredits(actorId, BuildConfig.TMDB_API_KEY)
        } catch (e: Exception) { null }

        if (filmographyResponse?.isSuccessful == true) {
            val filmography = filmographyResponse.body()?.cast ?: emptyList()
            val localMoviesCleaned = allLocalMovies.map { it to cleanMovieTitle(it.name) }

            val matchJobs = filmography.map { tmdbMovie ->
                async {
                    findLocalMovieMatches(tmdbMovie, localMoviesCleaned) to tmdbMovie
                }
            }
            val results = matchJobs.awaitAll()

            val available = results.flatMap { it.first }
            val matchedTmdbMovies = results.filter { it.first.isNotEmpty() }.map { it.second }
            val unavailable = filmography.filterNot { tmdbMovie -> matchedTmdbMovies.any { it.id == tmdbMovie.id } }

            // ¡NUEVO! Filtramos por link único (streamId + extension)
            val uniqueAvailable = available.distinctBy { "${it.streamId}.${it.containerExtension}" }

            return@coroutineScope ActorFilmography(biography, uniqueAvailable, unavailable)
        }
        return@coroutineScope ActorFilmography(biography, emptyList(), emptyList())
    }

    fun getRawLiveStreams(userId: Int): Flow<List<LiveStream>> = liveStreamDao.getAllLiveStreams(userId)
    suspend fun getAllEpgMapForUser(userId: Int): Map<Int, List<EpgEvent>> = epgEventDao.getAllEventsForUser(userId).groupBy { it.channelId }
    suspend fun enrichChannelsWithEpg(channels: List<LiveStream>, epgMap: Map<Int, List<EpgEvent>>): List<LiveStream> {
        return channels.map { stream ->
            val epgEvents = epgMap[stream.streamId] ?: emptyList()
            if (epgEvents.isNotEmpty()) {
                val currentTime = System.currentTimeMillis() / 1000
                stream.currentEpgEvent = epgEvents.find { it.startTimestamp <= currentTime && it.stopTimestamp > currentTime }
                stream.nextEpgEvent = epgEvents.filter { it.startTimestamp > currentTime }.minByOrNull { it.startTimestamp }
            }
            stream
        }
    }
    fun getAllMovies(userId: Int): Flow<List<Movie>> = movieDao.getAllMovies(userId)
    fun getAllSeries(userId: Int): Flow<List<Series>> = seriesDao.getAllSeries(userId)
    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            xtreamApiService.getLiveCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) { emptyList() }
    }
    open suspend fun getMovieCategories(user: String, pass: String): List<Category> {
        return try {
            xtreamApiService.getMovieCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) { emptyList() }
    }
    suspend fun cacheLiveStreams(user: String, pass: String, userId: Int) {
        val liveCategories = getLiveCategories(user, pass)
        val allStreamsForUser = mutableListOf<LiveStream>()
        for (category in liveCategories) {
            try {
                val streams = xtreamApiService.getLiveStreams(user, pass, category.categoryId.toInt()).body()
                if (!streams.isNullOrEmpty()) {
                    streams.forEach { it.userId = userId }
                    allStreamsForUser.addAll(streams)
                }
            } catch (e: Exception) { /* Log error */ }
        }
        liveStreamDao.replaceAll(allStreamsForUser, userId)
    }
    suspend fun cacheMovies(user: String, pass: String, userId: Int) {
        val movieCategories = xtreamApiService.getMovieCategories(user, pass).body() ?: emptyList()
        val allMoviesForUser = mutableListOf<Movie>()
        for (category in movieCategories) {
            try {
                val movies = xtreamApiService.getMovies(user, pass, category.categoryId.toInt()).body()
                if (!movies.isNullOrEmpty()) {
                    movies.forEach { it.userId = userId }
                    allMoviesForUser.addAll(movies)
                }
            } catch (e: Exception) { /* Log error */ }
        }
        movieDao.replaceAll(allMoviesForUser, userId)
    }
    suspend fun cacheSeries(user: String, pass: String, userId: Int) {
        val seriesCategories = xtreamApiService.getSeriesCategories(user, pass).body() ?: emptyList()
        val allSeriesForUser = mutableListOf<Series>()
        for (category in seriesCategories) {
            try {
                val series = xtreamApiService.getSeries(user, pass, category.categoryId.toInt()).body()
                if (!series.isNullOrEmpty()) {
                    series.forEach { it.userId = userId }
                    allSeriesForUser.addAll(series)
                }
            } catch (e: Exception) { /* Log error */ }
        }
        seriesDao.replaceAll(allSeriesForUser, userId)
    }
    suspend fun cacheEpgData(user: String, pass: String, userId: Int) {
        try {
            val allStreams = liveStreamDao.getAllLiveStreams(userId).first()
            val epgIdToStreamIdsMap = allStreams
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy({ it.epgChannelId!!.lowercase().trim() }, { it.streamId })
            val response = xtreamApiService.getXmlTvEpg(user, pass)
            if (response.isSuccessful && response.body() != null) {
                val inputStream = response.body()!!.byteStream()
                val allEpgEvents = XmlTvParser.parse(inputStream, epgIdToStreamIdsMap, userId)
                epgEventDao.replaceAll(allEpgEvents, userId)
            }
        } catch (e: Exception) { /* Log error */ }
    }
}
