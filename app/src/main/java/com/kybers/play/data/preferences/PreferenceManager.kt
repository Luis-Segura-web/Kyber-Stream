package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences using SharedPreferences.
 * Handles sorting orders, favorite IDs, and playback positions for movies and episodes.
 */
class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_SORT_ORDER_PREFIX = "sort_order_"
        private const val KEY_ASPECT_RATIO_MODE = "aspect_ratio_mode"
        private const val KEY_FAVORITE_MOVIE_IDS = "favorite_movie_ids"
        private const val KEY_FAVORITE_SERIES_IDS = "favorite_series_ids"
        private const val KEY_PLAYBACK_POSITION_PREFIX = "playback_position_"
        private const val KEY_MOVIES_DOWNLOADED_COUNT = "movies_downloaded_count"
        private const val KEY_MOVIES_CACHED_COUNT = "movies_cached_count"
        // --- ¡NUEVA CLAVE PARA EL ESTADO DE REPRODUCCIÓN DE EPISODIOS! ---
        private const val KEY_EPISODE_PLAYBACK_STATE_PREFIX = "ep_playback_state_"
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

    fun saveFavoriteSeriesIds(ids: Set<String>) {
        with(sharedPreferences.edit()) {
            putStringSet(KEY_FAVORITE_SERIES_IDS, ids)
            apply()
        }
    }

    fun getFavoriteSeriesIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_FAVORITE_SERIES_IDS, emptySet()) ?: emptySet()
    }

    // Métodos para películas (sin cambios)
    fun savePlaybackPosition(contentId: String, position: Long) {
        with(sharedPreferences.edit()) {
            putLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, position)
            apply()
        }
    }

    fun getPlaybackPosition(contentId: String): Long {
        return sharedPreferences.getLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, 0L)
    }

    fun getAllPlaybackPositions(): Map<String, Long> {
        return sharedPreferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_PLAYBACK_POSITION_PREFIX) && value is Long) {
                key.removePrefix(KEY_PLAYBACK_POSITION_PREFIX) to value
            } else {
                null
            }
        }.toMap()
    }

    // --- ¡NUEVAS FUNCIONES PARA EL ESTADO DE REPRODUCCIÓN DE EPISODIOS! ---

    /**
     * Guarda la posición y la duración total de un episodio.
     * @param episodeId El ID del episodio.
     * @param position La posición actual en milisegundos.
     * @param duration La duración total real del video en milisegundos.
     */
    fun saveEpisodePlaybackState(episodeId: String, position: Long, duration: Long) {
        // Solo guardamos si la duración es válida (mayor que cero).
        if (duration <= 0) return
        with(sharedPreferences.edit()) {
            putString(KEY_EPISODE_PLAYBACK_STATE_PREFIX + episodeId, "$position/$duration")
            apply()
        }
    }

    /**
     * Obtiene la posición y duración guardadas para un episodio.
     * @return Un Par (Pair) que contiene la posición y la duración. (0L, 0L) si no hay datos.
     */
    fun getEpisodePlaybackState(episodeId: String): Pair<Long, Long> {
        val state = sharedPreferences.getString(KEY_EPISODE_PLAYBACK_STATE_PREFIX + episodeId, "0/0") ?: "0/0"
        return try {
            val parts = state.split('/')
            if (parts.size == 2) {
                parts[0].toLong() to parts[1].toLong()
            } else {
                0L to 0L
            }
        } catch (e: Exception) {
            0L to 0L
        }
    }

    /**
     * Obtiene el estado de reproducción de todos los episodios guardados.
     * @return Un Mapa donde la clave es el ID del episodio y el valor es un Par (posición, duración).
     */
    fun getAllEpisodePlaybackStates(): Map<String, Pair<Long, Long>> {
        return sharedPreferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_EPISODE_PLAYBACK_STATE_PREFIX) && value is String) {
                try {
                    val episodeId = key.removePrefix(KEY_EPISODE_PLAYBACK_STATE_PREFIX)
                    val parts = value.split('/')
                    if (parts.size == 2) {
                        episodeId to (parts[0].toLong() to parts[1].toLong())
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }.toMap()
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
}
