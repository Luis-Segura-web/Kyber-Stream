package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.OMDbMovieDetail
import com.kybers.play.data.remote.model.TMDbMovieDetails
import com.kybers.play.data.remote.model.TMDbPerson
import com.kybers.play.data.remote.model.TMDbPersonMovieCredits
import com.kybers.play.data.remote.model.TMDbPersonTvCredits
import com.kybers.play.data.remote.model.TMDbSearchResponse
import com.kybers.play.data.remote.model.TMDbTvDetails
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExternalApiService {

    @GET("movie/{movie_id}")
    suspend fun getMovieDetailsTMDb(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,recommendations,release_dates",
        @Query("language") language: String = "es-ES"
    ): Response<TMDbMovieDetails>

    // --- ¡NUEVO ENDPOINT AÑADIDO! ---
    // Para obtener los detalles de una serie de TV, incluyendo su clasificación por edades.
    @GET("tv/{tv_id}")
    suspend fun getTvDetailsTMDb(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES",
        @Query("append_to_response") appendToResponse: String = "content_ratings"
    ): Response<TMDbTvDetails>

    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES"
    ): Response<TMDbPerson>

    @GET("person/{person_id}/movie_credits")
    suspend fun getPersonMovieCredits(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES"
    ): Response<TMDbPersonMovieCredits>

    @GET("person/{person_id}/tv_credits")
    suspend fun getPersonTvCredits(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES"
    ): Response<TMDbPersonTvCredits>

    @GET("/")
    suspend fun getMovieDetailsOMDb(
        @Query("apikey") apiKey: String,
        @Query("t") title: String,
        @Query("y") year: String? = null,
        @Query("plot") plot: String = "full"
    ): Response<OMDbMovieDetail>
}