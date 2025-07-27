package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.data.local.CategoryCacheDao
import com.kybers.play.data.local.EpisodeDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.local.model.CategoryCache
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.SeriesInfo
import com.kybers.play.data.remote.model.SeriesInfoResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * --- ¡REPOSITORIO ACTUALIZADO CON CACHÉ DE CATEGORÍAS! ---
 * Ahora, primero busca las categorías de películas y series en la base de datos local.
 */
class VodRepository(
    xtreamApiService: XtreamApiService,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryCacheDao: CategoryCacheDao
) : BaseContentRepository(xtreamApiService) {

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val categoryCacheExpiry = TimeUnit.HOURS.toMillis(4) // Categorías expiran cada 4 horas

    override suspend fun getMovieCategories(user: String, pass: String, userId: Int): List<Category> {
        val cached = categoryCacheDao.getCategories(userId, "movie")
        if (cached != null && System.currentTimeMillis() - cached.lastUpdated < categoryCacheExpiry) {
            Log.d("VodRepository", "Cargando categorías de Películas desde la caché.")
            return try {
                val type = Types.newParameterizedType(List::class.java, Category::class.java)
                val adapter = moshi.adapter<List<Category>>(type)
                adapter.fromJson(cached.categoriesJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        Log.d("VodRepository", "Cargando categorías de Películas desde la red.")
        return try {
            val response = xtreamApiService.getMovieCategories(user = user, pass = pass)
            val categories = response.body() ?: emptyList()
            if (categories.isNotEmpty()) {
                val type = Types.newParameterizedType(List::class.java, Category::class.java)
                val adapter = moshi.adapter<List<Category>>(type)
                val json = adapter.toJson(categories)
                categoryCacheDao.insertOrUpdate(
                    CategoryCache(userId = userId, type = "movie", categoriesJson = json, lastUpdated = System.currentTimeMillis())
                )
            }
            categories
        } catch (e: IOException) {
            emptyList()
        }
    }

    override suspend fun getSeriesCategories(user: String, pass: String, userId: Int): List<Category> {
        val cached = categoryCacheDao.getCategories(userId, "series")
        if (cached != null && System.currentTimeMillis() - cached.lastUpdated < categoryCacheExpiry) {
            Log.d("VodRepository", "Cargando categorías de Series desde la caché.")
            return try {
                val type = Types.newParameterizedType(List::class.java, Category::class.java)
                val adapter = moshi.adapter<List<Category>>(type)
                adapter.fromJson(cached.categoriesJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        Log.d("VodRepository", "Cargando categorías de Series desde la red.")
        return try {
            val response = xtreamApiService.getSeriesCategories(user = user, pass = pass)
            val categories = response.body() ?: emptyList()
            if (categories.isNotEmpty()) {
                val type = Types.newParameterizedType(List::class.java, Category::class.java)
                val adapter = moshi.adapter<List<Category>>(type)
                val json = adapter.toJson(categories)
                categoryCacheDao.insertOrUpdate(
                    CategoryCache(userId = userId, type = "series", categoriesJson = json, lastUpdated = System.currentTimeMillis())
                )
            }
            categories
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun getAllMovies(userId: Int): Flow<List<Movie>> = movieDao.getAllMovies(userId)

    suspend fun cacheMovies(user: String, pass: String, userId: Int): Int {
        val movieCategories = getMovieCategories(user, pass, userId)
        var downloadedMoviesCount = 0
        Log.d("VodRepository", "Iniciando descarga de películas para ${movieCategories.size} categorías.")

        for (category in movieCategories) {
            try {
                val movies = xtreamApiService.getMovies(
                    user = user,
                    pass = pass,
                    categoryId = category.categoryId.toInt()
                ).body()

                if (!movies.isNullOrEmpty()) {
                    movies.forEach { it.userId = userId }
                    downloadedMoviesCount += movies.size
                    movieDao.cacheMovies(movies, userId)
                }
            } catch (e: Exception) {
                Log.e("VodRepository", "Error al descargar películas de la categoría ${category.categoryId}", e)
            }
        }
        Log.d("VodRepository", "Descarga de películas completada. Total descargado: $downloadedMoviesCount")
        return downloadedMoviesCount
    }

    fun getAllSeries(userId: Int): Flow<List<Series>> = seriesDao.getAllSeries(userId)

    suspend fun cacheSeries(user: String, pass: String, userId: Int) {
        val seriesCategories = getSeriesCategories(user, pass, userId)
        val allSeriesForUser = mutableListOf<Series>()
        Log.d("VodRepository", "Iniciando descarga de series para ${seriesCategories.size} categorías.")

        for (category in seriesCategories) {
            try {
                val series = xtreamApiService.getSeries(
                    user = user,
                    pass = pass,
                    categoryId = category.categoryId.toInt()
                ).body()

                if (!series.isNullOrEmpty()) {
                    series.forEach { it.userId = userId }
                    allSeriesForUser.addAll(series)
                }
            } catch (e: Exception) {
                Log.e("VodRepository", "Error al descargar series de la categoría ${category.categoryId}", e)
            }
        }
        seriesDao.replaceAll(allSeriesForUser, userId)
        Log.d("VodRepository", "Descarga de series completada. Total: ${allSeriesForUser.size}")
    }

    fun getSeriesDetails(user: String, pass: String, seriesId: Int, userId: Int): Flow<SeriesInfoResponse?> = flow {
        val cachedEpisodes = episodeDao.getEpisodesForSeries(seriesId, userId).first()
        if (cachedEpisodes.isNotEmpty()) {
            val seriesInfoFromDb = seriesDao.getAllSeries(userId).first().find { it.seriesId == seriesId }
            if (seriesInfoFromDb != null) {
                val seasons = cachedEpisodes.map { it.season }.distinct().sorted()
                    .map { com.kybers.play.data.remote.model.Season(it, "Temporada $it") }
                val episodesBySeason = cachedEpisodes.groupBy { it.season.toString() }

                val seriesInfoForResponse = SeriesInfo(
                    name = seriesInfoFromDb.name,
                    cover = seriesInfoFromDb.cover,
                    plot = seriesInfoFromDb.plot,
                    cast = seriesInfoFromDb.cast,
                    director = seriesInfoFromDb.director,
                    genre = seriesInfoFromDb.genre,
                    releaseDate = seriesInfoFromDb.releaseDate,
                    lastModified = seriesInfoFromDb.lastModified,
                    rating = seriesInfoFromDb.rating,
                    rating5Based = seriesInfoFromDb.rating5Based,
                    backdropPath = seriesInfoFromDb.backdropPath,
                    youtubeTrailer = seriesInfoFromDb.youtubeTrailer,
                    episodeRunTime = seriesInfoFromDb.episodeRunTime,
                    categoryId = seriesInfoFromDb.categoryId
                )
                emit(SeriesInfoResponse(seriesInfoForResponse, seasons, episodesBySeason))
                return@flow
            }
        }

        try {
            val response = xtreamApiService.getSeriesInfo(user, pass, seriesId = seriesId)
            if (response.isSuccessful) {
                val seriesInfoResponse = response.body()
                seriesInfoResponse?.let { originalResponse ->
                    val processedEpisodes = originalResponse.episodes.values.flatten().map { episode ->
                        episode.apply {
                            this.seriesId = seriesId
                            this.userId = userId
                            this.plot = episode.info?.plot
                            this.imageUrl = episode.info?.imageUrl
                            this.rating = episode.info?.rating?.toFloatOrNull()
                            this.duration = episode.info?.duration
                            this.releaseDate = episode.info?.releaseDate
                        }
                    }
                    episodeDao.replaceAll(processedEpisodes, seriesId, userId)
                    val processedEpisodesBySeason = processedEpisodes.groupBy { it.season.toString() }
                    val newResponse = originalResponse.copy(episodes = processedEpisodesBySeason)
                    emit(newResponse)
                }
            } else {
                emit(null)
            }
        } catch (e: Exception) {
            emit(null)
        }
    }

    fun getEpisodesForSeries(seriesId: Int, userId: Int): Flow<List<Episode>> {
        return episodeDao.getEpisodesForSeries(seriesId, userId)
    }
}
