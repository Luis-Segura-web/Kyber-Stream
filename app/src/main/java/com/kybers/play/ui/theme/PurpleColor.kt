package com.kybers.play.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sistema de colores púrpura elegante con excelente contraste
 * Diseñado para ofrecer una experiencia visual rica y moderna
 */
object PurpleTheme {
    // === COLORES PRINCIPALES PÚRPURA ===
    val Primary = Color(0xFF9C27B0)           // Material Purple 500 - Púrpura principal
    val PrimaryVariant = Color(0xFF7B1FA2)    // Material Purple 600 - Púrpura más oscuro
    val PrimaryLight = Color(0xFFBA68C8)      // Material Purple 300 - Púrpura claro
    val OnPrimary = Color(0xFFFFFFFF)         // Blanco en púrpura
    
    // === COLORES SECUNDARIOS ===
    val Secondary = Color(0xFF8E24AA)         // Material Purple 600 - Púrpura intenso
    val SecondaryVariant = Color(0xFF6A1B9A)  // Material Purple 700 - Púrpura muy oscuro
    val SecondaryLight = Color(0xFFCE93D8)    // Material Purple 200 - Púrpura muy claro
    val OnSecondary = Color(0xFFFFFFFF)       // Blanco en secundario
    
    // === COLORES DE ACENTO ===
    val Accent = Color(0xFFAB47BC)            // Material Purple 400 - Púrpura de acento
    val AccentLight = Color(0xFFE1BEE7)       // Material Purple 100 - Púrpura acento claro
    val AccentDark = Color(0xFF4A148C)        // Material Purple 900 - Púrpura acento oscuro
    
    // === SUPERFICIES TEMA OSCURO ===
    val BackgroundDark = Color(0xFF1A0A1F)    // Púrpura muy oscuro, casi negro
    val SurfaceDark = Color(0xFF1F0F1F)       // Púrpura oscuro para tarjetas
    val SurfaceVariantDark = Color(0xFF2E1A2E) // Púrpura medio para elementos elevados
    val OnBackgroundDark = Color(0xFFF3E5F5)  // Púrpura muy claro para texto
    val OnSurfaceDark = Color(0xFFF8BBD9)     // Púrpura rosado claro para texto en superficie
    
    // === SUPERFICIES TEMA CLARO ===
    val BackgroundLight = Color(0xFFF8F3FF)   // Púrpura muy claro para fondo
    val SurfaceLight = Color(0xFFFFFFFF)      // Blanco puro para tarjetas
    val SurfaceVariantLight = Color(0xFFF3E5F5) // Púrpura muy suave para elementos
    val OnBackgroundLight = Color(0xFF1F0A1F) // Púrpura muy oscuro para texto
    val OnSurfaceLight = Color(0xFF2E1A2E)    // Púrpura oscuro para texto en superficie
    
    // === COLORES ADICIONALES ===
    val OutlineDark = Color(0xFF4A2C4A)       // Bordes tema oscuro
    val OutlineLight = Color(0xFFCE93D8)      // Bordes tema claro
    val Error = Color(0xFFFF5252)             // Rojo de error
    val ErrorDark = Color(0xFFD32F2F)         // Rojo error oscuro
    val OnError = Color(0xFFFFFFFF)           // Blanco en error
    val Success = Color(0xFF4CAF50)           // Verde éxito
    val Warning = Color(0xFFFF9800)           // Naranja advertencia
}

/**
 * Paleta extendida para elementos específicos de la UI con tema púrpura
 */
object PurpleUIColors {
    // === GRADIENTES ===
    val PrimaryGradient = listOf(
        PurpleTheme.Primary,
        PurpleTheme.PrimaryVariant
    )
    
    val SecondaryGradient = listOf(
        PurpleTheme.Secondary,
        PurpleTheme.SecondaryVariant
    )
    
    val BackgroundGradient = listOf(
        PurpleTheme.BackgroundDark,
        Color(0xFF4A148C)
    )
    
    // === PLAYER CONTROLS ===
    val PlayerBackground = Color(0x88000000)  // Semi-transparente
    val PlayerControlActive = PurpleTheme.Primary
    val PlayerControlInactive = Color(0x66FFFFFF)
    val PlayerProgressTrack = PurpleTheme.PrimaryLight
    val PlayerProgressBackground = Color(0x33FFFFFF)
    
    // === NAVIGATION ===
    val NavigationSelected = PurpleTheme.Primary
    val NavigationUnselected = Color(0xFF757575)
    val NavigationBackground = PurpleTheme.SurfaceDark
    
    // === CARDS Y CONTENIDO ===
    val CardBackground = PurpleTheme.SurfaceDark
    val CardElevated = PurpleTheme.SurfaceVariantDark
    val CardOverlay = Color(0x99000000)
    val CardBorder = PurpleTheme.OutlineDark
    
    // === RATINGS Y BADGES ===
    val RatingGold = Color(0xFFFFD700)
    val RatingEmpty = Color(0xFF424242)
    val BadgeLive = Color(0xFFFF1744)
    val BadgeNew = Color(0xFF00E676)
    val BadgeHD = PurpleTheme.Accent
}