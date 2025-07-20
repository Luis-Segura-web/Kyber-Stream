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
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.util.XmlTvParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    // --- LÓGICA PARA DETALLES DE PELÍCULAS ---

    suspend fun getMovieDetails(movie: Movie): MovieWithDetails {
        val cacheExpiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val cachedDetails = movieDetailsCacheDao.getByStreamId(movie.streamId)

        if (cachedDetails != null && cachedDetails.lastUpdated > cacheExpiry) {
            Log.d("ContentRepository", "Detalles de Película para '${movie.name}': Encontrado caché válido.")
            return MovieWithDetails(movie, cachedDetails)
        }

        Log.d("ContentRepository", "Detalles de Película para '${movie.name}': Sin caché válido. Buscando en la red...")
        // ¡CAMBIO CLAVE! Limpiamos el título antes de buscar.
        val cleanedTitle = cleanMovieTitle(movie.name)
        val newDetails = fetchFromTMDb(movie.streamId, cleanedTitle) ?: fetchFromOMDb(movie.streamId, cleanedTitle)

        newDetails?.let {
            movieDetailsCacheDao.insertOrUpdate(it)
            Log.d("ContentRepository", "Detalles de Película para '${movie.name}': Nuevos detalles guardados en caché.")
        }

        return MovieWithDetails(movie, newDetails)
    }

    /**
     * ¡NUEVA FUNCIÓN! Limpia el título de una película para mejorar las coincidencias en las APIs.
     * Elimina etiquetas de calidad, información entre paréntesis y caracteres especiales.
     */
    private fun cleanMovieTitle(title: String): String {
        // Regex para eliminar etiquetas comunes como (Dual), HD, 4K, etc. y espacios extra.
        return title
            .replace(Regex("""\s*\(.*?\)"""), "") // Elimina todo dentro de paréntesis
            .replace(Regex("""\s*\[.*?]"""), "") // Elimina todo dentro de corchetes
            .replace(Regex("""\b(HD|4K|FHD|1080p|720p|DUAL|LATINO|SUB|VOS|ES|ENG)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private suspend fun fetchFromTMDb(streamId: Int, cleanedTitle: String): MovieDetailsCache? {
        try {
            val response = tmdbApiService.searchMovieTMDb(
                apiKey = BuildConfig.TMDB_API_KEY,
                movieTitle = cleanedTitle
            )
            if (response.isSuccessful) {
                val tmdbMovie = response.body()?.results?.firstOrNull()
                if (tmdbMovie != null) {
                    Log.d("ContentRepository", "TMDb encontró: '${tmdbMovie.title}' para '$cleanedTitle'")
                    return MovieDetailsCache(
                        streamId = streamId,
                        plot = tmdbMovie.overview,
                        backdropUrl = tmdbMovie.getFullBackdropUrl(),
                        rating = tmdbMovie.voteAverage,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            } else {
                Log.e("ContentRepository", "Error de TMDb: ${response.code()} - ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo en la búsqueda de TMDb para '$cleanedTitle': ${e.message}")
        }
        Log.w("ContentRepository", "TMDb no encontró resultados para '$cleanedTitle'.")
        return null
    }

    private suspend fun fetchFromOMDb(streamId: Int, cleanedTitle: String): MovieDetailsCache? {
        try {
            val response = omdbApiService.getMovieOMDb(
                apiKey = BuildConfig.OMDB_API_KEY,
                movieTitle = cleanedTitle
            )
            if (response.isSuccessful) {
                val omdbMovie = response.body()
                if (omdbMovie != null && omdbMovie.hasSucceeded()) {
                    Log.d("ContentRepository", "OMDb (respaldo) encontró: '${omdbMovie.title}' para '$cleanedTitle'")
                    return MovieDetailsCache(
                        streamId = streamId,
                        plot = omdbMovie.plot,
                        backdropUrl = null,
                        rating = omdbMovie.imdbRating?.toDoubleOrNull(),
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "Fallo en la búsqueda de OMDb para '$cleanedTitle': ${e.message}")
        }
        Log.e("ContentRepository", "Ambos TMDb y OMDb fallaron para encontrar '$cleanedTitle'.")
        return null
    }

    // --- El resto de las funciones del repositorio permanecen igual ---
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
        } catch (e: IOException) {
            Log.e("ContentRepository", "Error al obtener categorías en vivo: ${e.message}")
            emptyList()
        }
    }
    open suspend fun getMovieCategories(user: String, pass: String): List<Category> {
        return try {
            xtreamApiService.getMovieCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) {
            Log.e("ContentRepository", "Error al obtener categorías de películas: ${e.message}")
            emptyList()
        }
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
                Log.e("ContentRepository", "Error al obtener streams de categoría ${category.categoryId}: ${e.message}")
            }
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
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al obtener películas de categoría ${category.categoryId}: ${e.message}")
            }
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
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al obtener series de categoría ${category.categoryId}: ${e.message}")
            }
        }
        seriesDao.replaceAll(allSeriesForUser, userId)
    }
    suspend fun cacheEpgData(user: String, pass: String, userId: Int) {
        Log.d("EPG_DEBUG", "Iniciando descarga de EPG (XMLTV) para userId: $userId")
        try {
            val allStreams = liveStreamDao.getAllLiveStreams(userId).first()
            val epgIdToStreamIdsMap = allStreams
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy(
                    { it.epgChannelId!!.lowercase().trim() },
                    { it.streamId }
                )
            val response = xtreamApiService.getXmlTvEpg(user, pass)
            if (response.isSuccessful && response.body() != null) {
                val inputStream = response.body()!!.byteStream()
                val allEpgEvents = XmlTvParser.parse(inputStream, epgIdToStreamIdsMap, userId)
                epgEventDao.replaceAll(allEpgEvents, userId)
            } else {
                Log.e("EPG_DEBUG", "Falló la descarga de XMLTV. Código: ${response.code()}. Mensaje: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("EPG_DEBUG", "Excepción mayor durante el proceso de caché de EPG: ${e.message}")
            e.printStackTrace()
        }
    }
}
