package com.kybers.play.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize // Necesario para fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

// Local composition para acceder al tamaño del dispositivo desde cualquier parte
val LocalDeviceSize = compositionLocalOf { DeviceSize.COMPACT }

// Formas redondeadas para toda la aplicación
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Tema principal mejorado para Kyber Stream con sistema de colores y modos independientes
 * Soporta 3 colores (Azul, Púrpura, Rosa) × 3 modos (Claro, Oscuro, Sistema) = 9 combinaciones
 */
@Composable
fun KyberStreamTheme(
    themeManager: ThemeManager? = null,
    deviceSize: DeviceSize = rememberDeviceSize(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentThemeManager = themeManager ?: rememberThemeManager(context)
    
    val currentThemeConfig = currentThemeManager.currentThemeConfig.collectAsState().value
    val isDarkTheme = currentThemeManager.shouldUseDarkTheme()
    
    // Adaptar tipografía según el tamaño del dispositivo
    val typography = when (deviceSize) {
        DeviceSize.COMPACT -> CompactTypography
        DeviceSize.MEDIUM -> MediumTypography
        DeviceSize.EXPANDED -> ExpandedTypography
    }
    
    // === ESQUEMAS DE COLOR DINÁMICOS SEGÚN CONFIGURACIÓN ===
    val colors = createColorScheme(currentThemeConfig.color, isDarkTheme)

    // === CONFIGURACIÓN DE BARRAS DEL SISTEMA ===
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            val windowInsetsController = WindowInsetsControllerCompat(window, view)

            // Configurar apariencia de las barras usando la nueva API
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
            
            // Las barras son automáticamente transparentes en modo edge-to-edge
            // No necesitamos usar las APIs deprecadas statusBarColor/navigationBarColor
        }
    }

    // === APLICAR TEMA ===
    CompositionLocalProvider(LocalDeviceSize provides deviceSize) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = AppShapes,
            content = {
                // Box raíz para colorear el fondo detrás de las barras del sistema
                Box(
                    modifier = Modifier
                        .fillMaxSize() // Ocupa toda la pantalla
                        .background(colors.surface) // Color de fondo para las áreas de las barras del sistema
                ) {
                    // Box para el contenido principal de la aplicación
                    Box(
                        modifier = Modifier
                            .fillMaxSize() // Asegura que el contenido (y su fondo) llene el espacio disponible
                            .systemBarsPadding() // Aplica padding para no solaparse con las barras
                            .background( // Fondo del contenido principal de la app
                                brush = Brush.verticalGradient(
                                    colors = getBackgroundGradient(currentThemeConfig.color, isDarkTheme)
                                )
                            )
                    ) {
                        content() // El contenido de la UI del usuario
                    }
                }
            }
        )
    }
}

// Alias para compatibilidad
@Composable
fun IPTVAppTheme(
    themeManager: ThemeManager? = null,
    content: @Composable () -> Unit
) = KyberStreamTheme(themeManager, content = content)

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

// === FUNCIONES DE ESQUEMAS DE COLOR ===

/**
 * Crea un esquema de color basado en el color y modo seleccionados
 */
private fun createColorScheme(color: ThemeColor, isDark: Boolean) = when (color) {
    ThemeColor.BLUE -> createBlueColorScheme(isDark)
    ThemeColor.PURPLE -> createPurpleColorScheme(isDark)
    ThemeColor.PINK -> createPinkColorScheme(isDark)
}

/**
 * Crea un esquema de color claro
 */
private fun createLightColorScheme() = lightColorScheme(
    primary = DarkTheme.Primary,
    onPrimary = DarkTheme.OnPrimary,
    primaryContainer = LightTheme.Primary,
    onPrimaryContainer = LightTheme.PrimaryVariant,
    
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

/**
 * Crea un esquema de color oscuro
 */
private fun createDarkColorScheme() = darkColorScheme(
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

/**
 * Crea un esquema de color azul
 */
private fun createBlueColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = BlueTheme.Primary,
        onPrimary = BlueTheme.OnPrimary,
        primaryContainer = BlueTheme.PrimaryVariant,
        onPrimaryContainer = BlueTheme.OnPrimary,
        
        secondary = BlueTheme.Secondary,
        onSecondary = BlueTheme.OnSecondary,
        secondaryContainer = BlueTheme.SecondaryVariant,
        onSecondaryContainer = BlueTheme.OnSecondary,
        
        background = BlueTheme.BackgroundDark,
        onBackground = BlueTheme.OnBackgroundDark,
        surface = BlueTheme.SurfaceDark,
        onSurface = BlueTheme.OnSurfaceDark,
        surfaceVariant = BlueTheme.SurfaceVariantDark,
        onSurfaceVariant = BlueTheme.OnSurfaceDark,
        
        outline = BlueTheme.OutlineDark,
        error = BlueTheme.Error,
        onError = BlueTheme.OnError
    )
} else {
    lightColorScheme(
        primary = BlueTheme.Primary,
        onPrimary = BlueTheme.OnPrimary,
        primaryContainer = BlueTheme.PrimaryLight,
        onPrimaryContainer = BlueTheme.PrimaryVariant,
        
        secondary = BlueTheme.Secondary,
        onSecondary = BlueTheme.OnSecondary,
        secondaryContainer = BlueTheme.SecondaryLight,
        onSecondaryContainer = BlueTheme.SecondaryVariant,
        
        background = BlueTheme.BackgroundLight,
        onBackground = BlueTheme.OnBackgroundLight,
        surface = BlueTheme.SurfaceLight,
        onSurface = BlueTheme.OnSurfaceLight,
        surfaceVariant = BlueTheme.SurfaceVariantLight,
        onSurfaceVariant = BlueTheme.OnSurfaceLight,
        
        outline = BlueTheme.OutlineLight,
        error = BlueTheme.ErrorDark,
        onError = BlueTheme.OnError
    )
}

/**
 * Crea un esquema de color púrpura
 */
private fun createPurpleColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = PurpleTheme.Primary,
        onPrimary = PurpleTheme.OnPrimary,
        primaryContainer = PurpleTheme.PrimaryVariant,
        onPrimaryContainer = PurpleTheme.OnPrimary,
        
        secondary = PurpleTheme.Secondary,
        onSecondary = PurpleTheme.OnSecondary,
        secondaryContainer = PurpleTheme.SecondaryVariant,
        onSecondaryContainer = PurpleTheme.OnSecondary,
        
        background = PurpleTheme.BackgroundDark,
        onBackground = PurpleTheme.OnBackgroundDark,
        surface = PurpleTheme.SurfaceDark,
        onSurface = PurpleTheme.OnSurfaceDark,
        surfaceVariant = PurpleTheme.SurfaceVariantDark,
        onSurfaceVariant = PurpleTheme.OnSurfaceDark,
        
        outline = PurpleTheme.OutlineDark,
        error = PurpleTheme.Error,
        onError = PurpleTheme.OnError
    )
} else {
    lightColorScheme(
        primary = PurpleTheme.Primary,
        onPrimary = PurpleTheme.OnPrimary,
        primaryContainer = PurpleTheme.PrimaryLight,
        onPrimaryContainer = PurpleTheme.PrimaryVariant,
        
        secondary = PurpleTheme.Secondary,
        onSecondary = PurpleTheme.OnSecondary,
        secondaryContainer = PurpleTheme.SecondaryLight,
        onSecondaryContainer = PurpleTheme.SecondaryVariant,
        
        background = PurpleTheme.BackgroundLight,
        onBackground = PurpleTheme.OnBackgroundLight,
        surface = PurpleTheme.SurfaceLight,
        onSurface = PurpleTheme.OnSurfaceLight,
        surfaceVariant = PurpleTheme.SurfaceVariantLight,
        onSurfaceVariant = PurpleTheme.OnSurfaceLight,
        
        outline = PurpleTheme.OutlineLight,
        error = PurpleTheme.ErrorDark,
        onError = PurpleTheme.OnError
    )
}

/**
 * Crea un esquema de color rosa
 */
private fun createPinkColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = PinkTheme.Primary,
        onPrimary = PinkTheme.OnPrimary,
        primaryContainer = PinkTheme.PrimaryVariant,
        onPrimaryContainer = PinkTheme.OnPrimary,
        
        secondary = PinkTheme.Secondary,
        onSecondary = PinkTheme.OnSecondary,
        secondaryContainer = PinkTheme.SecondaryVariant,
        onSecondaryContainer = PinkTheme.OnSecondary,
        
        background = PinkTheme.BackgroundDark,
        onBackground = PinkTheme.OnBackgroundDark,
        surface = PinkTheme.SurfaceDark,
        onSurface = PinkTheme.OnSurfaceDark,
        surfaceVariant = PinkTheme.SurfaceVariantDark,
        onSurfaceVariant = PinkTheme.OnSurfaceDark,
        
        outline = PinkTheme.OutlineDark,
        error = PinkTheme.Error,
        onError = PinkTheme.OnError
    )
} else {
    lightColorScheme(
        primary = PinkTheme.Primary,
        onPrimary = PinkTheme.OnPrimary,
        primaryContainer = PinkTheme.PrimaryLight,
        onPrimaryContainer = PinkTheme.PrimaryVariant,
        
        secondary = PinkTheme.Secondary,
        onSecondary = PinkTheme.OnSecondary,
        secondaryContainer = PinkTheme.SecondaryLight,
        onSecondaryContainer = PinkTheme.SecondaryVariant,
        
        background = PinkTheme.BackgroundLight,
        onBackground = PinkTheme.OnBackgroundLight,
        surface = PinkTheme.SurfaceLight,
        onSurface = PinkTheme.OnSurfaceLight,
        surfaceVariant = PinkTheme.SurfaceVariantLight,
        onSurfaceVariant = PinkTheme.OnSurfaceLight,
        
        outline = PinkTheme.OutlineLight,
        error = PinkTheme.ErrorDark,
        onError = PinkTheme.OnError
    )
}

private fun getBackgroundGradient(themeColor: ThemeColor, isDark: Boolean): List<androidx.compose.ui.graphics.Color> {
    return when (themeColor) {
        ThemeColor.BLUE -> if (isDark) BlueUIColors.BackgroundGradient else listOf(BlueTheme.BackgroundLight, BlueTheme.BackgroundLight)
        ThemeColor.PURPLE -> if (isDark) PurpleUIColors.BackgroundGradient else listOf(PurpleTheme.BackgroundLight, PurpleTheme.BackgroundLight)
        ThemeColor.PINK -> if (isDark) PinkUIColors.BackgroundGradient else listOf(PinkTheme.BackgroundLight, PinkTheme.BackgroundLight)
    }
}

/**
 * Legacy: Obtiene el gradiente de fondo según el tema
 * @deprecated Usar getBackgroundGradient(ThemeColor, Boolean)
 */
@Deprecated("Usar getBackgroundGradient(ThemeColor, Boolean)")
private fun getBackgroundGradient(themeMode: LegacyThemeMode, isDark: Boolean): List<androidx.compose.ui.graphics.Color> {
    return when (themeMode) {
        LegacyThemeMode.BLUE -> if (isDark) BlueUIColors.BackgroundGradient else listOf(BlueTheme.BackgroundLight, BlueTheme.BackgroundLight)
        LegacyThemeMode.PURPLE -> if (isDark) PurpleUIColors.BackgroundGradient else listOf(PurpleTheme.BackgroundLight, PurpleTheme.BackgroundLight)
        LegacyThemeMode.PINK -> if (isDark) PinkUIColors.BackgroundGradient else listOf(PinkTheme.BackgroundLight, PinkTheme.BackgroundLight)
        LegacyThemeMode.DARK -> listOf(DarkTheme.Background, androidx.compose.ui.graphics.Color(0xFF1A1A1A))
        LegacyThemeMode.LIGHT -> listOf(LightTheme.Background, LightTheme.Background)
        LegacyThemeMode.SYSTEM -> if (isDark) listOf(DarkTheme.Background, androidx.compose.ui.graphics.Color(0xFF1A1A1A)) else listOf(LightTheme.Background, LightTheme.Background)
    }
}
