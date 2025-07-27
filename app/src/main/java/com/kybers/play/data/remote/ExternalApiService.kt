package com.kybers.play.data.remote

import com.kybers.play.data.remote.model.OMDbMovieDetail
import com.kybers.play.data.remote.model.TMDbCollectionDetails
import com.kybers.play.data.remote.model.TMDbEpisodeDetails
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

    @GET("movie/popular")
    suspend fun getPopularMoviesTMDb(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX",
        @Query("page") page: Int = 1
    ): Response<TMDbSearchResponse>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetailsTMDb(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,recommendations,similar,release_dates",
        @Query("language") language: String = "es-MX"
    ): Response<TMDbMovieDetails>

    @GET("collection/{collection_id}")
    suspend fun getCollectionDetails(
        @Path("collection_id") collectionId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX"
    ): Response<TMDbCollectionDetails>

    @GET("tv/{tv_id}")
    suspend fun getTvDetailsTMDb(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX",
        @Query("append_to_response") appendToResponse: String = "content_ratings,credits,recommendations"
    ): Response<TMDbTvDetails>

    @GET("tv/{tv_id}/season/{season_number}/episode/{episode_number}")
    suspend fun getEpisodeDetailsTMDb(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX"
    ): Response<TMDbEpisodeDetails>

    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX"
    ): Response<TMDbPerson>

    @GET("person/{person_id}/movie_credits")
    suspend fun getPersonMovieCredits(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX"
    ): Response<TMDbPersonMovieCredits>

    @GET("person/{person_id}/tv_credits")
    suspend fun getPersonTvCredits(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX"
    ): Response<TMDbPersonTvCredits>

    @GET("/")
    suspend fun getMovieDetailsOMDb(
        @Query("apikey") apiKey: String,
        @Query("t") title: String,
        @Query("y") year: String? = null,
        @Query("plot") plot: String = "full"
    ): Response<OMDbMovieDetail>
}
