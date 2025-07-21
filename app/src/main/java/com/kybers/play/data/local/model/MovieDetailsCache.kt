package com.kybers.play.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ¡MODELO ACTUALIZADO!
 * Representa la tabla de caché para los detalles de películas.
 * Ahora almacena mucha más información enriquecida de TMDb.
 *
 * @param streamId El ID de la película de nuestro proveedor. Es la clave primaria.
 * @param tmdbId El ID único de la película en la base de datos de TMDb.
 * @param plot La sinopsis de la película.
 * @param posterUrl La URL del póster de la película.
 * @param backdropUrl La URL de la imagen de fondo (backdrop).
 * @param releaseYear El año de lanzamiento.
 * @param rating La calificación de la película (sobre 10).
 * @param castJson La lista del reparto principal, guardada como un string en formato JSON.
 * @param recommendationsJson La lista de películas recomendadas, en formato JSON.
 * @param lastUpdated Timestamp de la última actualización para controlar la caducidad del caché.
 */
@Entity(tableName = "movie_details_cache")
data class MovieDetailsCache(
    @PrimaryKey
    val streamId: Int,
    val tmdbId: Int?,
    val plot: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val rating: Double?,
    val castJson: String?,
    val recommendationsJson: String?,
    val lastUpdated: Long
)
