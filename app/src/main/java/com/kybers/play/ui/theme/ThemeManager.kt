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
 * Colores de tema disponibles para el usuario
 */
enum class ThemeColor {
    BLUE,    // Azul profesional
    PURPLE,  // Púrpura elegante
    PINK     // Rosa moderno
}

/**
 * Modos de visualización disponibles para el usuario
 */
enum class ThemeMode {
    LIGHT,   // Claro
    DARK,    // Oscuro
    SYSTEM   // Automático (sigue configuración del sistema)
}

/**
 * Configuración completa del tema con color y modo independientes
 */
data class ThemeConfig(
    val color: ThemeColor,
    val mode: ThemeMode
) {
    companion object {
        val DEFAULT = ThemeConfig(ThemeColor.BLUE, ThemeMode.SYSTEM)
    }
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
 * Manages application theme state and provides theme information
 * Enhanced for instant theme switching with independent color and mode selection
 */
class ThemeManager(private val preferenceManager: PreferenceManager) {
    
    // Nuevo sistema con configuración independiente
    private val _currentThemeConfig = MutableStateFlow(getCurrentThemeConfigFromPreferences())
    val currentThemeConfig: StateFlow<ThemeConfig> = _currentThemeConfig.asStateFlow()
    
    // Legacy support para compatibilidad hacia atrás
    private val _currentTheme = MutableStateFlow(legacyThemeModeFromConfig(_currentThemeConfig.value))
    val currentTheme: StateFlow<LegacyThemeMode> = _currentTheme.asStateFlow()
    
    private fun getCurrentThemeConfigFromPreferences(): ThemeConfig {
        // Intentar leer el nuevo formato primero
        val savedColor = preferenceManager.getThemeColor()
        val savedMode = preferenceManager.getThemeMode()
        
        if (savedColor != null && savedMode != null) {
            // Formato nuevo disponible
            return ThemeConfig(
                color = parseThemeColor(savedColor) ?: ThemeColor.BLUE,
                mode = parseThemeMode(savedMode) ?: ThemeMode.SYSTEM
            )
        }
        
        // Migrar desde formato anterior
        return migrateFromLegacyTheme()
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
        
        // Guardar en nuevo formato
        setThemeConfig(config)
        return config
    }
    
    private fun parseThemeColor(colorString: String): ThemeColor? {
        return try {
            ThemeColor.valueOf(colorString)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    
    private fun parseThemeMode(modeString: String): ThemeMode? {
        return try {
            ThemeMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    
    private fun legacyThemeModeFromConfig(config: ThemeConfig): LegacyThemeMode {
        return if (config.mode == ThemeMode.SYSTEM) {
            LegacyThemeMode.SYSTEM
        } else if (config.mode == ThemeMode.LIGHT && config.color == ThemeColor.BLUE) {
            LegacyThemeMode.LIGHT
        } else if (config.mode == ThemeMode.DARK && config.color == ThemeColor.BLUE) {
            LegacyThemeMode.DARK
        } else {
            when (config.color) {
                ThemeColor.BLUE -> LegacyThemeMode.BLUE
                ThemeColor.PURPLE -> LegacyThemeMode.PURPLE
                ThemeColor.PINK -> LegacyThemeMode.PINK
            }
        }
    }
    
    
    /**
     * Actualiza la configuración completa del tema
     */
    fun setThemeConfig(config: ThemeConfig) {
        preferenceManager.saveThemeColor(config.color.name)
        preferenceManager.saveThemeMode(config.mode.name)
        _currentThemeConfig.value = config
        _currentTheme.value = legacyThemeModeFromConfig(config)
        
        // Mantener compatibilidad con el sistema anterior
        val legacyThemeString = when {
            config.mode == ThemeMode.SYSTEM -> "SYSTEM"
            config.mode == ThemeMode.LIGHT && config.color == ThemeColor.BLUE -> "LIGHT"
            config.mode == ThemeMode.DARK && config.color == ThemeColor.BLUE -> "DARK"
            else -> config.color.name
        }
        preferenceManager.saveAppTheme(legacyThemeString)
    }
    
    /**
     * Actualiza solo el color del tema
     */
    fun setThemeColor(color: ThemeColor) {
        val currentConfig = _currentThemeConfig.value
        setThemeConfig(currentConfig.copy(color = color))
    }
    
    /**
     * Actualiza solo el modo del tema
     */
    fun setThemeMode(mode: ThemeMode) {
        val currentConfig = _currentThemeConfig.value
        setThemeConfig(currentConfig.copy(mode = mode))
    }
    
    /**
     * Legacy: Updates the theme preference and notifies observers
     * @deprecated Usar setThemeConfig() en su lugar
     */
    @Deprecated("Usar setThemeConfig() para el nuevo sistema")
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
    }
    
    /**
     * Legacy: Updates theme from string and applies immediately
     * Used by settings to ensure instant theme switching
     * @deprecated Usar setThemeConfig() en su lugar
     */
    @Deprecated("Usar setThemeConfig() para el nuevo sistema")
    fun updateThemeFromString(themeString: String) {
        val config = when (themeString) {
            "LIGHT" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.LIGHT)
            "DARK" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
            "BLUE" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
            "PURPLE" -> ThemeConfig(ThemeColor.PURPLE, ThemeMode.DARK)
            "PINK" -> ThemeConfig(ThemeColor.PINK, ThemeMode.DARK)
            "SYSTEM" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.SYSTEM)
            else -> ThemeConfig.DEFAULT
        }
        setThemeConfig(config)
    }
    
    /**
     * Legacy method: Refreshes theme from preferences
     * Useful when preferences might have changed externally
     */
    fun refreshThemeFromPreferences() {
        _currentThemeConfig.value = getCurrentThemeConfigFromPreferences()
        _currentTheme.value = legacyThemeModeFromConfig(_currentThemeConfig.value)
    }
    
    /**
     * Determines if dark theme should be used based on current theme configuration
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
     * Gets complete display name for current theme config
     */
    fun getCurrentThemeDisplayName(): String {
        val config = _currentThemeConfig.value
        return "${getThemeColorDisplayName(config.color)} • ${getThemeModeDisplayName(config.mode)}"
    }
    
    /**
     * Gets the display name for a theme color
     */
    fun getThemeColorDisplayName(themeColor: ThemeColor): String {
        return when (themeColor) {
            ThemeColor.BLUE -> "Azul"
            ThemeColor.PURPLE -> "Púrpura"
            ThemeColor.PINK -> "Rosa"
        }
    }
    
    /**
     * Gets the display name for a theme mode
     */
    fun getThemeModeDisplayName(themeMode: ThemeMode): String {
        return when (themeMode) {
            ThemeMode.LIGHT -> "Claro"
            ThemeMode.DARK -> "Oscuro"
            ThemeMode.SYSTEM -> "Sistema"
        }
    }
    
    /**
     * Legacy: Gets the display name for a theme mode
     * @deprecated Usar getThemeColorDisplayName() y getThemeModeDisplayName()
     */
    @Deprecated("Usar getThemeColorDisplayName() y getThemeModeDisplayName()")
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
     * Gets all available theme colors
     */
    fun getAvailableThemeColors(): List<ThemeColor> {
        return listOf(ThemeColor.BLUE, ThemeColor.PURPLE, ThemeColor.PINK)
    }
    
    /**
     * Gets all available theme modes
     */
    fun getAvailableThemeModes(): List<ThemeMode> {
        return listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    }
    
    /**
     * Legacy: Gets all available theme options
     * @deprecated Usar getAvailableThemeColors() y getAvailableThemeModes()
     */
    @Deprecated("Usar getAvailableThemeColors() y getAvailableThemeModes()")
    fun getAvailableThemes(): List<LegacyThemeMode> {
        return listOf(LegacyThemeMode.DARK, LegacyThemeMode.LIGHT, LegacyThemeMode.BLUE, LegacyThemeMode.PURPLE, LegacyThemeMode.PINK, LegacyThemeMode.SYSTEM)
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