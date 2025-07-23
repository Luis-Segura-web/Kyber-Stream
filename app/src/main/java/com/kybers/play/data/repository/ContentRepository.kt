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

data class CleanedTitle(val title: String, val year: String?)

data class ActorFilmography(
    val biography: String?,
    val availableMovies: List<Movie>,
    val unavailableMovies: List<TMDbMovieResult>
)

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
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    suspend fun getMovieDetails(movie: Movie): MovieWithDetails {
        val cacheExpiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val cachedDetails = movieDetailsCacheDao.getByStreamId(movie.streamId)

        if (cachedDetails != null && cachedDetails.lastUpdated > cacheExpiry) {
            Log.d("ContentRepository", "Detalles para '${movie.name}' encontrados en caché y válidos.")
            return MovieWithDetails(movie, cachedDetails)
        }

        val cleaned = cleanMovieTitle(movie.name)
        var finalDetails: MovieDetailsCache?

        Log.d("ContentRepository", "Iniciando búsqueda de detalles para '${movie.name}' en TMDb.")
        val tmdbIdFromProvider = movie.tmdbId?.toIntOrNull()
        val tmdbId = if (tmdbIdFromProvider != null && tmdbIdFromProvider > 0) {
            tmdbIdFromProvider
        } else {
            findTMDbId(cleaned.title, cleaned.year)
        }

        finalDetails = if (tmdbId != null) {
            fetchFromTMDbById(movie.streamId, tmdbId)
        } else {
            Log.w("ContentRepository", "No se encontró ID en TMDb para '${movie.name}'.")
            null
        }

        val needsEnrichment = finalDetails != null && (finalDetails.plot.isNullOrBlank() || finalDetails.posterUrl.isNullOrBlank())

        if (needsEnrichment) {
            Log.d("ContentRepository", "Datos de TMDb incompletos para '${movie.name}'. Buscando en OMDb para enriquecer.")
            val tmdbMovieTitle = findTMDbTitleById(tmdbId!!) ?: cleaned.title
            val omdbBackup = fetchFromOMDbByTitle(movie.streamId, tmdbMovieTitle, finalDetails!!.releaseYear ?: cleaned.year)

            if (omdbBackup != null) {
                finalDetails = finalDetails.copy(
                    plot = finalDetails.plot.takeIf { !it.isNullOrBlank() } ?: omdbBackup.plot,
                    posterUrl = finalDetails.posterUrl.takeIf { !it.isNullOrBlank() } ?: omdbBackup.posterUrl,
                    rating = finalDetails.rating.takeIf { it != null && it > 0 } ?: omdbBackup.rating
                )
                Log.d("ContentRepository", "Datos de OMDb fusionados exitosamente para '${movie.name}'.")
            }
        } else if (finalDetails == null) {
            Log.d("ContentRepository", "TMDb falló. Iniciando búsqueda de respaldo para '${movie.name}' en OMDb.")
            finalDetails = fetchFromOMDbByTitle(movie.streamId, cleaned.title, cleaned.year)
            if (finalDetails != null) {
                Log.d("ContentRepository", "¡Éxito! Detalles encontrados en OMDb para '${movie.name}'.")
            } else {
                Log.e("ContentRepository", "Ambos servicios fallaron para '${movie.name}'.")
            }
        }

        finalDetails?.let {
            Log.d("ContentRepository", "Guardando detalles finales en caché para '${movie.name}'.")
            movieDetailsCacheDao.insertOrUpdate(it)
        }

        return MovieWithDetails(movie, finalDetails)
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

    private suspend fun findTMDbTitleById(tmdbId: Int): String? {
        try {
            val response = tmdbApiService.getMovieDetailsTMDb(movieId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) {
                return response.body()?.title
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo al obtener título de TMDb para ID $tmdbId: ${e.message}")
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

    private suspend fun fetchFromOMDbByTitle(streamId: Int, title: String, year: String?): MovieDetailsCache? {
        try {
            val response = omdbApiService.getMovieDetailsOMDb(
                apiKey = BuildConfig.OMDB_API_KEY,
                title = title,
                year = year
            )
            if (response.isSuccessful) {
                val details = response.body()
                if (details != null && details.response == "True") {
                    val castList: List<TMDbCastMember> = if (details.actors.isNullOrBlank() || details.actors == "N/A") {
                        emptyList()
                    } else {
                        details.actors.split(", ").map { TMDbCastMember(name = it, character = "", profilePath = null, id = 0) }
                    }
                    val castAdapter = moshi.adapter<List<TMDbCastMember>>(
                        Types.newParameterizedType(List::class.java, TMDbCastMember::class.java)
                    )
                    return MovieDetailsCache(
                        streamId = streamId,
                        tmdbId = null,
                        plot = if (details.plot == "N/A") null else details.plot,
                        posterUrl = if (details.poster == "N/A") null else details.poster,
                        backdropUrl = null,
                        releaseYear = if (details.year == "N/A") null else details.year,
                        rating = details.imdbRating?.toDoubleOrNull(),
                        castJson = castAdapter.toJson(castList),
                        recommendationsJson = "[]",
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo al obtener detalles de OMDb para '$title': ${e.message}")
        }
        return null
    }

    suspend fun findMoviesByTMDbResults(tmdbMovies: List<TMDbMovieResult>, allLocalMovies: List<Movie>): List<Movie> = coroutineScope {
        val localMoviesCleaned = allLocalMovies.map { it to cleanMovieTitle(it.name) }
        val matchJobs = tmdbMovies.map { tmdbMovie ->
            async {
                findLocalMovieMatchesByName(tmdbMovie, localMoviesCleaned)
            }
        }
        matchJobs.awaitAll().flatten().distinctBy { "${it.streamId}.${it.containerExtension}" }
    }

    private suspend fun findLocalMovieMatchesByName(tmdbMovie: TMDbMovieResult, localMoviesCleaned: List<Pair<Movie, CleanedTitle>>): List<Movie> {
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
            Log.e("ContentRepository", "Error buscando coincidencias por nombre para TMDb ID ${tmdbMovie.id}: ${e.message}")
        }
        return emptyList()
    }

    // --- ¡FUNCIÓN CLAVE MODIFICADA! ---
    suspend fun getActorFilmography(actorId: Int, allLocalMovies: List<Movie>): ActorFilmography = coroutineScope {
        val biography = try {
            tmdbApiService.getPersonDetails(actorId, BuildConfig.TMDB_API_KEY).body()?.biography
        } catch (e: Exception) { null }

        val filmographyResponse = try {
            tmdbApiService.getPersonMovieCredits(actorId, BuildConfig.TMDB_API_KEY)
        } catch (e: Exception) { null }

        if (filmographyResponse?.isSuccessful == true) {
            val actorFilmography = filmographyResponse.body()?.cast ?: emptyList()

            // 1. Separamos nuestras películas locales en dos grupos para mayor eficiencia.
            val localMoviesWithId = allLocalMovies.filter { it.tmdbId?.toIntOrNull() != null }
            val localMoviesWithoutId = allLocalMovies.filter { it.tmdbId?.toIntOrNull() == null }

            val availableMovies = mutableListOf<Movie>()
            val matchedTmdbIds = mutableSetOf<Int>()

            // 2. PRIMERA PASADA: Búsqueda por ID (la más rápida y precisa).
            Log.d("Filmography", "Iniciando búsqueda por ID. Filmografía del actor: ${actorFilmography.size}, Locales con ID: ${localMoviesWithId.size}")
            actorFilmography.forEach { tmdbMovie ->
                val match = localMoviesWithId.find { it.tmdbId?.toIntOrNull() == tmdbMovie.id }
                if (match != null) {
                    availableMovies.add(match)
                    matchedTmdbIds.add(tmdbMovie.id)
                }
            }
            Log.d("Filmography", "Fin de búsqueda por ID. Coincidencias encontradas: ${availableMovies.size}")

            // 3. SEGUNDA PASADA: Búsqueda por nombre para las películas restantes.
            val remainingTMDbFilmography = actorFilmography.filterNot { it.id in matchedTmdbIds }
            if (remainingTMDbFilmography.isNotEmpty() && localMoviesWithoutId.isNotEmpty()) {
                Log.d("Filmography", "Iniciando búsqueda por nombre para ${remainingTMDbFilmography.size} películas restantes.")
                val localMoviesCleaned = localMoviesWithoutId.map { it to cleanMovieTitle(it.name) }
                val nameMatchJobs = remainingTMDbFilmography.map { tmdbMovie ->
                    async {
                        findLocalMovieMatchesByName(tmdbMovie, localMoviesCleaned)
                    }
                }
                val nameMatches = nameMatchJobs.awaitAll().flatten()
                if (nameMatches.isNotEmpty()) {
                    availableMovies.addAll(nameMatches)
                    nameMatches.forEach { movie ->
                        // Aunque la encontramos por nombre, si la película de TMDb tenía un ID, lo marcamos como encontrado.
                        val tmdbIdMatch = remainingTMDbFilmography.find { tmdbMovie ->
                            cleanMovieTitle(tmdbMovie.title ?: "").title.equals(cleanMovieTitle(movie.name).title, ignoreCase = true)
                        }
                        tmdbIdMatch?.let { matchedTmdbIds.add(it.id) }
                    }
                }
                Log.d("Filmography", "Fin de búsqueda por nombre. Nuevas coincidencias: ${nameMatches.size}")
            }

            // 4. Determinamos las películas no disponibles y devolvemos el resultado.
            val unavailable = actorFilmography.filterNot { it.id in matchedTmdbIds }
            val uniqueAvailable = availableMovies.distinctBy { "${it.streamId}.${it.containerExtension}" }

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