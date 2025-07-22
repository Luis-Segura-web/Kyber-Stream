package com.kybers.play.data.remote.model

import androidx.room.Entity
import androidx.room.Ignore
import com.squareup.moshi.Json
import android.util.Base64

data class XtreamResponse(
    @Json(name = "user_info") val userInfo: UserInfo?,
    @Json(name = "server_info") val serverInfo: ServerInfo?
)

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

data class Category(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int
)

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
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int,
    @Json(name = "is_adult") val isAdult: String? = "0"
) {
    var userId: Int = 0
    @Ignore var currentEpgEvent: EpgEvent? = null
    @Ignore var nextEpgEvent: EpgEvent? = null
}

// --- ¡ENTIDAD MODIFICADA! ---
// La clave primaria ahora es solo el ID del stream y del usuario.
// Esto nos permite tener un categoryId nulo sin problemas.
@Entity(tableName = "movies", primaryKeys = ["streamId", "userId"])
data class Movie(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "tmdb_id") val tmdbId: String?,
    @Json(name = "num") val num: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_type") val streamType: String,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "rating_5based") val rating5Based: Float,
    @Json(name = "added") val added: String,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "container_extension") val containerExtension: String
) {
    var userId: Int = 0
}

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

data class EpgListingsResponse(
    @Json(name = "epg_listings") val epgListings: List<ApiEpgEvent>?
)

data class ApiEpgEvent(
    @Json(name = "id") val id: String,
    @Json(name = "start_timestamp") val startTimestamp: Long,
    @Json(name = "stop_timestamp") val stopTimestamp: Long,
    @Json(name = "title") private val base64Title: String,
    @Json(name = "description") private val base64Description: String
) {
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
            input
        }
    }
}

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