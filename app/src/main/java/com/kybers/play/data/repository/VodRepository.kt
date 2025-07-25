package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio especializado en la gestión de contenido "Bajo Demanda" (VOD).
 * Se encarga de todo lo relacionado con Películas y Series.
 *
 * Hereda de [BaseContentRepository] para reutilizar la lógica común de obtención de categorías.
 *
 * @property xtreamApiService La instancia del servicio Retrofit para la API de Xtream.
 * @property movieDao El DAO para acceder a la tabla de películas en la base de datos local.
 * @property seriesDao El DAO para acceder a la tabla de series en la base de datos local.
 */
class VodRepository(
    xtreamApiService: XtreamApiService,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao
) : BaseContentRepository(xtreamApiService) {

    /**
     * Obtiene un Flow con todas las películas cacheadas en la base de datos para un usuario específico.
     *
     * @param userId El ID del usuario.
     * @return Un [Flow] que emite la lista de [Movie].
     */
    fun getAllMovies(userId: Int): Flow<List<Movie>> = movieDao.getAllMovies(userId)

    /**
     * Obtiene un Flow con todas las series cacheadas en la base de datos para un usuario específico.
     *
     * @param userId El ID del usuario.
     * @return Un [Flow] que emite la lista de [Series].
     */
    fun getAllSeries(userId: Int): Flow<List<Series>> = seriesDao.getAllSeries(userId)

    /**
     * Descarga y cachea todas las películas desde el servidor para un usuario.
     * Itera sobre cada categoría de películas, descarga los streams y los guarda en la base de datos.
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @param userId El ID del usuario para asociar los datos cacheados.
     * @return El número total de películas descargadas en esta sesión.
     */
    suspend fun cacheMovies(user: String, pass: String, userId: Int): Int {
        // Obtiene las categorías de películas desde la API (método heredado de la clase base).
        val movieCategories = getMovieCategories(user, pass)
        var downloadedMoviesCount = 0
        Log.d("VodRepository", "Iniciando descarga de películas para ${movieCategories.size} categorías.")

        for (category in movieCategories) {
            try {
                // Descarga las películas para la categoría actual.
                val movies = xtreamApiService.getMovies(
                    user = user,
                    pass = pass,
                    categoryId = category.categoryId.toInt()
                ).body()

                if (!movies.isNullOrEmpty()) {
                    // Asigna el userId a cada película antes de guardarla.
                    movies.forEach { it.userId = userId }
                    downloadedMoviesCount += movies.size
                    // Guarda las películas en la base de datos local.
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

    /**
     * Descarga y cachea todas las series desde el servidor para un usuario.
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @param userId El ID del usuario para asociar los datos cacheados.
     */
    suspend fun cacheSeries(user: String, pass: String, userId: Int) {
        // Obtiene las categorías de series.
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
        // Reemplaza todas las series existentes para este usuario con la nueva lista.
        seriesDao.replaceAll(allSeriesForUser, userId)
        Log.d("VodRepository", "Descarga de series completada. Total: ${allSeriesForUser.size}")
    }
}
