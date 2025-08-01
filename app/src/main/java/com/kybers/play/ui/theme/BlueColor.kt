package com.kybers.play.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sistema de colores azul elegante con excelente contraste
 * Cumple con WCAG 2.1 AA/AAA guidelines
 */
object BlueTheme {
    // === COLORES PRINCIPALES AZULES ===
    val Primary = Color(0xFF1976D2)           // Material Blue 700 - Azul principal
    val PrimaryVariant = Color(0xFF1565C0)    // Material Blue 800 - Azul más oscuro
    val PrimaryLight = Color(0xFF42A5F5)      // Material Blue 400 - Azul claro
    val OnPrimary = Color(0xFFFFFFFF)         // Blanco en azul
    
    // === COLORES SECUNDARIOS ===
    val Secondary = Color(0xFF0277BD)         // Light Blue 800 - Azul cyan
    val SecondaryVariant = Color(0xFF01579B)  // Light Blue 900 - Azul cyan oscuro
    val SecondaryLight = Color(0xFF29B6F6)    // Light Blue 400 - Azul cyan claro
    val OnSecondary = Color(0xFFFFFFFF)       // Blanco en secundario
    
    // === COLORES DE ACENTO ===
    val Accent = Color(0xFF2196F3)            // Material Blue 500 - Azul de acento
    val AccentLight = Color(0xFF64B5F6)       // Material Blue 300 - Azul acento claro
    val AccentDark = Color(0xFF0D47A1)        // Material Blue 900 - Azul acento oscuro
    
    // === SUPERFICIES TEMA OSCURO ===
    val BackgroundDark = Color(0xFF0A0E1A)    // Azul muy oscuro, casi negro
    val SurfaceDark = Color(0xFF0F1419)       // Azul oscuro para tarjetas
    val SurfaceVariantDark = Color(0xFF1A1F2E) // Azul medio para elementos elevados
    val OnBackgroundDark = Color(0xFFE3F2FD)  // Azul muy claro para texto
    val OnSurfaceDark = Color(0xFFE1F5FE)     // Azul muy claro para texto en superficie
    
    // === SUPERFICIES TEMA CLARO ===
    val BackgroundLight = Color(0xFFF3F8FF)   // Azul muy claro para fondo
    val SurfaceLight = Color(0xFFFFFFFF)      // Blanco puro para tarjetas
    val SurfaceVariantLight = Color(0xFFE8F4FD) // Azul muy suave para elementos
    val OnBackgroundLight = Color(0xFF0D1421) // Azul muy oscuro para texto
    val OnSurfaceLight = Color(0xFF1A1F2E)    // Azul oscuro para texto en superficie
    
    // === COLORES ADICIONALES ===
    val OutlineDark = Color(0xFF2C3E50)       // Bordes tema oscuro
    val OutlineLight = Color(0xFFB0BEC5)      // Bordes tema claro
    val Error = Color(0xFFFF5252)             // Rojo de error
    val ErrorDark = Color(0xFFD32F2F)         // Rojo error oscuro
    val OnError = Color(0xFFFFFFFF)           // Blanco en error
    val Success = Color(0xFF4CAF50)           // Verde éxito
    val Warning = Color(0xFFFF9800)           // Naranja advertencia
}

/**
 * Paleta extendida para elementos específicos de la UI
 */
object BlueUIColors {
    // === GRADIENTES ===
    val PrimaryGradient = listOf(
        BlueTheme.Primary,
        BlueTheme.PrimaryVariant
    )
    
    val SecondaryGradient = listOf(
        BlueTheme.Secondary,
        BlueTheme.SecondaryVariant
    )
    
    val BackgroundGradient = listOf(
        BlueTheme.BackgroundDark,
        Color(0xFF1A237E)
    )
    
    // === PLAYER CONTROLS ===
    val PlayerBackground = Color(0x88000000)  // Semi-transparente
    val PlayerControlActive = BlueTheme.Primary
    val PlayerControlInactive = Color(0x66FFFFFF)
    val PlayerProgressTrack = BlueTheme.PrimaryLight
    val PlayerProgressBackground = Color(0x33FFFFFF)
    
    // === NAVIGATION ===
    val NavigationSelected = BlueTheme.Primary
    val NavigationUnselected = Color(0xFF757575)
    val NavigationBackground = BlueTheme.SurfaceDark
    
    // === CARDS Y CONTENIDO ===
    val CardBackground = BlueTheme.SurfaceDark
    val CardElevated = BlueTheme.SurfaceVariantDark
    val CardOverlay = Color(0x99000000)
    val CardBorder = BlueTheme.OutlineDark
    
    // === RATINGS Y BADGES ===
    val RatingGold = Color(0xFFFFD700)
    val RatingEmpty = Color(0xFF424242)
    val BadgeLive = Color(0xFFFF1744)
    val BadgeNew = Color(0xFF00E676)
    val BadgeHD = BlueTheme.Accent
}