package com.kybers.play.data.repository

import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.EpgEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull // <--- ¡IMPORTACIÓN CRÍTICA!
import kotlinx.coroutines.flow.first // <--- ¡IMPORTACIÓN CRÍTICA!
import java.io.IOException
import android.util.Log

open class ContentRepository(
    private val apiService: XtreamApiService,
    private val liveStreamDao: LiveStreamDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val epgEventDao: EpgEventDao
) {

    // --- Data Reading Functions (from cache, now filtered by userId and enriched with EPG) ---

    // Obtiene LiveStreams por categoría para un usuario específico, enriquecidos con EPG
    fun getLiveStreamsByCategory(categoryId: String, userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getLiveStreamsByCategory(categoryId, userId)
            .map { liveStreams ->
                // Enriquecer cada LiveStream con información EPG
                liveStreams.map { stream ->
                    enrichLiveStreamWithEpg(stream, userId)
                }
            }
    }

    // Obtiene todos los LiveStreams para un usuario específico, enriquecidos con EPG
    fun getAllLiveStreams(userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getAllLiveStreams(userId)
            .map { liveStreams ->
                // Enriquecer cada LiveStream con información EPG
                liveStreams.map { stream ->
                    enrichLiveStreamWithEpg(stream, userId)
                }
            }
    }

    // Función auxiliar para enriquecer un LiveStream con eventos EPG
    private suspend fun enrichLiveStreamWithEpg(stream: LiveStream, userId: Int): LiveStream {
        // Solo intentamos obtener EPG si el canal tiene un epgChannelId válido
        if (stream.epgChannelId.isNullOrEmpty()) {
            return stream // Si no hay epgChannelId, no hay EPG para este canal
        }

        // ¡CORRECCIÓN! Usar firstOrNull() en el Flow para obtener la lista de eventos EPG
        val epgEvents = epgEventDao.getEpgEventsForChannel(stream.streamId, userId).firstOrNull() ?: emptyList()
        val currentTime = System.currentTimeMillis() / 1000 // Convertir a segundos para comparar con timestamps de EPG

        val currentEvent = epgEvents.find { it.startTimestamp <= currentTime && it.endTimestamp > currentTime }
        val nextEvent = epgEvents.filter { it.startTimestamp > currentTime }
            .minByOrNull { it.startTimestamp } // Encuentra el evento más cercano en el futuro

        // ¡CORRECCIÓN! Asignar directamente a las propiedades 'var' de LiveStream
        stream.currentEpgEvent = currentEvent
        stream.nextEpgEvent = nextEvent

        return stream
    }


    fun getAllMovies(userId: Int): Flow<List<Movie>> {
        return movieDao.getAllMovies(userId)
    }

    fun getAllSeries(userId: Int): Flow<List<Series>> {
        return seriesDao.getAllSeries(userId)
    }

    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            apiService.getLiveCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) {
            Log.e("ContentRepository", "Error al obtener categorías en vivo: ${e.message}")
            emptyList()
        }
    }

    // --- Granular Caching Functions (now user-specific) ---

    /**
     * Caches only the live stream channels for a specific user.
     * Fetches from the API, adds the userId, and then replaces existing data in the local DB.
     */
    suspend fun cacheLiveStreams(user: String, pass: String, userId: Int) {
        val liveCategories = apiService.getLiveCategories(user, pass).body() ?: emptyList()
        val allStreamsForUser = mutableListOf<LiveStream>()

        for (category in liveCategories) {
            try {
                val streams = apiService.getLiveStreams(user, pass, category.categoryId.toInt()).body()
                if (!streams.isNullOrEmpty()) {
                    streams.forEach { it.userId = userId }
                    allStreamsForUser.addAll(streams)
                }
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al obtener streams de categoría ${category.categoryId}: ${e.message}")
                e.printStackTrace()
            }
        }
        liveStreamDao.replaceAll(allStreamsForUser, userId)
        Log.d("ContentRepository", "cacheLiveStreams: ${allStreamsForUser.size} canales guardados para userId: $userId")
    }

    /**
     * Caches only the movies for a specific user.
     * Fetches from the API, adds the userId, and then replaces existing data in the local DB.
     */
    suspend fun cacheMovies(user: String, pass: String, userId: Int) {
        val movieCategories = apiService.getMovieCategories(user, pass).body() ?: emptyList()
        val allMoviesForUser = mutableListOf<Movie>()

        for (category in movieCategories) {
            try {
                val movies = apiService.getMovies(user, pass, category.categoryId.toInt()).body()
                if (!movies.isNullOrEmpty()) {
                    movies.forEach { it.userId = userId }
                    allMoviesForUser.addAll(movies)
                }
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al obtener películas de categoría ${category.categoryId}: ${e.message}")
                e.printStackTrace()
            }
        }
        movieDao.replaceAll(allMoviesForUser, userId)
        Log.d("ContentRepository", "cacheMovies: ${allMoviesForUser.size} películas guardadas para userId: $userId")
    }

    /**
     * Caches only the series for a specific user.
     * Fetches from the API, adds the userId, and then replaces existing data in the local DB.
     */
    suspend fun cacheSeries(user: String, pass: String, userId: Int) {
        val seriesCategories = apiService.getSeriesCategories(user, pass).body() ?: emptyList()
        val allSeriesForUser = mutableListOf<Series>()

        for (category in seriesCategories) {
            try {
                val series = apiService.getSeries(user, pass, category.categoryId.toInt()).body()
                if (!series.isNullOrEmpty()) {
                    series.forEach { it.userId = userId }
                    allSeriesForUser.addAll(series)
                }
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al obtener series de categoría ${category.categoryId}: ${e.message}")
                e.printStackTrace()
            }
        }
        seriesDao.replaceAll(allSeriesForUser, userId)
        Log.d("ContentRepository", "cacheSeries: ${allSeriesForUser.size} series guardadas para userId: $userId")
    }

    /**
     * Sincroniza los eventos EPG para todos los canales de un usuario.
     * Obtiene todos los LiveStreams del usuario, y para cada uno con epgChannelId,
     * obtiene su EPG de la API y la guarda.
     */
    suspend fun cacheEpgEvents(user: String, pass: String, userId: Int) {
        Log.d("ContentRepository", "Iniciando cacheEpgEvents para userId: $userId")
        // Obtener todos los LiveStreams del usuario desde la base de datos local
        val allLiveStreams = liveStreamDao.getAllLiveStreams(userId).first() // .first() para obtener el valor actual del Flow

        if (allLiveStreams.isEmpty()) {
            Log.d("ContentRepository", "No hay LiveStreams para userId $userId. Saltando caché de EPG.")
            return
        }

        val epgEventsToCache = mutableListOf<EpgEvent>()
        for (stream in allLiveStreams) {
            // Solo intentar obtener EPG si el canal tiene un epgChannelId
            if (!stream.epgChannelId.isNullOrEmpty()) {
                try {
                    val epgResponse = apiService.getEpgForChannel(user, pass, stream.epgChannelId!!)
                    val events = epgResponse.body()
                    if (!events.isNullOrEmpty()) {
                        events.forEach { event ->
                            event.userId = userId // Asignar userId al evento EPG
                            event.channelId = stream.streamId // Asignar el streamId al evento EPG
                        }
                        epgEventsToCache.addAll(events)
                        Log.d("ContentRepository", "EPG obtenida para canal ${stream.name} (${stream.epgChannelId}): ${events.size} eventos.")
                    } else {
                        Log.d("ContentRepository", "No se encontraron eventos EPG para canal ${stream.name} (${stream.epgChannelId}).")
                    }
                } catch (e: Exception) {
                    Log.e("ContentRepository", "Error al obtener EPG para canal ${stream.name} (${stream.epgChannelId}): ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        epgEventDao.replaceAll(epgEventsToCache, userId)
        Log.d("ContentRepository", "cacheEpgEvents: ${epgEventsToCache.size} eventos EPG guardados para userId: $userId")
    }
}
