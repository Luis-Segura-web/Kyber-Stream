package com.kybers.play.data.repository

import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import kotlinx.coroutines.flow.Flow
import java.io.IOException

open class ContentRepository(
    private val apiService: XtreamApiService,
    private val liveStreamDao: LiveStreamDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao
) {

    // --- Data Reading Functions (from cache, now filtered by userId) ---

    // Obtiene LiveStreams por categoría para un usuario específico
    fun getLiveStreamsByCategory(categoryId: String, userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getLiveStreamsByCategory(categoryId, userId)
    }

    // Obtiene todos los LiveStreams para un usuario específico
    fun getAllLiveStreams(userId: Int): Flow<List<LiveStream>> {
        return liveStreamDao.getAllLiveStreams(userId)
    }

    // Obtiene todas las películas para un usuario específico
    fun getAllMovies(userId: Int): Flow<List<Movie>> {
        return movieDao.getAllMovies(userId)
    }

    // Obtiene todas las series para un usuario específico
    fun getAllSeries(userId: Int): Flow<List<Series>> {
        return seriesDao.getAllSeries(userId)
    }

    // Obtiene las categorías de Live, directamente de la API (no se cachean por usuario)
    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            apiService.getLiveCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    // --- Granular Caching Functions (now user-specific and without progress callback) ---

    /**
     * Caches only the live stream channels for a specific user.
     * Fetches from the API, adds the userId, and then replaces existing data in the local DB.
     * ¡MODIFICADO! Se eliminó el callback onProgress.
     */
    suspend fun cacheLiveStreams(user: String, pass: String, userId: Int) {
        val liveCategories = apiService.getLiveCategories(user, pass).body() ?: emptyList()
        val allStreamsForUser = mutableListOf<LiveStream>()

        // En una sincronización real, aquí podrías obtener el total de canales
        // antes de empezar a iterar por categorías para un progreso más preciso.
        // Por ahora, simplemente iteramos y asignamos el userId.
        for (category in liveCategories) {
            try {
                val streams = apiService.getLiveStreams(user, pass, category.categoryId.toInt()).body()
                if (!streams.isNullOrEmpty()) {
                    streams.forEach { it.userId = userId }
                    allStreamsForUser.addAll(streams)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        liveStreamDao.replaceAll(allStreamsForUser, userId)
    }

    /**
     * Caches only the movies for a specific user.
     * Fetches from the API, adds the userId, and then replaces existing data in the local DB.
     * ¡MODIFICADO! Se eliminó el callback onProgress.
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
                e.printStackTrace()
            }
        }
        movieDao.replaceAll(allMoviesForUser, userId)
    }

    /**
     * Caches only the series for a specific user.
     * Fetches from the API, adds the userId, and then replaces existing data in the local DB.
     * ¡MODIFICADO! Se eliminó el callback onProgress.
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
                e.printStackTrace()
            }
        }
        seriesDao.replaceAll(allSeriesForUser, userId)
    }
}
