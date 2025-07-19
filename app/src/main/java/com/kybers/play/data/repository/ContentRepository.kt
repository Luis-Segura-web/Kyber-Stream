package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.util.XmlTvParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException

open class ContentRepository(
    private val apiService: XtreamApiService,
    private val liveStreamDao: LiveStreamDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val epgEventDao: EpgEventDao,
    private val baseUrl: String
) {

    // --- Data Reading Functions ---

    /**
     * ¡NUEVO! Función de "vía rápida" que obtiene los canales sin procesar la EPG.
     * Esto es para mostrar la lista de canales de forma instantánea.
     */
    fun getRawLiveStreams(userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getAllLiveStreams(userId)
    }

    /**
     * Obtiene todos los canales y les añade la información de la EPG.
     * Esta es la función "lenta" que ahora se llamará en segundo plano.
     */
    fun getAllLiveStreamsWithEpg(userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getAllLiveStreams(userId)
            .map { liveStreams ->
                liveStreams.map { stream ->
                    enrichLiveStreamWithEpg(stream)
                }
            }
    }

    private suspend fun enrichLiveStreamWithEpg(stream: LiveStream): LiveStream {
        val epgEvents = epgEventDao.getEpgEventsForChannel(stream.streamId, stream.userId).firstOrNull() ?: emptyList()

        if (epgEvents.isEmpty()) {
            return stream
        }

        val currentTime = System.currentTimeMillis() / 1000
        var currentEvent: EpgEvent? = epgEvents.find { it.startTimestamp <= currentTime && it.stopTimestamp > currentTime }
        var nextEvent: EpgEvent?

        if (currentEvent == null) {
            val futureEvents = epgEvents.filter { it.startTimestamp > currentTime }.sortedBy { it.startTimestamp }
            currentEvent = futureEvents.getOrNull(0)
            nextEvent = futureEvents.getOrNull(1)
        } else {
            nextEvent = epgEvents.filter { it.startTimestamp > currentTime }.minByOrNull { it.startTimestamp }
        }

        stream.currentEpgEvent = currentEvent
        stream.nextEpgEvent = nextEvent
        return stream
    }

    fun getAllMovies(userId: Int): Flow<List<Movie>> = movieDao.getAllMovies(userId)
    fun getAllSeries(userId: Int): Flow<List<Series>> = seriesDao.getAllSeries(userId)

    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            apiService.getLiveCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) {
            Log.e("ContentRepository", "Error al obtener categorías en vivo: ${e.message}")
            emptyList()
        }
    }

    // --- Caching Functions ---

    suspend fun cacheLiveStreams(user: String, pass: String, userId: Int) {
        val liveCategories = getLiveCategories(user, pass)
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
            }
        }
        liveStreamDao.replaceAll(allStreamsForUser, userId)
    }

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
            }
        }
        movieDao.replaceAll(allMoviesForUser, userId)
    }

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
            }
        }
        seriesDao.replaceAll(allSeriesForUser, userId)
    }

    suspend fun cacheEpgData(user: String, pass: String, userId: Int) {
        Log.d("ContentRepository", "Iniciando descarga de EPG (Solo XMLTV) para userId: $userId")
        var allEpgEvents = mutableListOf<EpgEvent>()

        try {
            val allStreams = liveStreamDao.getAllLiveStreams(userId).first()

            val epgIdToStreamIdsMap = allStreams
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy(
                    { it.epgChannelId!!.lowercase().trim() },
                    { it.streamId }
                )

            val response = apiService.getXmlTvEpg(user, pass)

            if (response.isSuccessful && response.body() != null) {
                val inputStream = response.body()!!.byteStream()
                allEpgEvents = XmlTvParser.parse(inputStream, epgIdToStreamIdsMap, userId).toMutableList()
                Log.d("ContentRepository", "XMLTV parseado. ${allEpgEvents.size} eventos encontrados.")
            } else {
                Log.e("ContentRepository", "Falló la descarga de XMLTV: ${response.code()}.")
            }

            Log.d("ContentRepository", "Guardando ${allEpgEvents.size} eventos de EPG.")
            epgEventDao.replaceAll(allEpgEvents, userId)

        } catch (e: Exception) {
            Log.e("ContentRepository", "Excepción mayor durante el proceso de EPG: ${e.message}")
            e.printStackTrace()
        }
    }
}
