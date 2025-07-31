package com.kybers.play.cache

/**
 * Basic data models used by the cache system
 */

data class Content(
    val id: Int,
    val streamUrl: String,
    val title: String,
    val type: ContentType
)

enum class ContentType {
    MOVIE, SERIES, CHANNEL
}

data class ViewingRecord(
    val contentId: Int,
    val userId: Int,
    val timestamp: Long,
    val duration: Long
)

data class Episode(
    val id: Int,
    val seriesId: Int,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val streamUrl: String,
    val title: String
)