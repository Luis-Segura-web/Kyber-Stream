package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.OMDbMovieResponse
import com.kybers.play.data.remote.model.TMDbMovieDetails
import com.kybers.play.data.remote.model.TMDbPersonDetails
import com.kybers.play.data.remote.model.TMDbPersonMovieCredits
import com.kybers.play.data.remote.model.TMDbSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExternalApiService {

    // --- Endpoints de TMDb ---

    @GET("search/movie")
    suspend fun searchMovieTMDb(
        @Query("api_key") apiKey: String,
        @Query("query") movieTitle: String,
        @Query("language") language: String = "es-ES",
        @Query("year") year: String? = null
    ): Response<TMDbSearchResponse>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetailsTMDb(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES",
        @Query("append_to_response") appendToResponse: String = "credits,recommendations,alternative_titles"
    ): Response<TMDbMovieDetails>

    @GET("person/{person_id}/movie_credits")
    suspend fun getPersonMovieCredits(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES"
    ): Response<TMDbPersonMovieCredits>

    /**
     * ¡NUEVO ENDPOINT!
     * Obtiene los detalles de una persona, incluyendo su biografía.
     */
    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-ES"
    ): Response<TMDbPersonDetails>


    // --- Endpoints de OMDb ---

    @GET("/")
    suspend fun getMovieOMDb(
        @Query("apikey") apiKey: String,
        @Query("t") movieTitle: String
    ): Response<OMDbMovieResponse>
}
