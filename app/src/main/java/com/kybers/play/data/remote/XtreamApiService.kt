package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.SeriesInfoResponse
import com.kybers.play.data.remote.model.XtreamResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApiService {

    // --- ¡NUEVA FUNCIÓN AÑADIDA! ---
    // Esta función nos permitirá obtener los detalles de la cuenta del usuario.
    @GET("player_api.php")
    suspend fun getUserInfo(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_user_info"
    ): Response<XtreamResponse>

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_categories"
    ): Response<List<Category>>

    @GET("player_api.php")
    suspend fun getMovieCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_vod_categories"
    ): Response<List<Category>>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_series_categories"
    ): Response<List<Category>>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: Int
    ): Response<List<LiveStream>>

    @GET("player_api.php")
    suspend fun getMovies(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: Int
    ): Response<List<Movie>>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: Int
    ): Response<List<Series>>

    @GET("xmltv.php")
    suspend fun getXmlTvEpg(
        @Query("username") user: String,
        @Query("password") pass: String
    ): Response<ResponseBody>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): Response<SeriesInfoResponse>
}
