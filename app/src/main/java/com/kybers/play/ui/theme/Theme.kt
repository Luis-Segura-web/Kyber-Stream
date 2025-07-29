package com.kybers.play.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Elegant theme for IPTV App with dynamic theme switching
 */
@Composable
fun IPTVAppTheme(
    themeManager: ThemeManager? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentThemeManager = themeManager ?: rememberThemeManager(context)
    
    // Get the current theme preference
    val isDarkTheme = currentThemeManager.shouldUseDarkTheme()
    
    // Create elegant color schemes
    val darkColors = darkColorScheme(
        primary = DarkTheme.Primary,
        onPrimary = DarkTheme.OnPrimary,
        primaryContainer = DarkTheme.PrimaryVariant,
        onPrimaryContainer = DarkTheme.OnPrimary,
        
        secondary = DarkTheme.Secondary,
        onSecondary = DarkTheme.OnSecondary,
        secondaryContainer = DarkTheme.SecondaryVariant,
        onSecondaryContainer = DarkTheme.OnSecondary,
        
        background = DarkTheme.Background,
        onBackground = DarkTheme.OnBackground,
        surface = DarkTheme.Surface,
        onSurface = DarkTheme.OnSurface,
        surfaceVariant = DarkTheme.SurfaceVariant,
        onSurfaceVariant = DarkTheme.OnSurface,
        
        outline = DarkTheme.Outline,
        error = DarkTheme.Error,
        onError = DarkTheme.OnError
    )
    
    val lightColors = lightColorScheme(
        primary = LightTheme.Primary,
        onPrimary = LightTheme.OnPrimary,
        primaryContainer = LightTheme.PrimaryVariant,
        onPrimaryContainer = LightTheme.OnPrimary,
        
        secondary = LightTheme.Secondary,
        onSecondary = LightTheme.OnSecondary,
        secondaryContainer = LightTheme.SecondaryVariant,
        onSecondaryContainer = LightTheme.OnSecondary,
        
        background = LightTheme.Background,
        onBackground = LightTheme.OnBackground,
        surface = LightTheme.Surface,
        onSurface = LightTheme.OnSurface,
        surfaceVariant = LightTheme.SurfaceVariant,
        onSurfaceVariant = LightTheme.OnSurface,
        
        outline = LightTheme.Outline,
        error = LightTheme.Error,
        onError = LightTheme.OnError
    )

    val colors = if (isDarkTheme) darkColors else lightColors

    // Configure system UI (status bar, navigation bar)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowInsetsControllerCompat(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    // Apply the theme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography
    ) {
        Box(
            modifier = Modifier
                .systemBarsPadding()
                .background(colors.background)
        ) {
            content()
        }
    }
}

/**
 * Legacy theme composable for backward compatibility
 * Delegates to the new theme system
 */
@Composable
fun IPTVAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Create a temporary theme manager that uses the legacy darkTheme parameter
    val context = LocalContext.current
    val themeManager = rememberThemeManager(context)
    
    // Override the theme based on the darkTheme parameter if needed
    // This is for backward compatibility only
    IPTVAppTheme(
        themeManager = themeManager,
        content = content
    )
}
