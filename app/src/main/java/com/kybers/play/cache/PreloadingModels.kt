package com.kybers.play.cache

/**
 * Data models for the preloading system
 * These models are used to represent content that needs to be preloaded
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