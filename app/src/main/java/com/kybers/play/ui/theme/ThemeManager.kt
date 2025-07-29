package com.kybers.play.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.kybers.play.data.preferences.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Theme options available to the user
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Manages application theme state and provides theme information
 * Enhanced for instant theme switching
 */
class ThemeManager(private val preferenceManager: PreferenceManager) {
    
    private val _currentTheme = MutableStateFlow(getCurrentThemeFromPreferences())
    val currentTheme: StateFlow<ThemeMode> = _currentTheme.asStateFlow()
    
    private fun getCurrentThemeFromPreferences(): ThemeMode {
        return when (preferenceManager.getAppTheme()) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            "SYSTEM" -> ThemeMode.SYSTEM
            else -> ThemeMode.SYSTEM // Default fallback
        }
    }
    
    /**
     * Updates the theme preference and notifies observers
     */
    fun setTheme(themeMode: ThemeMode) {
        val themeString = when (themeMode) {
            ThemeMode.LIGHT -> "LIGHT"
            ThemeMode.DARK -> "DARK"
            ThemeMode.SYSTEM -> "SYSTEM"
        }
        preferenceManager.saveAppTheme(themeString)
        _currentTheme.value = themeMode
    }
    
    /**
     * Updates theme from string and applies immediately
     * Used by settings to ensure instant theme switching
     */
    fun updateThemeFromString(themeString: String) {
        val themeMode = when (themeString) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            "SYSTEM" -> ThemeMode.SYSTEM
            else -> ThemeMode.SYSTEM
        }
        _currentTheme.value = themeMode
    }
    
    /**
     * Refreshes theme from preferences
     * Useful when preferences might have changed externally
     */
    fun refreshThemeFromPreferences() {
        _currentTheme.value = getCurrentThemeFromPreferences()
    }
    
    /**
     * Determines if dark theme should be used based on current theme mode
     */
    @Composable
    fun shouldUseDarkTheme(): Boolean {
        val currentThemeMode = currentTheme.collectAsState().value
        return when (currentThemeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    }
    
    /**
     * Gets the display name for a theme mode
     */
    fun getThemeDisplayName(themeMode: ThemeMode): String {
        return when (themeMode) {
            ThemeMode.LIGHT -> "Claro"
            ThemeMode.DARK -> "Oscuro"
            ThemeMode.SYSTEM -> "Sistema"
        }
    }
    
    /**
     * Gets all available theme options
     */
    fun getAvailableThemes(): List<ThemeMode> {
        return listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    }
}

/**
 * Composable function to create and remember a ThemeManager instance
 */
@Composable
fun rememberThemeManager(context: Context): ThemeManager {
    return remember {
        val preferenceManager = PreferenceManager(context)
        ThemeManager(preferenceManager)
    }
}