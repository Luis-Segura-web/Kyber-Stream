package com.kybers.play.data.remote.model

import com.squareup.moshi.Json

// Este archivo contiene todos los modelos de datos (Data Transfer Objects o DTOs)
// que se corresponden con las respuestas JSON de la API de Xtream Codes.
// La librería Moshi usará estas clases para convertir el JSON en objetos Kotlin.

/**
 * Representa la respuesta principal de la API al hacer login.
 */
data class XtreamResponse(
    @Json(name = "user_info") val userInfo: UserInfo?,
    @Json(name = "server_info") val serverInfo: ServerInfo?
)

/**
 * Información básica del usuario autenticado.
 */
data class UserInfo(
    @Json(name = "username") val username: String?,
    @Json(name = "password") val password: String?,
    @Json(name = "message") val message: String?,
    @Json(name = "auth") val auth: Int?,
    @Json(name = "status") val status: String?,
    @Json(name = "exp_date") val expDate: String?,
    @Json(name = "is_trial") val isTrial: String?,
    @Json(name = "active_cons") val activeCons: Int?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "max_connections") val maxConnections: String?,
    @Json(name = "allowed_output_formats") val allowedOutputFormats: List<String>?
)

/**
 * Información del servidor al que nos conectamos.
 */
data class ServerInfo(
    @Json(name = "url") val url: String?,
    @Json(name = "port") val port: String?,
    @Json(name = "https_port") val httpsPort: String?,
    @Json(name = "server_protocol") val serverProtocol: String?,
    @Json(name = "rtmp_port") val rtmpPort: String?,
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "timestamp_now") val timestampNow: Long?,
    @Json(name = "time_now") val timeNow: String?
)

/**
 * Representa una categoría, ya sea de canales en vivo, películas o series.
 */
data class Category(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int
)

/**
 * Representa un canal de TV en vivo.
 */
data class LiveStream(
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_type") val streamType: String,
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "epg_channel_id") val epgChannelId: String?,
    @Json(name = "added") val added: String,
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "tv_archive") val tvArchive: Int,
    @Json(name = "direct_source") val directSource: String,
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int
)

/**
 * Representa una película (VOD - Video On Demand).
 */
data class Movie(
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_type") val streamType: String,
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "rating") val rating: String?, // La API a veces devuelve "5.5" como String
    @Json(name = "rating_5based") val rating5Based: Float,
    @Json(name = "added") val added: String,
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "container_extension") val containerExtension: String,
    // Podríamos añadir más campos si los necesitamos para la pantalla de detalles
)

/**
 * Representa una serie.
 */
data class Series(
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
    @Json(name = "series_id") val seriesId: Int,
    @Json(name = "cover") val cover: String?,
    @Json(name = "plot") val plot: String?,
    @Json(name = "cast") val cast: String?,
    @Json(name = "director") val director: String?,
    @Json(name = "genre") val genre: String?,
    @Json(name = "releaseDate") val releaseDate: String?,
    @Json(name = "last_modified") val lastModified: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "rating_5based") val rating5Based: Float,
    @Json(name = "backdrop_path") val backdropPath: List<String>?,
    @Json(name = "youtube_trailer") val youtubeTrailer: String?,
    @Json(name = "episode_run_time") val episodeRunTime: String?,
    @Json(name = "category_id") val categoryId: String
)
