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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Tema azul elegante para Kyber Stream con alto contraste
 */
@Composable
fun KyberStreamTheme(
    themeManager: ThemeManager? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentThemeManager = themeManager ?: rememberThemeManager(context)
    
    val isDarkTheme = currentThemeManager.shouldUseDarkTheme()
    
    // === ESQUEMAS DE COLOR OPTIMIZADOS ===
    val darkColors = darkColorScheme(
        // Primarios
        primary = BlueTheme.Primary,
        onPrimary = BlueTheme.OnPrimary,
        primaryContainer = BlueTheme.PrimaryVariant,
        onPrimaryContainer = BlueTheme.OnPrimary,
        
        // Secundarios
        secondary = BlueTheme.Secondary,
        onSecondary = BlueTheme.OnSecondary,
        secondaryContainer = BlueTheme.SecondaryVariant,
        onSecondaryContainer = BlueTheme.OnSecondary,
        
        // Terciarios (usando acentos)
        tertiary = BlueTheme.Accent,
        onTertiary = BlueTheme.OnPrimary,
        tertiaryContainer = BlueTheme.AccentDark,
        onTertiaryContainer = BlueTheme.OnPrimary,
        
        // Superficies
        background = BlueTheme.BackgroundDark,
        onBackground = BlueTheme.OnBackgroundDark,
        surface = BlueTheme.SurfaceDark,
        onSurface = BlueTheme.OnSurfaceDark,
        surfaceVariant = BlueTheme.SurfaceVariantDark,
        onSurfaceVariant = BlueTheme.OnSurfaceDark,
        
        // Otros
        outline = BlueTheme.OutlineDark,
        outlineVariant = BlueTheme.OutlineDark.copy(alpha = 0.5f),
        error = BlueTheme.Error,
        onError = BlueTheme.OnError,
        errorContainer = BlueTheme.ErrorDark,
        onErrorContainer = BlueTheme.OnError,
        
        // Superficies especiales
        surfaceTint = BlueTheme.Primary,
        inverseSurface = BlueTheme.OnSurfaceDark,
        inverseOnSurface = BlueTheme.SurfaceDark,
        inversePrimary = BlueTheme.PrimaryLight,
        
        // Containers especiales
        surfaceContainer = BlueTheme.SurfaceDark,
        surfaceContainerHigh = BlueTheme.SurfaceVariantDark,
        surfaceContainerHighest = BlueTheme.SurfaceVariantDark.copy(alpha = 0.9f),
        surfaceContainerLow = BlueTheme.SurfaceDark.copy(alpha = 0.8f),
        surfaceContainerLowest = BlueTheme.BackgroundDark,
        
        // Bordes y sombras
        scrim = BlueUIColors.CardOverlay
    )
    
    val lightColors = lightColorScheme(
        // Primarios
        primary = BlueTheme.Primary,
        onPrimary = BlueTheme.OnPrimary,
        primaryContainer = BlueTheme.PrimaryLight,
        onPrimaryContainer = BlueTheme.PrimaryVariant,
        
        // Secundarios
        secondary = BlueTheme.Secondary,
        onSecondary = BlueTheme.OnSecondary,
        secondaryContainer = BlueTheme.SecondaryLight,
        onSecondaryContainer = BlueTheme.SecondaryVariant,
        
        // Terciarios
        tertiary = BlueTheme.Accent,
        onTertiary = BlueTheme.OnPrimary,
        tertiaryContainer = BlueTheme.AccentLight,
        onTertiaryContainer = BlueTheme.AccentDark,
        
        // Superficies
        background = BlueTheme.BackgroundLight,
        onBackground = BlueTheme.OnBackgroundLight,
        surface = BlueTheme.SurfaceLight,
        onSurface = BlueTheme.OnSurfaceLight,
        surfaceVariant = BlueTheme.SurfaceVariantLight,
        onSurfaceVariant = BlueTheme.OnSurfaceLight,
        
        // Otros
        outline = BlueTheme.OutlineLight,
        outlineVariant = BlueTheme.OutlineLight.copy(alpha = 0.5f),
        error = BlueTheme.ErrorDark,
        onError = BlueTheme.OnError,
        errorContainer = BlueTheme.Error.copy(alpha = 0.1f),
        onErrorContainer = BlueTheme.ErrorDark,
        
        // Superficies especiales
        surfaceTint = BlueTheme.Primary,
        inverseSurface = BlueTheme.OnSurfaceLight,
        inverseOnSurface = BlueTheme.SurfaceLight,
        inversePrimary = BlueTheme.Primary,
        
        // Containers
        surfaceContainer = BlueTheme.SurfaceLight,
        surfaceContainerHigh = BlueTheme.SurfaceVariantLight,
        surfaceContainerHighest = BlueTheme.SurfaceVariantLight.copy(alpha = 0.9f),
        surfaceContainerLow = BlueTheme.SurfaceLight.copy(alpha = 0.8f),
        surfaceContainerLowest = BlueTheme.BackgroundLight,
        
        scrim = BlueUIColors.CardOverlay.copy(alpha = 0.3f)
    )

    val colors = if (isDarkTheme) darkColors else lightColors

    // === CONFIGURACIÓN DE BARRAS DEL SISTEMA ===
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Color de la barra de estado
            window.statusBarColor = colors.surface.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            
            val windowInsetsController = WindowInsetsControllerCompat(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    // === APLICAR TEMA ===
    MaterialTheme(
        colorScheme = colors,
        typography = Typography
    ) {
        Box(
            modifier = Modifier
                .systemBarsPadding()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDarkTheme) {
                            BlueUIColors.BackgroundGradient
                        } else {
                            listOf(colors.background, colors.background)
                        }
                    )
                )
        ) {
            content()
        }
    }
}

// Alias para compatibilidad
@Composable
fun IPTVAppTheme(
    themeManager: ThemeManager? = null,
    content: @Composable () -> Unit
) = KyberStreamTheme(themeManager, content)

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
    KyberStreamTheme(
        themeManager = themeManager,
        content = content
    )
}
