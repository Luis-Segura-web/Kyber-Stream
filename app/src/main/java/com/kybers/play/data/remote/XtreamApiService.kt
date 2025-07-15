package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.XtreamResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz que define todos los endpoints de la API de Xtream Codes usando Retrofit.
 * Cada función corresponde a una acción específica de la API.
 */
interface XtreamApiService {

    /**
     * Autentica a un usuario y obtiene su información básica.
     * La URL final será: {baseUrl}/player_api.php?username=...&password=...
     * @param username El nombre de usuario.
     * @param password La contraseña.
     * @return Un objeto XtreamResponse envuelto en un Response de Retrofit, lo que nos permite
     * verificar el código de estado de la respuesta (ej. 200 OK, 404 Not Found).
     */
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<XtreamResponse>

    /**
     * Obtiene la lista de categorías de canales en vivo.
     */
    @GET("player_api.php?action=get_live_categories")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<List<Category>>

    /**
     * Obtiene la lista de canales en vivo para una categoría específica.
     * @param categoryId El ID de la categoría de la cual obtener los canales.
     */
    @GET("player_api.php?action=get_live_streams")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: Int
    ): Response<List<LiveStream>>

    /**
     * Obtiene la lista de categorías de películas.
     */
    @GET("player_api.php?action=get_vod_categories")
    suspend fun getMovieCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<List<Category>>

    /**
     * Obtiene la lista de películas para una categoría específica.
     * @param categoryId El ID de la categoría de la cual obtener las películas.
     */
    @GET("player_api.php?action=get_vod_streams")
    suspend fun getMovies(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: Int
    ): Response<List<Movie>>

    /**
     * Obtiene la lista de categorías de series.
     */
    @GET("player_api.php?action=get_series_categories")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<List<Category>>

    /**
     * Obtiene la lista de series para una categoría específica.
     * @param categoryId El ID de la categoría de la cual obtener las series.
     */
    @GET("player_api.php?action=get_series")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: Int
    ): Response<List<Series>>

    // Aquí podríamos añadir más llamadas en el futuro, como get_series_info, etc.
}
