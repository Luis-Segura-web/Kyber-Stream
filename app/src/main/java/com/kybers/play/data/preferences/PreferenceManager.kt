package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences using SharedPreferences.
 * Handles sorting orders, aspect ratio, favorite IDs, and playback positions.
 */
class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_SORT_ORDER_PREFIX = "sort_order_"
        private const val KEY_ASPECT_RATIO_MODE = "aspect_ratio_mode"
        private const val KEY_FAVORITE_MOVIE_IDS = "favorite_movie_ids"
        private const val KEY_PLAYBACK_POSITION_PREFIX = "playback_position_"
        private const val KEY_MOVIES_DOWNLOADED_COUNT = "movies_downloaded_count"
        private const val KEY_MOVIES_CACHED_COUNT = "movies_cached_count"
    }

    fun saveSortOrder(key: String, sortOrder: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_SORT_ORDER_PREFIX + key, sortOrder)
            apply()
        }
    }

    fun getSortOrder(key: String): String {
        return sharedPreferences.getString(KEY_SORT_ORDER_PREFIX + key, "DEFAULT") ?: "DEFAULT"
    }

    fun saveAspectRatioMode(mode: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_ASPECT_RATIO_MODE, mode)
            apply()
        }
    }

    fun getAspectRatioMode(): String {
        return sharedPreferences.getString(KEY_ASPECT_RATIO_MODE, "FIT_SCREEN") ?: "FIT_SCREEN"
    }

    fun saveFavoriteMovieIds(ids: Set<String>) {
        with(sharedPreferences.edit()) {
            putStringSet(KEY_FAVORITE_MOVIE_IDS, ids)
            apply()
        }
    }

    fun getFavoriteMovieIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_FAVORITE_MOVIE_IDS, emptySet()) ?: emptySet()
    }

    fun savePlaybackPosition(contentId: String, position: Long) {
        with(sharedPreferences.edit()) {
            putLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, position)
            apply()
        }
    }

    fun getPlaybackPosition(contentId: String): Long {
        return sharedPreferences.getLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, 0L)
    }

    fun saveMovieSyncStats(downloaded: Int, totalInCache: Int) {
        with(sharedPreferences.edit()) {
            putInt(KEY_MOVIES_DOWNLOADED_COUNT, downloaded)
            putInt(KEY_MOVIES_CACHED_COUNT, totalInCache)
            apply()
        }
    }

    fun getMovieSyncStats(): Pair<Int, Int> {
        val downloaded = sharedPreferences.getInt(KEY_MOVIES_DOWNLOADED_COUNT, 0)
        val cached = sharedPreferences.getInt(KEY_MOVIES_CACHED_COUNT, 0)
        return Pair(downloaded, cached)
    }

    // --- ¡NUEVA FUNCIÓN! ---
    /**
     * Devuelve un mapa con todos los IDs de películas y sus posiciones de reproducción guardadas.
     * @return Un mapa donde la clave es el ID de la película y el valor es la posición en milisegundos.
     */
    fun getAllPlaybackPositions(): Map<String, Long> {
        return sharedPreferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_PLAYBACK_POSITION_PREFIX) && value is Long) {
                key.removePrefix(KEY_PLAYBACK_POSITION_PREFIX) to value
            } else {
                null
            }
        }.toMap()
    }
}