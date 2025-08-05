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
 * Theme colors available to the user
 */
enum class ThemeColor {
    BLUE,
    PURPLE,
    PINK
}

/**
 * Theme modes available to the user
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Legacy theme modes for backward compatibility
 */
enum class LegacyThemeMode {
    LIGHT,
    DARK,
    BLUE,
    PURPLE,
    PINK,
    SYSTEM
}

/**
 * Configuration for the new theme system with independent color and mode selection
 */
data class ThemeConfig(
    val color: ThemeColor,
    val mode: ThemeMode
) {
    companion object {
        val DEFAULT = ThemeConfig(
            color = ThemeColor.BLUE,
            mode = ThemeMode.SYSTEM
        )
    }
}

/**
 * Manages application theme state and provides theme information
 * Enhanced for independent color and mode selection with legacy support
 */
class ThemeManager(private val preferenceManager: PreferenceManager) {
    
    // Inicializar con valores por defecto primero
    private val _currentThemeConfig = MutableStateFlow(ThemeConfig.DEFAULT)
    val currentThemeConfig: StateFlow<ThemeConfig> = _currentThemeConfig.asStateFlow()

    // Legacy support para compatibilidad hacia atrás
    private val _currentTheme = MutableStateFlow(LegacyThemeMode.SYSTEM)
    val currentTheme: StateFlow<LegacyThemeMode> = _currentTheme.asStateFlow()

    // Bloque init para cargar configuración real
    init {
        val actualConfig = getCurrentThemeConfigFromPreferences()
        _currentThemeConfig.value = actualConfig
        _currentTheme.value = legacyThemeModeFromConfig(actualConfig)
    }
    
    private fun getCurrentThemeConfigFromPreferences(): ThemeConfig {
        // Check if new system preferences exist
        val themeColor = preferenceManager.getThemeColor()
        val themeMode = preferenceManager.getThemeMode()
        
        // If new preferences don't exist, migrate from legacy
        return if (themeColor == "BLUE" && themeMode == "SYSTEM" && !hasNewThemePreferences()) {
            migrateFromLegacyTheme()
        } else {
            ThemeConfig(
                color = parseThemeColor(themeColor),
                mode = parseThemeMode(themeMode)
            )
        }
    }
    
    private fun hasNewThemePreferences(): Boolean {
        // Check if the user has explicitly set new theme preferences
        val legacyTheme = preferenceManager.getAppTheme()
        val newColor = preferenceManager.getThemeColor()
        val newMode = preferenceManager.getThemeMode()
        
        // If legacy is not default and new are default, then we haven't migrated yet
        return !(legacyTheme != "SYSTEM" && newColor == "BLUE" && newMode == "SYSTEM")
    }
    
    private fun migrateFromLegacyTheme(): ThemeConfig {
        val legacyTheme = preferenceManager.getAppTheme()
        val config = when (legacyTheme) {
            "LIGHT" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.LIGHT)
            "DARK" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
            "BLUE" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
            "PURPLE" -> ThemeConfig(ThemeColor.PURPLE, ThemeMode.DARK)
            "PINK" -> ThemeConfig(ThemeColor.PINK, ThemeMode.DARK)
            "SYSTEM" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.SYSTEM)
            else -> ThemeConfig.DEFAULT
        }
        
        // Guardar directamente sin llamar setThemeConfig durante inicialización
        preferenceManager.saveThemeColor(config.color.name)
        preferenceManager.saveThemeMode(config.mode.name)
        return config
    }
    
    private fun parseThemeColor(colorString: String): ThemeColor {
        return when (colorString) {
            "BLUE" -> ThemeColor.BLUE
            "PURPLE" -> ThemeColor.PURPLE
            "PINK" -> ThemeColor.PINK
            else -> ThemeColor.BLUE
        }
    }
    
    private fun parseThemeMode(modeString: String): ThemeMode {
        return when (modeString) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            "SYSTEM" -> ThemeMode.SYSTEM
            else -> ThemeMode.SYSTEM
        }
    }
    
    private fun legacyThemeModeFromConfig(config: ThemeConfig): LegacyThemeMode {
        return when {
            config.mode == ThemeMode.LIGHT -> LegacyThemeMode.LIGHT
            config.mode == ThemeMode.DARK && config.color == ThemeColor.BLUE -> LegacyThemeMode.BLUE
            config.mode == ThemeMode.DARK && config.color == ThemeColor.PURPLE -> LegacyThemeMode.PURPLE
            config.mode == ThemeMode.DARK && config.color == ThemeColor.PINK -> LegacyThemeMode.PINK
            config.mode == ThemeMode.DARK -> LegacyThemeMode.DARK
            config.mode == ThemeMode.SYSTEM -> LegacyThemeMode.SYSTEM
            else -> LegacyThemeMode.SYSTEM
        }
    }
    
    /**
     * Sets the theme configuration (color and mode independently)
     */
    fun setThemeConfig(config: ThemeConfig) {
        preferenceManager.saveThemeColor(config.color.name)
        preferenceManager.saveThemeMode(config.mode.name)
        _currentThemeConfig.value = config
        _currentTheme.value = legacyThemeModeFromConfig(config)
    }
    
    /**
     * Sets only the theme color, keeping the current mode
     */
    fun setThemeColor(color: ThemeColor) {
        val newConfig = _currentThemeConfig.value.copy(color = color)
        setThemeConfig(newConfig)
    }
    
    /**
     * Sets only the theme mode, keeping the current color
     */
    fun setThemeMode(mode: ThemeMode) {
        val newConfig = _currentThemeConfig.value.copy(mode = mode)
        setThemeConfig(newConfig)
    }
    
    // Legacy methods for backward compatibility
    private fun getCurrentThemeFromPreferences(): LegacyThemeMode {
        return legacyThemeModeFromConfig(_currentThemeConfig.value)
    }
    
    /**
     * Legacy method: Updates the theme preference and notifies observers
     */
    fun setTheme(themeMode: LegacyThemeMode) {
        val config = when (themeMode) {
            LegacyThemeMode.LIGHT -> ThemeConfig(ThemeColor.BLUE, ThemeMode.LIGHT)
            LegacyThemeMode.DARK -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
            LegacyThemeMode.BLUE -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
            LegacyThemeMode.PURPLE -> ThemeConfig(ThemeColor.PURPLE, ThemeMode.DARK)
            LegacyThemeMode.PINK -> ThemeConfig(ThemeColor.PINK, ThemeMode.DARK)
            LegacyThemeMode.SYSTEM -> ThemeConfig(ThemeColor.BLUE, ThemeMode.SYSTEM)
        }
        setThemeConfig(config)
        
        // Also update legacy preference for full compatibility
        val themeString = when (themeMode) {
            LegacyThemeMode.LIGHT -> "LIGHT"
            LegacyThemeMode.DARK -> "DARK"
            LegacyThemeMode.BLUE -> "BLUE"
            LegacyThemeMode.PURPLE -> "PURPLE"
            LegacyThemeMode.PINK -> "PINK"
            LegacyThemeMode.SYSTEM -> "SYSTEM"
        }
        preferenceManager.saveAppTheme(themeString)
    }
    
    /**
     * Legacy method: Updates theme from string and applies immediately
     * Used by settings to ensure instant theme switching
     */
    fun updateThemeFromString(themeString: String) {
        val themeMode = when (themeString) {
            "LIGHT" -> LegacyThemeMode.LIGHT
            "DARK" -> LegacyThemeMode.DARK
            "BLUE" -> LegacyThemeMode.BLUE
            "PURPLE" -> LegacyThemeMode.PURPLE
            "PINK" -> LegacyThemeMode.PINK
            "SYSTEM" -> LegacyThemeMode.SYSTEM
            else -> LegacyThemeMode.SYSTEM
        }
        setTheme(themeMode)
    }
    
    /**
     * Legacy method: Refreshes theme from preferences
     * Useful when preferences might have changed externally
     */
    fun refreshThemeFromPreferences() {
        val actualConfig = getCurrentThemeConfigFromPreferences()
        _currentThemeConfig.value = actualConfig
        _currentTheme.value = legacyThemeModeFromConfig(actualConfig)
    }
    
    /**
     * Determines if dark theme should be used based on current theme mode
     */
    @Composable
    fun shouldUseDarkTheme(): Boolean {
        val currentConfig = currentThemeConfig.collectAsState().value
        return when (currentConfig.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    }
    
    /**
     * Gets the display name for a legacy theme mode
     */
    fun getThemeDisplayName(themeMode: LegacyThemeMode): String {
        return when (themeMode) {
            LegacyThemeMode.LIGHT -> "Claro"
            LegacyThemeMode.DARK -> "Oscuro"
            LegacyThemeMode.BLUE -> "Azul"
            LegacyThemeMode.PURPLE -> "Morado"
            LegacyThemeMode.PINK -> "Rosa"
            LegacyThemeMode.SYSTEM -> "Sistema"
        }
    }
    
    /**
     * Gets the display name for a theme color
     */
    fun getColorDisplayName(color: ThemeColor): String {
        return when (color) {
            ThemeColor.BLUE -> "Azul"
            ThemeColor.PURPLE -> "Morado"
            ThemeColor.PINK -> "Rosa"
        }
    }
    
    /**
     * Gets the display name for a theme mode
     */
    fun getModeDisplayName(mode: ThemeMode): String {
        return when (mode) {
            ThemeMode.LIGHT -> "Claro"
            ThemeMode.DARK -> "Oscuro"
            ThemeMode.SYSTEM -> "Sistema"
        }
    }
    
    /**
     * Gets all available legacy theme options
     */
    fun getAvailableThemes(): List<LegacyThemeMode> {
        return listOf(LegacyThemeMode.DARK, LegacyThemeMode.LIGHT, LegacyThemeMode.BLUE, LegacyThemeMode.PURPLE, LegacyThemeMode.PINK, LegacyThemeMode.SYSTEM)
    }
    
    /**
     * Gets all available theme colors
     */
    fun getAvailableColors(): List<ThemeColor> {
        return listOf(ThemeColor.BLUE, ThemeColor.PURPLE, ThemeColor.PINK)
    }
    
    /**
     * Gets all available theme modes
     */
    fun getAvailableModes(): List<ThemeMode> {
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