package com.kybers.play.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * --- ¡NUEVO MODELO! ---
 * Representa la tabla de caché para los detalles de las series obtenidos de TMDb.
 *
 * @param seriesId El ID de la serie de nuestro proveedor. Es la clave primaria.
 * @param tmdbId El ID único de la serie en la base de datos de TMDb.
 * @param plot La sinopsis detallada de la serie.
 * @param posterUrl La URL del póster de alta calidad.
 * @param backdropUrl La URL de la imagen de fondo de alta calidad.
 * @param firstAirYear El año de estreno de la serie.
 * @param rating La calificación de la serie (sobre 10).
 * @param certification La clasificación por edades (ej. "TV-14").
 * @param castJson La lista del reparto principal, guardada como un string en formato JSON.
 * @param recommendationsJson La lista de series recomendadas, en formato JSON.
 * @param lastUpdated Timestamp de la última actualización para controlar la caducidad del caché.
 */
@Entity(tableName = "series_details_cache")
data class SeriesDetailsCache(
    @PrimaryKey
    val seriesId: Int,
    val tmdbId: Int?,
    val plot: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val firstAirYear: String?,
    val rating: Double?,
    val certification: String?,
    val castJson: String?,
    val recommendationsJson: String?,
    val lastUpdated: Long
)
