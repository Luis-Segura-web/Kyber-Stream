package com.kybers.play.data.remote.model

import com.squareup.moshi.Json // ¡CORRECCIÓN! Importamos la anotación correcta.

// Este archivo es ahora la ÚNICA fuente de verdad para los modelos de TMDb.

data class TMDbSearchResponse(
    val results: List<TMDbMovieResult>?
)

data class TMDbMovieResult(
    val id: Int,
    @Json(name = "poster_path") val posterPath: String?, // ¡CORRECCIÓN! Usamos @Json
    val title: String?
) {
    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

data class TMDbMovieDetails(
    val id: Int,
    val title: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?, // ¡CORRECCIÓN! Usamos @Json
    @Json(name = "backdrop_path") val backdropPath: String?, // ¡CORRECCIÓN! Usamos @Json
    @Json(name = "release_date") val releaseDate: String?, // ¡CORRECCIÓN! Usamos @Json
    @Json(name = "vote_average") val voteAverage: Double?, // ¡CORRECCIÓN! Usamos @Json
    val credits: TMDbMovieCredits?,
    val recommendations: TMDbSearchResponse?,
    @Json(name = "alternative_titles") val alternativeTitles: TMDbAlternativeTitles? // ¡CORRECCIÓN! Usamos @Json
) {
    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getFullBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

data class TMDbMovieCredits(
    val cast: List<TMDbCastMember>?
)

data class TMDbAlternativeTitles(
    val titles: List<TMDbTitle>?
)

data class TMDbTitle(
    @Json(name = "iso_3166_1") val countryCode: String, // ¡CORRECCIÓN! Usamos @Json
    val title: String,
    val type: String
)

data class TMDbCastMember(
    val id: Int,
    val name: String,
    val character: String?,
    @Json(name = "profile_path") val profilePath: String? // ¡CORRECCIÓN! Usamos @Json
) {
    fun getFullProfileUrl(): String? = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

data class TMDbPerson(
    val id: Int,
    val name: String,
    val biography: String?,
    @Json(name = "profile_path") val profilePath: String? // ¡CORRECCIÓN! Usamos @Json
)

data class TMDbPersonMovieCredits(
    val cast: List<TMDbMovieResult>?
)