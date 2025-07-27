package com.kybers.play.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * --- ¡NUEVO MODELO DE CACHÉ! ---
 * Representa la tabla de caché para los detalles de los actores (biografía y filmografía).
 *
 * @param actorId El ID del actor en TMDB. Es la clave primaria.
 * @param biography La biografía del actor.
 * @param filmographyJson La filmografía completa del actor, guardada como un string en formato JSON.
 * @param lastUpdated Timestamp de la última actualización para controlar la caducidad del caché.
 */
@Entity(tableName = "actor_details_cache")
data class ActorDetailsCache(
    @PrimaryKey
    val actorId: Int,
    val biography: String?,
    val filmographyJson: String?,
    val lastUpdated: Long
)

/**
 * --- ¡NUEVO MODELO DE CACHÉ! ---
 * Representa la tabla de caché para los detalles extra de los episodios, como la imagen de TMDB.
 *
 * @param episodeId El ID del episodio del proveedor. Es la clave primaria.
 * @param imageUrl La URL de la imagen del episodio obtenida de TMDB.
 * @param lastUpdated Timestamp de la última actualización.
 */
@Entity(tableName = "episode_details_cache")
data class EpisodeDetailsCache(
    @PrimaryKey
    val episodeId: String,
    val imageUrl: String?,
    val lastUpdated: Long
)

/**
 * --- ¡NUEVO MODELO DE CACHÉ! ---
 * Representa la tabla de caché para las listas de categorías.
 *
 * @param id El ID autogenerado para la entrada.
 * @param userId El ID del usuario al que pertenecen estas categorías.
 * @param type El tipo de categoría ("live", "movie", "series").
 * @param categoriesJson La lista de categorías, guardada como un string en formato JSON.
 * @param lastUpdated Timestamp de la última actualización.
 */
@Entity(tableName = "category_cache")
data class CategoryCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val type: String,
    val categoriesJson: String,
    val lastUpdated: Long
)
