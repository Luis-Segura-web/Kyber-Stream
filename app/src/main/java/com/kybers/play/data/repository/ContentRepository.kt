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
import java.io.IOException

open class ContentRepository(
    private val apiService: XtreamApiService,
    private val liveStreamDao: LiveStreamDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val epgEventDao: EpgEventDao,
    private val baseUrl: String
) {

    fun getRawLiveStreams(userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getAllLiveStreams(userId)
    }

    /**
     * ¡NUEVA FUNCIÓN DE ALTO RENDIMIENTO!
     * Obtiene TODOS los eventos EPG para un usuario de la base de datos de una sola vez
     * y los devuelve agrupados en un mapa por channelId. Esto es infinitamente más rápido
     * que hacer una consulta por cada canal.
     */
    suspend fun getAllEpgMapForUser(userId: Int): Map<Int, List<EpgEvent>> {
        return epgEventDao.getAllEventsForUser(userId).groupBy { it.channelId }
    }

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
            apiService.getLiveCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) {
            Log.e("ContentRepository", "Error al obtener categorías en vivo: ${e.message}")
            emptyList()
        }
    }

    /**
     * ¡NUEVA FUNCIÓN!
     * Obtiene las categorías de películas desde la API.
     * La necesitamos para la nueva pantalla de películas.
     */
    open suspend fun getMovieCategories(user: String, pass: String): List<Category> {
        return try {
            apiService.getMovieCategories(user, pass).body() ?: emptyList()
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
                val streams = apiService.getLiveStreams(user, pass, category.categoryId.toInt()).body()
                if (!streams.isNullOrEmpty()) {
                    streams.forEach { it.userId = userId }
                    allStreamsForUser.addAll(streams)
                }
            } catch (e: Exception) {
                Log.e("ContentRepository", "Error al obtener streams de categoría ${category.categoryId}: ${e.message}")
            }
        }

        val channelsWithEpgId = allStreamsForUser.count { !it.epgChannelId.isNullOrBlank() }
        Log.d("EPG_DIAGNOSTIC", "Total de canales a guardar: ${allStreamsForUser.size}. Canales CON EPG ID: $channelsWithEpgId. Canales SIN EPG ID: ${allStreamsForUser.size - channelsWithEpgId}")

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
        Log.d("EPG_DEBUG", "Iniciando descarga de EPG (XMLTV) para userId: $userId")
        try {
            val allStreams = liveStreamDao.getAllLiveStreams(userId).first()
            val epgIdToStreamIdsMap = allStreams
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy(
                    { it.epgChannelId!!.lowercase().trim() },
                    { it.streamId }
                )

            Log.d("EPG_DEBUG", "Mapa de EPG IDs a Stream IDs creado. Total de EPG IDs únicos en nuestros canales: ${epgIdToStreamIdsMap.size}")
            if (epgIdToStreamIdsMap.isNotEmpty()) {
                Log.d("EPG_DEBUG", "Ejemplos de EPG IDs de nuestros canales: ${epgIdToStreamIdsMap.keys.take(5)}")
            }

            val response = apiService.getXmlTvEpg(user, pass)

            if (response.isSuccessful && response.body() != null) {
                val inputStream = response.body()!!.byteStream()
                val allEpgEvents = XmlTvParser.parse(inputStream, epgIdToStreamIdsMap, userId)
                Log.d("EPG_DEBUG", "Análisis de XMLTV completado. ${allEpgEvents.size} eventos de EPG generados.")

                epgEventDao.replaceAll(allEpgEvents, userId)
                Log.d("EPG_DEBUG", "Se han guardado ${allEpgEvents.size} eventos de EPG en la base de datos para userId: $userId.")
            } else {
                Log.e("EPG_DEBUG", "Falló la descarga de XMLTV. Código: ${response.code()}. Mensaje: ${response.message()}")
            }

        } catch (e: Exception) {
            Log.e("EPG_DEBUG", "Excepción mayor durante el proceso de caché de EPG: ${e.message}")
            e.printStackTrace()
        }
    }
}