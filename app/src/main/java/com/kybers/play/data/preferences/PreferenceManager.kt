package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * --- ¡GESTOR DE PREFERENCIAS ACTUALIZADO! ---
 * Manages user preferences using SharedPreferences.
 * Ahora incluye métodos para gestionar el límite de vistos recientemente y el control parental.
 */
class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_preferences"

        // Claves existentes
        private const val KEY_SORT_ORDER_PREFIX = "sort_order_"
        private const val KEY_ASPECT_RATIO_MODE = "aspect_ratio_mode"
        private const val KEY_FAVORITE_MOVIE_IDS = "favorite_movie_ids"
        private const val KEY_FAVORITE_SERIES_IDS = "favorite_series_ids"
        private const val KEY_PLAYBACK_POSITION_PREFIX = "playback_position_"
        private const val KEY_MOVIES_DOWNLOADED_COUNT = "movies_downloaded_count"
        private const val KEY_MOVIES_CACHED_COUNT = "movies_cached_count"
        private const val KEY_EPISODE_PLAYBACK_STATE_PREFIX = "ep_playback_state_"
        private const val KEY_SYNC_FREQUENCY = "sync_frequency"
        private const val KEY_STREAM_FORMAT = "stream_format"
        private const val KEY_HW_ACCELERATION = "hw_acceleration"
        private const val KEY_NETWORK_BUFFER = "network_buffer"
        private const val KEY_APP_THEME = "app_theme"

        // --- ¡NUEVAS CLAVES AÑADIDAS! ---
        private const val KEY_RECENTLY_WATCHED_LIMIT = "recently_watched_limit"
        private const val KEY_PARENTAL_CONTROL_ENABLED = "parental_control_enabled"
        private const val KEY_PARENTAL_CONTROL_PIN = "parental_control_pin"
        private const val KEY_BLOCKED_CATEGORIES = "blocked_categories"

        // --- NUEVA CLAVE PARA CATEGORÍAS OCULTAS EN TV EN VIVO ---
        private const val KEY_HIDDEN_LIVE_CATEGORIES = "hidden_live_categories"
    }

    // --- Métodos existentes (sin cambios) ---

    fun saveSortOrder(key: String, sortOrder: String) = sharedPreferences.edit().putString(KEY_SORT_ORDER_PREFIX + key, sortOrder).apply()
    fun getSortOrder(key: String): String = sharedPreferences.getString(KEY_SORT_ORDER_PREFIX + key, "DEFAULT") ?: "DEFAULT"

    fun saveAspectRatioMode(mode: String) = sharedPreferences.edit().putString(KEY_ASPECT_RATIO_MODE, mode).apply()
    fun getAspectRatioMode(): String = sharedPreferences.getString(KEY_ASPECT_RATIO_MODE, "FIT_SCREEN") ?: "FIT_SCREEN"

    fun saveFavoriteMovieIds(ids: Set<String>) = sharedPreferences.edit().putStringSet(KEY_FAVORITE_MOVIE_IDS, ids).apply()
    fun getFavoriteMovieIds(): Set<String> = sharedPreferences.getStringSet(KEY_FAVORITE_MOVIE_IDS, emptySet()) ?: emptySet()

    fun saveFavoriteSeriesIds(ids: Set<String>) = sharedPreferences.edit().putStringSet(KEY_FAVORITE_SERIES_IDS, ids).apply()
    fun getFavoriteSeriesIds(): Set<String> = sharedPreferences.getStringSet(KEY_FAVORITE_SERIES_IDS, emptySet()) ?: emptySet()

    fun savePlaybackPosition(contentId: String, position: Long) = sharedPreferences.edit().putLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, position).apply()
    fun getPlaybackPosition(contentId: String): Long = sharedPreferences.getLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, 0L)

    fun getAllPlaybackPositions(): Map<String, Long> {
        return sharedPreferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_PLAYBACK_POSITION_PREFIX) && value is Long) {
                key.removePrefix(KEY_PLAYBACK_POSITION_PREFIX) to value
            } else null
        }.toMap()
    }

    fun saveEpisodePlaybackState(episodeId: String, position: Long, duration: Long) {
        if (duration <= 0) return
        sharedPreferences.edit().putString(KEY_EPISODE_PLAYBACK_STATE_PREFIX + episodeId, "$position/$duration").apply()
    }

    fun getEpisodePlaybackState(episodeId: String): Pair<Long, Long> {
        val state = sharedPreferences.getString(KEY_EPISODE_PLAYBACK_STATE_PREFIX + episodeId, "0/0") ?: "0/0"
        return try {
            val parts = state.split('/')
            if (parts.size == 2) parts[0].toLong() to parts[1].toLong() else 0L to 0L
        } catch (e: Exception) { 0L to 0L }
    }

    fun getAllEpisodePlaybackStates(): Map<String, Pair<Long, Long>> {
        return sharedPreferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_EPISODE_PLAYBACK_STATE_PREFIX) && value is String) {
                try {
                    val episodeId = key.removePrefix(KEY_EPISODE_PLAYBACK_STATE_PREFIX)
                    val parts = value.split('/')
                    if (parts.size == 2) episodeId to (parts[0].toLong() to parts[1].toLong()) else null
                } catch (e: Exception) { null }
            } else null
        }.toMap()
    }

    fun saveMovieSyncStats(downloaded: Int, totalInCache: Int) = sharedPreferences.edit().putInt(KEY_MOVIES_DOWNLOADED_COUNT, downloaded).putInt(KEY_MOVIES_CACHED_COUNT, totalInCache).apply()
    fun getMovieSyncStats(): Pair<Int, Int> = sharedPreferences.getInt(KEY_MOVIES_DOWNLOADED_COUNT, 0) to sharedPreferences.getInt(KEY_MOVIES_CACHED_COUNT, 0)

    fun saveSyncFrequency(frequencyHours: Int) = sharedPreferences.edit().putInt(KEY_SYNC_FREQUENCY, frequencyHours).apply()
    fun getSyncFrequency(): Int = sharedPreferences.getInt(KEY_SYNC_FREQUENCY, 12)

    fun saveStreamFormat(format: String) = sharedPreferences.edit().putString(KEY_STREAM_FORMAT, format).apply()
    fun getStreamFormat(): String = sharedPreferences.getString(KEY_STREAM_FORMAT, "AUTOMATIC") ?: "AUTOMATIC"

    fun saveHwAcceleration(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_HW_ACCELERATION, enabled).apply()
    fun getHwAcceleration(): Boolean = sharedPreferences.getBoolean(KEY_HW_ACCELERATION, true)

    fun saveNetworkBuffer(bufferSize: String) = sharedPreferences.edit().putString(KEY_NETWORK_BUFFER, bufferSize).apply()
    fun getNetworkBuffer(): String = sharedPreferences.getString(KEY_NETWORK_BUFFER, "MEDIUM") ?: "MEDIUM"

    fun saveAppTheme(theme: String) = sharedPreferences.edit().putString(KEY_APP_THEME, theme).apply()
    fun getAppTheme(): String = sharedPreferences.getString(KEY_APP_THEME, "SYSTEM") ?: "SYSTEM"

    // --- ¡NUEVOS MÉTODOS AÑADIDOS! ---

    fun saveRecentlyWatchedLimit(limit: Int) = sharedPreferences.edit().putInt(KEY_RECENTLY_WATCHED_LIMIT, limit).apply()
    fun getRecentlyWatchedLimit(): Int = sharedPreferences.getInt(KEY_RECENTLY_WATCHED_LIMIT, 10) // Default 10 items

    /**
     * Elimina todo el historial de reproducción de películas y series.
     */
    fun clearAllPlaybackHistory() {
        val editor = sharedPreferences.edit()
        sharedPreferences.all.keys.forEach { key ->
            if (key.startsWith(KEY_PLAYBACK_POSITION_PREFIX) || key.startsWith(KEY_EPISODE_PLAYBACK_STATE_PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun saveParentalControlEnabled(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_PARENTAL_CONTROL_ENABLED, enabled).apply()
    fun isParentalControlEnabled(): Boolean = sharedPreferences.getBoolean(KEY_PARENTAL_CONTROL_ENABLED, false)

    fun saveParentalPin(pin: String) = sharedPreferences.edit().putString(KEY_PARENTAL_CONTROL_PIN, pin).apply()
    fun getParentalPin(): String? = sharedPreferences.getString(KEY_PARENTAL_CONTROL_PIN, null)

    fun saveBlockedCategories(categoryIds: Set<String>) = sharedPreferences.edit().putStringSet(KEY_BLOCKED_CATEGORIES, categoryIds).apply()
    fun getBlockedCategories(): Set<String> = sharedPreferences.getStringSet(KEY_BLOCKED_CATEGORIES, emptySet()) ?: emptySet()

    // --- Preferencias de categorías ocultas en TV en Vivo ---
    fun saveHiddenLiveCategories(ids: Set<String>) = sharedPreferences.edit().putStringSet(KEY_HIDDEN_LIVE_CATEGORIES, ids).apply()
    fun getHiddenLiveCategories(): Set<String> = sharedPreferences.getStringSet(KEY_HIDDEN_LIVE_CATEGORIES, emptySet()) ?: emptySet()

    /**
     * Generates VLC options based on current user preferences.
     * This replaces hardcoded options with dynamic settings.
     */
    fun getVLCOptions(): ArrayList<String> {
        val options = arrayListOf<String>()
        
        // Apply network buffer setting
        val networkBuffer = getNetworkBuffer()
        val bufferValue = when (networkBuffer) {
            "LOW" -> "1000"
            "MEDIUM" -> "3000"
            "HIGH" -> "5000"
            "ULTRA" -> "8000"
            else -> "3000"
        }
        options.add("--network-caching=$bufferValue")
        options.add("--file-caching=$bufferValue")
        
        // Apply hardware acceleration setting
        if (getHwAcceleration()) {
            options.add("--avcodec-hw=any")
        } else {
            options.add("--avcodec-hw=none")
        }
        
        // Apply stream format specific configurations
        val streamFormat = getStreamFormat()
        when (streamFormat) {
            "HLS" -> {
                options.add("--http-reconnect")
            }
            "TS" -> {
                options.add("--ts-seek-percent")
            }
        }
        
        // General performance configurations
        options.add("--audio-time-stretch")
        
        // Log the options being applied for debugging
        android.util.Log.d("PlayerSettings", "Generated VLC options: ${options.joinToString(", ")}")
        android.util.Log.d("PlayerSettings", "Settings - Buffer: $networkBuffer, HW Accel: ${getHwAcceleration()}, Format: $streamFormat")
        
        return options
    }
}
