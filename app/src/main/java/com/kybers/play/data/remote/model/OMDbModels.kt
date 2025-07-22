package com.kybers.play.data.remote.model

import com.squareup.moshi.Json

/**
 * Representa la respuesta de detalles de una película desde la API de OMDb.
 * Los campos están anotados con @Json para que coincidan con los nombres de la API.
 */
data class OMDbMovieDetail(
    @Json(name = "Title") val title: String?,
    @Json(name = "Year") val year: String?,
    @Json(name = "Rated") val rated: String?,
    @Json(name = "Released") val released: String?,
    @Json(name = "Runtime") val runtime: String?,
    @Json(name = "Genre") val genre: String?,
    @Json(name = "Director") val director: String?,
    @Json(name = "Writer") val writer: String?,
    @Json(name = "Actors") val actors: String?,
    @Json(name = "Plot") val plot: String?,
    @Json(name = "Language") val language: String?,
    @Json(name = "Country") val country: String?,
    @Json(name = "Awards") val awards: String?,
    @Json(name = "Poster") val poster: String?,
    @Json(name = "imdbRating") val imdbRating: String?,
    @Json(name = "imdbID") val imdbID: String?,
    @Json(name = "Type") val type: String?,
    @Json(name = "Response") val response: String, // "True" o "False"
    @Json(name = "Error") val error: String?
)