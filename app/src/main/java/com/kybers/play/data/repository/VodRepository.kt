package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.data.local.EpisodeDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.SeriesInfo
import com.kybers.play.data.remote.model.SeriesInfoResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class VodRepository(
    xtreamApiService: XtreamApiService,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao
) : BaseContentRepository(xtreamApiService) {

    fun getAllMovies(userId: Int): Flow<List<Movie>> = movieDao.getAllMovies(userId)

    suspend fun cacheMovies(user: String, pass: String, userId: Int): Int {
        val movieCategories = getMovieCategories(user, pass)
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
                    Log.d("VodRepository", "Categoría '${category.categoryName}': ${movies.size} películas cacheadas.")
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
        val seriesCategories = getSeriesCategories(user, pass)
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
            Log.d("VodRepository", "Detalles de la serie $seriesId encontrados en caché.")
            val seriesInfoFromDb = seriesDao.getAllSeries(userId).first().find { it.seriesId == seriesId }
            if (seriesInfoFromDb != null) {
                val seasons = cachedEpisodes.map { it.season }.distinct().sorted()
                    .map { com.kybers.play.data.remote.model.Season(it, "Temporada $it") }
                val episodesBySeason = cachedEpisodes.groupBy { it.season.toString() }

                // --- ¡CORRECCIÓN! ---
                // Creamos un objeto SeriesInfo a partir del Series que tenemos en la base de datos.
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

        Log.d("VodRepository", "No hay caché para la serie $seriesId. Solicitando a la red.")
        try {
            val response = xtreamApiService.getSeriesInfo(user, pass, seriesId = seriesId)
            if (response.isSuccessful) {
                val seriesInfoResponse = response.body()
                seriesInfoResponse?.let {
                    val processedEpisodes = it.episodes.values.flatten().map { episode ->
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
                    emit(it)
                }
            } else {
                Log.e("VodRepository", "Error en la API al obtener detalles de la serie $seriesId: ${response.code()}")
                emit(null)
            }
        } catch (e: Exception) {
            Log.e("VodRepository", "Excepción al obtener detalles de la serie $seriesId", e)
            emit(null)
        }
    }

    fun getEpisodesForSeries(seriesId: Int, userId: Int): Flow<List<Episode>> {
        return episodeDao.getEpisodesForSeries(seriesId, userId)
    }
}
