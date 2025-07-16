package com.kybers.play.data.remote.model

import com.squareup.moshi.Json

// Este archivo contiene todos los modelos de datos (Data Transfer Objects o DTOs)
// que se corresponden con las respuestas JSON de la API de Xtream Codes.
// La librería Moshi usará estas clases para convertir el JSON en objetos Kotlin.

/**
 * Representa la respuesta principal de la API al hacer login.
 */
data class XtreamResponse(
    @param:Json(name = "user_info")    val userInfo: UserInfo?,
    @param:Json(name = "server_info")  val serverInfo: ServerInfo?
)

/**
 * Información básica del usuario autenticado.
 */
data class UserInfo(
    @param:Json(name = "username")                val username: String?,
    @param:Json(name = "password")                val password: String?,
    @param:Json(name = "message")                 val message: String?,
    @param:Json(name = "auth")                    val auth: Int?,
    @param:Json(name = "status")                  val status: String?,
    @param:Json(name = "exp_date")                val expDate: String?,
    @param:Json(name = "is_trial")                val isTrial: String?,
    @param:Json(name = "active_cons")             val activeCons: Int?,
    @param:Json(name = "created_at")              val createdAt: String?,
    @param:Json(name = "max_connections")         val maxConnections: String?,
    @param:Json(name = "allowed_output_formats")  val allowedOutputFormats: List<String>?
)

/**
 * Información del servidor al que nos conectamos.
 */
data class ServerInfo(
    @param:Json(name = "url")            val url: String?,
    @param:Json(name = "port")           val port: String?,
    @param:Json(name = "https_port")     val httpsPort: String?,
    @param:Json(name = "server_protocol")val serverProtocol: String?,
    @param:Json(name = "rtmp_port")      val rtmpPort: String?,
    @param:Json(name = "timezone")       val timezone: String?,
    @param:Json(name = "timestamp_now")  val timestampNow: Long?,
    @param:Json(name = "time_now")       val timeNow: String?
)

/**
 * Representa una categoría, ya sea de canales en vivo, películas o series.
 */
data class Category(
    @param:Json(name = "category_id")    val categoryId: String,
    @param:Json(name = "category_name")  val categoryName: String,
    @param:Json(name = "parent_id")      val parentId: Int
)

/**
 * Representa un canal de TV en vivo.
 */
data class LiveStream(
    @param:Json(name = "num")              val num: Int,
    @param:Json(name = "name")             val name: String,
    @param:Json(name = "stream_type")      val streamType: String,
    @param:Json(name = "stream_id")        val streamId: Int,
    @param:Json(name = "stream_icon")      val streamIcon: String?,
    @param:Json(name = "epg_channel_id")   val epgChannelId: String?,
    @param:Json(name = "added")            val added: String,
    @param:Json(name = "category_id")      val categoryId: String,
    @param:Json(name = "tv_archive")       val tvArchive: Int,
    @param:Json(name = "direct_source")    val directSource: String,
    @param:Json(name = "tv_archive_duration") val tvArchiveDuration: Int
)

/**
 * Representa una película (VOD - Video On Demand).
 */
data class Movie(
    @param:Json(name = "num")               val num: Int,
    @param:Json(name = "name")              val name: String,
    @param:Json(name = "stream_type")       val streamType: String,
    @param:Json(name = "stream_id")         val streamId: Int,
    @param:Json(name = "stream_icon")       val streamIcon: String?,
    @param:Json(name = "rating")            val rating: String?,
    @param:Json(name = "rating_5based")     val rating5Based: Float,
    @param:Json(name = "added")             val added: String,
    @param:Json(name = "category_id")       val categoryId: String,
    @param:Json(name = "container_extension") val containerExtension: String
)

/**
 * Representa una serie.
 */
data class Series(
    @param:Json(name = "num")                val num: Int,
    @param:Json(name = "name")               val name: String,
    @param:Json(name = "series_id")          val seriesId: Int,
    @param:Json(name = "cover")              val cover: String?,
    @param:Json(name = "plot")               val plot: String?,
    @param:Json(name = "cast")               val cast: String?,
    @param:Json(name = "director")           val director: String?,
    @param:Json(name = "genre")              val genre: String?,
    @param:Json(name = "releaseDate")        val releaseDate: String?,
    @param:Json(name = "last_modified")      val lastModified: String?,
    @param:Json(name = "rating")             val rating: String?,
    @param:Json(name = "rating_5based")      val rating5Based: Float,
    @param:Json(name = "backdrop_path")      val backdropPath: List<String>?,
    @param:Json(name = "youtube_trailer")    val youtubeTrailer: String?,
    @param:Json(name = "episode_run_time")   val episodeRunTime: String?,
    @param:Json(name = "category_id")        val categoryId: String
)
