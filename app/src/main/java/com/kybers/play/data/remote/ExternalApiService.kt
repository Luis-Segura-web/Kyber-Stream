package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.OMDbMovieDetail
import com.kybers.play.data.remote.model.TMDbMovieDetails
import com.kybers.play.data.remote.model.TMDbPerson
import com.kybers.play.data.remote.model.TMDbPersonMovieCredits
import com.kybers.play.data.remote.model.TMDbSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExternalApiService {

    @GET("search/movie")
    suspend fun searchMovieTMDb(
        @Query("api_key") apiKey: String,
        @Query("query") movieTitle: String,
        @Query("year") year: String? = null, // Anotación incorrecta eliminada
        @Query("language") language: String = "es-ES"
    ): Response<TMDbSearchResponse>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetailsTMDb(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,recommendations,alternative_titles",
        @Query("language") language: String = "es-ES"
    ): Response<TMDbMovieDetails>

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

    @GET("/")
    suspend fun getMovieDetailsOMDb(
        @Query("apikey") apiKey: String,
        @Query("t") title: String,
        @Query("y") year: String? = null,
        @Query("plot") plot: String = "full"
    ): Response<OMDbMovieDetail>
}