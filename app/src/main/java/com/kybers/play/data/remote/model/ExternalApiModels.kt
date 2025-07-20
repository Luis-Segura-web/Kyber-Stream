package com.kybers.play.data.remote.model

import com.squareup.moshi.Json

// --- Modelos para TMDb (The Movie Database) ---

/**
 * Representa la respuesta completa de una búsqueda de películas en TMDb.
 */
data class TMDbSearchResponse(
    @Json(name = "results") val results: List<TMDbMovieResult>
)

/**
 * Representa un único resultado de película en la lista de búsqueda de TMDb.
 */
data class TMDbMovieResult(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?
) {
    /**
     * Función de utilidad para obtener la URL completa del póster.
     */
    fun getFullPosterUrl(): String? {
        return posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }

    /**
     * Función de utilidad para obtener la URL completa de la imagen de fondo.
     */
    fun getFullBackdropUrl(): String? {
        return backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    }
}


// --- Modelos para OMDb (The Open Movie Database) ---

/**
 * Representa la respuesta de una búsqueda de película por título en OMDb.
 * Nota: Los nombres de las variables coinciden con los de la API (PascalCase).
 */
data class OMDbMovieResponse(
    @Json(name = "Title") val title: String?,
    @Json(name = "Year") val year: String?,
    @Json(name = "Plot") val plot: String?,
    @Json(name = "Poster") val poster: String?,
    @Json(name = "imdbRating") val imdbRating: String?,
    @Json(name = "Response") val response: String // "True" or "False"
) {
    fun hasSucceeded(): Boolean = response.equals("True", ignoreCase = true)
}
