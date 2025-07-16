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

    // --- FUNCIONES DE LECTURA (Leen desde el caché) ---

    fun getLiveStreamsByCategory(categoryId: String): Flow<List<LiveStream>> {
        return liveStreamDao.getLiveStreamsByCategory(categoryId)
    }

    // ¡NUEVO! Para la pantalla de Inicio
    fun getAllMovies(): Flow<List<Movie>> {
        return movieDao.getAllMovies()
    }

    // ¡NUEVO! Para la pantalla de Inicio
    fun getAllSeries(): Flow<List<Series>> {
        return seriesDao.getAllSeries()
    }

    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            apiService.getLiveCategories(user, pass).body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    open suspend fun cacheAllData(user: String, pass: String) {
        val liveCategories = apiService.getLiveCategories(user, pass).body() ?: emptyList()
        for (category in liveCategories) {
            try {
                val streams = apiService.getLiveStreams(user, pass, category.categoryId.toInt()).body()
                if (!streams.isNullOrEmpty()) {
                    liveStreamDao.insertAll(streams)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val movieCategories = apiService.getMovieCategories(user, pass).body() ?: emptyList()
        for (category in movieCategories) {
            try {
                val movies = apiService.getMovies(user, pass, category.categoryId.toInt()).body()
                if (!movies.isNullOrEmpty()) {
                    movieDao.insertAll(movies)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val seriesCategories = apiService.getSeriesCategories(user, pass).body() ?: emptyList()
        for (category in seriesCategories) {
            try {
                val series = apiService.getSeries(user, pass, category.categoryId.toInt()).body()
                if (!series.isNullOrEmpty()) {
                    seriesDao.insertAll(series)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
