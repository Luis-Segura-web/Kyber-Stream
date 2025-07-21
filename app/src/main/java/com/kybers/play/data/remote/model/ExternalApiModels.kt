package com.kybers.play.data.remote.model

import com.squareup.moshi.Json

// --- Modelos para TMDb (The Movie Database) ---

data class TMDbSearchResponse(
    @Json(name = "results") val results: List<TMDbMovieResult>
)

data class TMDbMovieResult(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?
) {
    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getFullBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
}

data class TMDbMovieDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "credits") val credits: TMDbCredits?,
    @Json(name = "recommendations") val recommendations: TMDbRecommendations?,
    @Json(name = "alternative_titles") val alternativeTitles: TMDbAlternativeTitlesResponse?
) {
    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getFullBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
}

data class TMDbCredits(
    @Json(name = "cast") val cast: List<TMDbCastMember>?
)

data class TMDbCastMember(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String?,
    @Json(name = "profile_path") val profilePath: String?
) {
    fun getFullProfileUrl(): String? = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

data class TMDbRecommendations(
    @Json(name = "results") val results: List<TMDbMovieResult>?
)

data class TMDbPersonMovieCredits(
    @Json(name = "cast") val cast: List<TMDbMovieResult>
)

data class TMDbAlternativeTitlesResponse(
    @Json(name = "titles") val titles: List<AlternativeTitle>
)

data class AlternativeTitle(
    @Json(name = "iso_3166_1") val countryCode: String,
    @Json(name = "title") val title: String
)

/**
 * ¡NUEVO! Representa los detalles de una persona, incluyendo su biografía.
 */
data class TMDbPersonDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "biography") val biography: String?
)

// --- Modelos para OMDb (The Open Movie Database) ---

data class OMDbMovieResponse(
    @Json(name = "Title") val title: String?,
    @Json(name = "Year") val year: String?,
    @Json(name = "Plot") val plot: String?,
    @Json(name = "Poster") val poster: String?,
    @Json(name = "imdbRating") val imdbRating: String?,
    @Json(name = "Response") val response: String
) {
    fun hasSucceeded(): Boolean = response.equals("True", ignoreCase = true)
}
