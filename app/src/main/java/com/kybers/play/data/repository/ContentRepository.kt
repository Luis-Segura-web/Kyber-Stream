package com.kybers.play.data.repository

import android.util.Log
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
import com.kybers.play.data.remote.model.*
import com.kybers.play.util.XmlTvParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ActorFilmography(
    val biography: String?,
    val availableItems: List<FilmographyItem>,
    val unavailableItems: List<FilmographyItem>
)

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
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    suspend fun getMovieDetails(movie: Movie): MovieWithDetails {
        val cacheExpiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val cachedDetails = movieDetailsCacheDao.getByStreamId(movie.streamId)

        if (cachedDetails != null && cachedDetails.lastUpdated > cacheExpiry) {
            Log.d("ContentRepository", "Detalles para '${movie.name}' (ID: ${movie.streamId}) encontrados en caché y válidos.")
            return MovieWithDetails(movie, cachedDetails)
        }

        val tmdbId = movie.tmdbId?.toIntOrNull()

        if (tmdbId == null || tmdbId <= 0) {
            Log.w("ContentRepository", "La película '${movie.name}' (ID: ${movie.streamId}) no tiene un TMDB ID válido. No se buscarán detalles.")
            return MovieWithDetails(movie, null)
        }

        Log.d("ContentRepository", "Buscando detalles en TMDb para '${movie.name}' con TMDB ID: $tmdbId")
        val finalDetails = fetchFromTMDbById(movie.streamId, tmdbId)

        finalDetails?.let {
            Log.d("ContentRepository", "Guardando nuevos detalles de TMDb en caché para '${movie.name}'.")
            movieDetailsCacheDao.insertOrUpdate(it)
        } ?: Log.e("ContentRepository", "Fallo al obtener detalles de TMDb para '${movie.name}' (TMDB ID: $tmdbId).")

        return MovieWithDetails(movie, finalDetails)
    }

    suspend fun getTMDbDetails(tmdbId: Int): TMDbMovieDetails? {
        return try {
            val response = tmdbApiService.getMovieDetailsTMDb(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e("ContentRepository", "Error en la respuesta de TMDb para ID $tmdbId: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo en la llamada a la API de TMDb para ID $tmdbId: ${e.message}")
            null
        }
    }

    suspend fun getTMDbTvDetails(tvId: Int): TMDbTvDetails? {
        return try {
            val response = tmdbApiService.getTvDetailsTMDb(
                tvId = tvId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo en la llamada a la API de TMDb para TV ID $tvId: ${e.message}")
            null
        }
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
                    val certification = findMovieCertification(details)
                    val castList: List<TMDbCastMember> = details.credits?.cast ?: emptyList()
                    val recommendations: List<TMDbMovieResult> = details.recommendations?.results?.take(10) ?: emptyList()
                    val castAdapter = moshi.adapter<List<TMDbCastMember>>(
                        Types.newParameterizedType(List::class.java, TMDbCastMember::class.java)
                    )
                    val recommendationsAdapter = moshi.adapter<List<TMDbMovieResult>>(
                        Types.newParameterizedType(List::class.java, TMDbMovieResult::class.java)
                    )

                    return MovieDetailsCache(
                        streamId = streamId, tmdbId = tmdbId, plot = details.overview,
                        posterUrl = details.getFullPosterUrl(), backdropUrl = details.getFullBackdropUrl(),
                        releaseYear = details.releaseDate?.substringBefore("-"), rating = details.voteAverage,
                        certification = certification,
                        castJson = castAdapter.toJson(castList),
                        recommendationsJson = recommendationsAdapter.toJson(recommendations),
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo al obtener detalles de TMDb para ID $tmdbId: ${e.message}")
        }
        return null
    }

    fun findMovieCertification(details: TMDbMovieDetails?): String? {
        val usRelease = details?.releaseDates?.results?.find { it.countryCode == "US" }
        return usRelease?.releaseDates?.find { it.type == 3 }?.certification?.takeIf { it.isNotBlank() }
            ?: usRelease?.releaseDates?.firstOrNull()?.certification?.takeIf { it.isNotBlank() }
    }

    fun findTvCertification(details: TMDbTvDetails?): String? {
        return details?.contentRatings?.results?.find { it.countryCode == "US" }?.rating?.takeIf { it.isNotBlank() }
    }


    suspend fun findMoviesByTMDbResults(tmdbMovies: List<TMDbMovieResult>, allLocalMovies: List<Movie>): List<Movie> {
        if (tmdbMovies.isEmpty() || allLocalMovies.isEmpty()) return emptyList()

        val localMoviesWithId = allLocalMovies.filter { it.tmdbId?.toIntOrNull() != null }
        if (localMoviesWithId.isEmpty()) return emptyList()

        val tmdbMovieIds = tmdbMovies.map { it.id }.toSet()

        return localMoviesWithId.filter { localMovie ->
            tmdbMovieIds.contains(localMovie.tmdbId?.toIntOrNull())
        }
    }

    suspend fun getActorFilmography(actorId: Int, allLocalMovies: List<Movie>, allLocalSeries: List<Series>): ActorFilmography = coroutineScope {
        val biographyJob = async {
            try {
                tmdbApiService.getPersonDetails(actorId, BuildConfig.TMDB_API_KEY).body()?.biography
            } catch (e: Exception) { null }
        }
        val movieCreditsJob = async {
            try {
                tmdbApiService.getPersonMovieCredits(actorId, BuildConfig.TMDB_API_KEY).body()?.cast ?: emptyList()
            } catch (e: Exception) { emptyList<TMDbMovieResult>() }
        }
        val tvCreditsJob = async {
            try {
                tmdbApiService.getPersonTvCredits(actorId, BuildConfig.TMDB_API_KEY).body()?.cast ?: emptyList()
            } catch (e: Exception) { emptyList<TMDbTvResult>() }
        }

        val biography = biographyJob.await()
        val movieCredits = movieCreditsJob.await()
        val tvCredits = tvCreditsJob.await()

        val fullFilmography: List<FilmographyItem> = (movieCredits + tvCredits).distinctBy { it.id }

        val localMovieTmdbIds = allLocalMovies.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()

        val (available, unavailable) = fullFilmography.partition { filmographyItem ->
            when (filmographyItem.mediaType) {
                "movie" -> localMovieTmdbIds.contains(filmographyItem.id)
                "tv" -> false
                else -> false
            }
        }

        return@coroutineScope ActorFilmography(biography, available, unavailable)
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
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al descargar streams de categoría ${category.categoryId}", e)
            }
        }
        liveStreamDao.replaceAll(allStreamsForUser, userId)
    }

    suspend fun cacheMovies(user: String, pass: String, userId: Int): Int {
        val movieCategories = xtreamApiService.getMovieCategories(user, pass).body() ?: emptyList()
        var downloadedMoviesCount = 0
        Log.d("ContentRepository", "Iniciando descarga masiva de películas para ${movieCategories.size} categorías.")
        for (category in movieCategories) {
            try {
                val movies = xtreamApiService.getMovies(user, pass, category.categoryId.toInt()).body()
                if (!movies.isNullOrEmpty()) {
                    movies.forEach { it.userId = userId }
                    downloadedMoviesCount += movies.size
                    movieDao.cacheMovies(movies, userId)
                    Log.d("ContentRepository", "Categoría '${category.categoryName}': ${movies.size} películas descargadas y cacheadas.")
                }
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al descargar películas de categoría ${category.categoryId}", e)
            }
        }
        Log.d("ContentRepository", "Descarga masiva completada. Total de películas descargadas en esta sesión: $downloadedMoviesCount")
        return downloadedMoviesCount
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
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al descargar series de categoría ${category.categoryId}", e)
            }
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
        } catch (e: Exception) {
            Log.e("ContentRepository", "Error al cachear datos de EPG", e)
        }
    }

    suspend fun getAllCachedMovieDetailsMap(): Map<Int, MovieDetailsCache> {
        return movieDetailsCacheDao.getAllDetails().associateBy { it.streamId }
    }

}
