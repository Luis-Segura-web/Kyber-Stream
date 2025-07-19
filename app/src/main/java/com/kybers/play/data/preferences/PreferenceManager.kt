package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences using SharedPreferences.
 * Handles sorting orders, aspect ratio, and favorite IDs.
 *
 * @param context The application context.
 */
class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_SORT_ORDER_PREFIX = "sort_order_"
        private const val KEY_ASPECT_RATIO_MODE = "aspect_ratio_mode"
        // ¡NUEVO! Clave para guardar los IDs de las películas favoritas.
        private const val KEY_FAVORITE_MOVIE_IDS = "favorite_movie_ids"
    }

    /**
     * Saves the selected sorting order for a specific key.
     */
    fun saveSortOrder(key: String, sortOrder: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_SORT_ORDER_PREFIX + key, sortOrder)
            apply()
        }
    }

    /**
     * Retrieves the saved sorting order for a specific key.
     */
    fun getSortOrder(key: String): String {
        return sharedPreferences.getString(KEY_SORT_ORDER_PREFIX + key, "DEFAULT") ?: "DEFAULT"
    }

    /**
     * Saves the selected aspect ratio mode.
     */
    fun saveAspectRatioMode(mode: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_ASPECT_RATIO_MODE, mode)
            apply()
        }
    }

    /**
     * Retrieves the saved aspect ratio mode.
     */
    fun getAspectRatioMode(): String {
        return sharedPreferences.getString(KEY_ASPECT_RATIO_MODE, "FIT_SCREEN") ?: "FIT_SCREEN"
    }

    /**
     * ¡NUEVA FUNCIÓN!
     * Guarda el conjunto de IDs de películas favoritas.
     * @param ids Un Set de Strings con los IDs de las películas.
     */
    fun saveFavoriteMovieIds(ids: Set<String>) {
        with(sharedPreferences.edit()) {
            putStringSet(KEY_FAVORITE_MOVIE_IDS, ids)
            apply()
        }
    }

    /**
     * ¡NUEVA FUNCIÓN!
     * Recupera el conjunto de IDs de películas favoritas.
     * @return Un Set de Strings, o un conjunto vacío si no hay ninguno guardado.
     */
    fun getFavoriteMovieIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_FAVORITE_MOVIE_IDS, emptySet()) ?: emptySet()
    }
}
