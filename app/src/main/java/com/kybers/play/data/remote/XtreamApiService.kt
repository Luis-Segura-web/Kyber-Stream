package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.EpgListingsResponse
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.XtreamResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz que define todos los endpoints de la API de Xtream Codes usando Retrofit.
 */
interface XtreamApiService {

    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<XtreamResponse>

    @GET("player_api.php?action=get_live_categories")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<List<Category>>

    @GET("player_api.php?action=get_live_streams")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: Int
    ): Response<List<LiveStream>>

    @GET("player_api.php?action=get_vod_categories")
    suspend fun getMovieCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<List<Category>>

    @GET("player_api.php?action=get_vod_streams")
    suspend fun getMovies(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: Int
    ): Response<List<Movie>>

    @GET("player_api.php?action=get_series_categories")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<List<Category>>

    @GET("player_api.php?action=get_series")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: Int
    ): Response<List<Series>>

    /**
     * ¡CORREGIDO! Se define la llamada a xmltv.php de la forma estándar de Retrofit.
     * Esto evita errores de URL malformada.
     */
    @GET("xmltv.php")
    suspend fun getXmlTvEpg(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<ResponseBody>

    /**
     * Endpoint de respaldo para EPG para un solo canal.
     */
    @GET("player_api.php?action=get_simple_data_table")
    suspend fun getSimpleDataTable(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int
    ): Response<EpgListingsResponse>
}
