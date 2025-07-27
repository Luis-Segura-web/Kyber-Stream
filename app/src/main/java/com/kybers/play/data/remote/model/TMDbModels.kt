package com.kybers.play.data.remote.model

import com.squareup.moshi.Json

// Unifica películas y series para manejarlas fácilmente en una sola lista.
sealed class FilmographyItem {
    abstract val id: Int
    abstract val title: String
    abstract val posterPath: String?
    abstract val mediaType: String // "movie" o "tv"

    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

data class TMDbSearchResponse(
    val results: List<TMDbMovieResult>?
)

data class TMDbTvSearchResponse(
    val results: List<TMDbTvResult>?
)

// --- ¡MODELO ACTUALIZADO! ---
// Ahora incluye la fecha de estreno para poder ordenar las colecciones.
data class TMDbMovieResult(
    override val id: Int,
    @Json(name = "poster_path") override val posterPath: String?,
    override val title: String,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "media_type") override val mediaType: String = "movie"
) : FilmographyItem()

data class TMDbTvResult(
    override val id: Int,
    @Json(name = "poster_path") override val posterPath: String?,
    @Json(name = "name") override val title: String,
    @Json(name = "media_type") override val mediaType: String = "tv"
) : FilmographyItem()

// --- ¡MODELO ACTUALIZADO! ---
// Ahora incluye información sobre la colección y películas similares.
data class TMDbMovieDetails(
    val id: Int,
    val title: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val credits: TMDbMovieCredits?,
    val recommendations: TMDbSearchResponse?,
    val similar: TMDbSearchResponse?,
    @Json(name = "release_dates") val releaseDates: TMDbReleaseDatesResponse?,
    @Json(name = "belongs_to_collection") val collection: TMDbCollection?
) {
    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getFullBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

data class TMDbTvDetails(
    val id: Int,
    @Json(name = "name") val name: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val credits: TMDbMovieCredits?,
    val recommendations: TMDbTvSearchResponse?,
    @Json(name = "content_ratings") val contentRatings: TMDbContentRatingsResponse?
) {
    fun getFullPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getFullBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

data class TMDbEpisodeDetails(
    @Json(name = "still_path") val stillPath: String?
) {
    fun getFullStillUrl(): String? = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

// --- ¡NUEVOS MODELOS PARA COLECCIONES! ---
data class TMDbCollection(
    val id: Int,
    val name: String,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?
)

data class TMDbCollectionDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    val parts: List<TMDbMovieResult>
)

data class TMDbMovieCredits(
    val cast: List<TMDbCastMember>?
)

data class TMDbPersonTvCredits(
    val cast: List<TMDbTvResult>?
)

data class TMDbCastMember(
    val id: Int,
    val name: String,
    val character: String?,
    @Json(name = "profile_path") val profilePath: String?
) {
    fun getFullProfileUrl(): String? = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

data class TMDbPerson(
    val id: Int,
    val name: String,
    val biography: String?,
    @Json(name = "profile_path") val profilePath: String?
)

data class TMDbPersonMovieCredits(
    val cast: List<TMDbMovieResult>?
)

data class TMDbReleaseDatesResponse(
    val results: List<TMDbReleaseDateResults>?
)

data class TMDbReleaseDateResults(
    @Json(name = "iso_3166_1") val countryCode: String,
    @Json(name = "release_dates") val releaseDates: List<TMDbReleaseDate>?
)

data class TMDbReleaseDate(
    val certification: String?,
    val type: Int
)

data class TMDbContentRatingsResponse(
    val results: List<TMDbContentRating>?
)

data class TMDbContentRating(
    @Json(name = "iso_3166_1") val countryCode: String,
    val rating: String?
)
