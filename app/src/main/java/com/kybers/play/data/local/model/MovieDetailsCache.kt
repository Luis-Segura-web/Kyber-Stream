package com.kybers.play.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * --- ¡MODELO DE CACHÉ ACTUALIZADO! ---
 * Ahora guarda también la información de la colección y las películas similares.
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
    val certification: String?,
    val castJson: String?,
    val recommendationsJson: String?,
    val similarJson: String?,
    val collectionId: Int?,
    val collectionName: String?,
    val lastUpdated: Long
)
