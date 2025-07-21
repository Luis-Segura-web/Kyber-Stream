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
        // ¡NUEVO! Prefijo para guardar la posición de reproducción.
        private const val KEY_PLAYBACK_POSITION_PREFIX = "playback_position_"
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

    /**
     * ¡NUEVA FUNCIÓN!
     * Guarda la última posición de reproducción conocida para un contenido.
     * @param contentId El ID único del contenido (ej. streamId de la película).
     * @param position La posición en milisegundos.
     */
    fun savePlaybackPosition(contentId: String, position: Long) {
        with(sharedPreferences.edit()) {
            putLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, position)
            apply()
        }
    }

    /**
     * ¡NUEVA FUNCIÓN!
     * Recupera la última posición de reproducción guardada para un contenido.
     * @param contentId El ID único del contenido.
     * @return La posición en milisegundos, o 0L si no hay ninguna guardada.
     */
    fun getPlaybackPosition(contentId: String): Long {
        return sharedPreferences.getLong(KEY_PLAYBACK_POSITION_PREFIX + contentId, 0L)
    }
}
