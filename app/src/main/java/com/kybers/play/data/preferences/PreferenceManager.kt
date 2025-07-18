package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences using SharedPreferences.
 * Currently handles the sorting order for channels and categories, and aspect ratio mode.
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
    }

    /**
     * Saves the selected sorting order for a specific key (e.g., "category" or "channel").
     * @param key The key to identify the preference (e.g., "category", "channel").
     * @param sortOrder The SortOrder enum value to save as a String.
     */
    fun saveSortOrder(key: String, sortOrder: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_SORT_ORDER_PREFIX + key, sortOrder)
            apply()
        }
    }

    /**
     * Retrieves the saved sorting order for a specific key.
     * @param key The key to identify the preference.
     * @return The saved SortOrder enum value as a String, or "DEFAULT" if not found.
     */
    fun getSortOrder(key: String): String {
        return sharedPreferences.getString(KEY_SORT_ORDER_PREFIX + key, "DEFAULT") ?: "DEFAULT"
    }

    /**
     * Saves the selected aspect ratio mode.
     * @param mode The AspectRatioMode enum value to save as a String.
     */
    fun saveAspectRatioMode(mode: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_ASPECT_RATIO_MODE, mode)
            apply()
        }
    }

    /**
     * Retrieves the saved aspect ratio mode.
     * @return The saved AspectRatioMode enum value as a String, or "FIT_SCREEN" if not found.
     */
    fun getAspectRatioMode(): String {
        return sharedPreferences.getString(KEY_ASPECT_RATIO_MODE, "FIT_SCREEN") ?: "FIT_SCREEN"
    }
}