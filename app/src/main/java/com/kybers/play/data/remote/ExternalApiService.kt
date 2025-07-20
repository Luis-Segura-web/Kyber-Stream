package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.OMDbMovieResponse
import com.kybers.play.data.remote.model.TMDbSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz de Retrofit para las APIs externas (TMDb y OMDb).
 */
interface ExternalApiService {

    // --- Endpoints de TMDb ---

    @GET("search/movie")
    suspend fun searchMovieTMDb(
        @Query("api_key") apiKey: String,
        @Query("query") movieTitle: String,
        @Query("language") language: String = "es-ES" // Buscamos en español por defecto
    ): Response<TMDbSearchResponse>

    // --- Endpoints de OMDb ---

    @GET("/") // La ruta base ya contiene todo lo necesario
    suspend fun getMovieOMDb(
        @Query("apikey") apiKey: String,
        @Query("t") movieTitle: String
    ): Response<OMDbMovieResponse>
}
