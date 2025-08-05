package com.kybers.play.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sistema de colores rosa elegante con excelente contraste
 * Diseñado para ofrecer una experiencia visual cálida y moderna
 */
object PinkTheme {
    // === COLORES PRINCIPALES ROSA ===
    val Primary = Color(0xFFE91E63)           // Material Pink 500 - Rosa principal
    val PrimaryVariant = Color(0xFFC2185B)    // Material Pink 700 - Rosa más oscuro
    val PrimaryLight = Color(0xFFF48FB1)      // Material Pink 200 - Rosa claro
    val OnPrimary = Color(0xFFFFFFFF)         // Blanco en rosa
    
    // === COLORES SECUNDARIOS ===
    val Secondary = Color(0xFFAD1457)         // Material Pink 800 - Rosa intenso
    val SecondaryVariant = Color(0xFF880E4F)  // Material Pink 900 - Rosa muy oscuro
    val SecondaryLight = Color(0xFFF8BBD0)    // Material Pink 100 - Rosa muy claro
    val OnSecondary = Color(0xFFFFFFFF)       // Blanco en secundario
    
    // === COLORES DE ACENTO ===
    val Accent = Color(0xFFEC407A)            // Material Pink 400 - Rosa de acento
    val AccentLight = Color(0xFFFCE4EC)       // Material Pink 50 - Rosa acento claro
    val AccentDark = Color(0xFF880E4F)        // Material Pink 900 - Rosa acento oscuro
    
    // === SUPERFICIES TEMA OSCURO ===
    val BackgroundDark = Color(0xFF1F0A15)    // Rosa muy oscuro, casi negro
    val SurfaceDark = Color(0xFF1F0F1A)       // Rosa oscuro para tarjetas
    val SurfaceVariantDark = Color(0xFF2E1A25) // Rosa medio para elementos elevados
    val OnBackgroundDark = Color(0xFFFCE4EC)  // Rosa muy claro para texto
    val OnSurfaceDark = Color(0xFFF8BBD0)     // Rosa claro para texto en superficie
    
    // === SUPERFICIES TEMA CLARO ===
    val BackgroundLight = Color(0xFFFFF8FA)   // Rosa muy claro para fondo
    val SurfaceLight = Color(0xFFFFFFFF)      // Blanco puro para tarjetas
    val SurfaceVariantLight = Color(0xFFFCE4EC) // Rosa muy suave para elementos
    val OnBackgroundLight = Color(0xFF1F0A15) // Rosa muy oscuro para texto
    val OnSurfaceLight = Color(0xFF2E1A25)    // Rosa oscuro para texto en superficie
    
    // === COLORES ADICIONALES ===
    val OutlineDark = Color(0xFF4A2C3A)       // Bordes tema oscuro
    val OutlineLight = Color(0xFFF48FB1)      // Bordes tema claro
    val Error = Color(0xFFFF5252)             // Rojo de error
    val ErrorDark = Color(0xFFD32F2F)         // Rojo error oscuro
    val OnError = Color(0xFFFFFFFF)           // Blanco en error
    val Success = Color(0xFF4CAF50)           // Verde éxito
    val Warning = Color(0xFFFF9800)           // Naranja advertencia
}

/**
 * Paleta extendida para elementos específicos de la UI con tema rosa
 */
object PinkUIColors {
    // === GRADIENTES ===
    val PrimaryGradient = listOf(
        PinkTheme.Primary,
        PinkTheme.PrimaryVariant
    )
    
    val SecondaryGradient = listOf(
        PinkTheme.Secondary,
        PinkTheme.SecondaryVariant
    )
    
    val BackgroundGradient = listOf(
        PinkTheme.BackgroundDark,
        Color(0xFF880E4F)
    )
    
    // === PLAYER CONTROLS ===
    val PlayerBackground = Color(0x88000000)  // Semi-transparente
    val PlayerControlActive = PinkTheme.Primary
    val PlayerControlInactive = Color(0x66FFFFFF)
    val PlayerProgressTrack = PinkTheme.PrimaryLight
    val PlayerProgressBackground = Color(0x33FFFFFF)
    
    // === NAVIGATION ===
    val NavigationSelected = PinkTheme.Primary
    val NavigationUnselected = Color(0xFF757575)
    val NavigationBackground = PinkTheme.SurfaceDark
    
    // === CARDS Y CONTENIDO ===
    val CardBackground = PinkTheme.SurfaceDark
    val CardElevated = PinkTheme.SurfaceVariantDark
    val CardOverlay = Color(0x99000000)
    val CardBorder = PinkTheme.OutlineDark
    
    // === RATINGS Y BADGES ===
    val RatingGold = Color(0xFFFFD700)
    val RatingEmpty = Color(0xFF424242)
    val BadgeLive = Color(0xFFFF1744)
    val BadgeNew = Color(0xFF00E676)
    val BadgeHD = PinkTheme.Accent
}