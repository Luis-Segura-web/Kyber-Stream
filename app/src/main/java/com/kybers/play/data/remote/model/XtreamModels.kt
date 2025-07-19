package com.kybers.play.data.remote.model

import androidx.room.Entity
import androidx.room.Ignore
import com.squareup.moshi.Json
import android.util.Base64

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
 * Representa un canal de TV en vivo. Ahora también es una entidad de Room.
 * 'userId' se mueve fuera del constructor principal y es 'var'.
 * ¡MODIFICADO! currentEpgEvent y nextEpgEvent usan @Ignore para Room.
 */
@Entity(tableName = "live_streams", primaryKeys = ["streamId", "userId"])
data class LiveStream(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_type") val streamType: String,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "epg_channel_id") val epgChannelId: String?,
    @Json(name = "added") val added: String,
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "tv_archive") val tvArchive: Int,
    @Json(name = "direct_source") val directSource: String,
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int
) {
    var userId: Int = 0
    @Ignore var currentEpgEvent: EpgEvent? = null
    @Ignore var nextEpgEvent: EpgEvent? = null
}

/**
 * Representa una película (VOD - Video On Demand). Es una entidad de Room.
 * 'userId' se mueve fuera del constructor principal y es 'var'.
 */
@Entity(tableName = "movies", primaryKeys = ["streamId", "userId"])
data class Movie(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_type") val streamType: String,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "rating_5based") val rating5Based: Float,
    @Json(name = "added") val added: String,
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "container_extension") val containerExtension: String
) {
    var userId: Int = 0
}

/**
 * Representa una serie. Es una entidad de Room.
 * 'userId' se mueve fuera del constructor principal y es 'var'.
 */
@Entity(tableName = "series", primaryKeys = ["seriesId", "userId"])
data class Series(
    @Json(name = "series_id") val seriesId: Int,
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
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
) {
    var userId: Int = 0
}

// --- ¡NUEVOS MODELOS PARA EPG! ---

/**
 * Clase contenedora para la respuesta de la API de EPG.
 * La API devuelve un objeto JSON que contiene una lista llamada "epg_listings".
 */
data class EpgListingsResponse(
    @Json(name = "epg_listings") val epgListings: List<ApiEpgEvent>?
)

/**
 * Representa un evento EPG tal como viene de la API.
 * No se guarda directamente en la base de datos, es un modelo intermedio.
 * Contiene los campos codificados en Base64.
 */
data class ApiEpgEvent(
    @Json(name = "id") val id: String,
    @Json(name = "start_timestamp") val startTimestamp: Long,
    @Json(name = "stop_timestamp") val stopTimestamp: Long,
    @Json(name = "title") private val base64Title: String,
    @Json(name = "description") private val base64Description: String
) {
    /**
     * Convierte este objeto de API a un objeto EpgEvent para la base de datos.
     * Aquí es donde ocurre la magia de la decodificación.
     */
    fun toEpgEvent(streamId: Int, currentUserId: Int): EpgEvent {
        return EpgEvent(
            apiEventId = this.id,
            channelId = streamId,
            userId = currentUserId,
            title = decodeBase64(this.base64Title),
            description = decodeBase64(this.base64Description),
            startTimestamp = this.startTimestamp,
            stopTimestamp = this.stopTimestamp
        )
    }

    private fun decodeBase64(input: String): String {
        return try {
            Base64.decode(input, Base64.DEFAULT).toString(Charsets.UTF_8).trim()
        } catch (e: Exception) {
            // Si la decodificación falla, devuelve el texto original.
            input
        }
    }
}

/**
 * ¡REDEFINIDO! Representa un evento EPG en nuestra base de datos Room.
 * Almacena los datos ya decodificados y limpios.
 * La clave primaria ahora es una combinación del ID del evento, el ID del canal y el ID del usuario
 * para garantizar que cada entrada sea única.
 */
@Entity(tableName = "epg_events", primaryKeys = ["apiEventId", "channelId", "userId"])
data class EpgEvent(
    val apiEventId: String,
    val channelId: Int,
    val userId: Int,
    val title: String,
    val description: String,
    val startTimestamp: Long,
    val stopTimestamp: Long
)
