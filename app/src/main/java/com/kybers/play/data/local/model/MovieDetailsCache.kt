package com.kybers.play.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa una fila en la tabla de caché para los detalles de películas.
 * Esta tabla almacena la información enriquecida obtenida de APIs externas como TMDb.
 *
 * @param streamId El ID de la película de nuestro proveedor de IPTV. Actúa como clave primaria.
 * @param plot La sinopsis o descripción de la película.
 * @param backdropUrl La URL de la imagen de fondo (backdrop) de alta resolución.
 * @param rating La calificación de la película (normalmente sobre 10).
 * @param lastUpdated La marca de tiempo (timestamp) de cuándo se guardó este caché por última vez.
 * Nos servirá para saber si los datos son antiguos y necesitan actualizarse.
 */
@Entity(tableName = "movie_details_cache")
data class MovieDetailsCache(
    @PrimaryKey
    val streamId: Int,
    val plot: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val lastUpdated: Long
)
