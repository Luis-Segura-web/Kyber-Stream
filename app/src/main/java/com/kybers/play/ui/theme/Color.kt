package com.kybers.play.ui.theme

import androidx.compose.ui.graphics.Color

// ======= ELEGANT DARK THEME COLORS =======
object DarkTheme {
    // Primary Colors - Deep Purple/Violet theme
    val Primary = Color(0xFF9C5BFF)           // Rich violet
    val PrimaryVariant = Color(0xFF7B42D3)    // Deeper violet
    val OnPrimary = Color(0xFFFFFFFF)         // White text on primary
    
    // Secondary Colors - Teal accent
    val Secondary = Color(0xFF04D9C4)         // Vibrant teal
    val SecondaryVariant = Color(0xFF018A7B)  // Darker teal
    val OnSecondary = Color(0xFF000000)       // Black text on secondary
    
    // Background & Surface
    val Background = Color(0xFF0E0E0E)        // True black background
    val Surface = Color(0xFF1A1A1A)           // Dark grey for cards/surfaces
    val SurfaceVariant = Color(0xFF2A2A2A)    // Lighter surface for elevated elements
    val OnBackground = Color(0xFFE6E6E6)      // Light grey text
    val OnSurface = Color(0xFFE6E6E6)         // Light grey text on surface
    
    // Additional elegant colors
    val Outline = Color(0xFF404040)           // Border/outline color
    val Error = Color(0xFFFF5252)             // Error red
    val OnError = Color(0xFFFFFFFF)           // White text on error
}

// ======= ELEGANT LIGHT THEME COLORS =======
object LightTheme {
    // Primary Colors - Purple theme
    val Primary = Color(0xFF6A1B9A)           // Deep purple
    val PrimaryVariant = Color(0xFF4A148C)    // Even deeper purple
    val OnPrimary = Color(0xFFFFFFFF)         // White text on primary
    
    // Secondary Colors - Teal accent
    val Secondary = Color(0xFF00695C)         // Dark teal
    val SecondaryVariant = Color(0xFF004D40)  // Darker teal
    val OnSecondary = Color(0xFFFFFFFF)       // White text on secondary
    
    // Background & Surface
    val Background = Color(0xFFFAFAFA)        // Off-white background
    val Surface = Color(0xFFFFFFFF)           // Pure white for cards/surfaces
    val SurfaceVariant = Color(0xFFF5F5F5)    // Light grey for elevated elements
    val OnBackground = Color(0xFF1A1A1A)      // Dark text
    val OnSurface = Color(0xFF1A1A1A)         // Dark text on surface
    
    // Additional elegant colors
    val Outline = Color(0xFFE0E0E0)           // Light border/outline color
    val Error = Color(0xFFD32F2F)             // Error red
    val OnError = Color(0xFFFFFFFF)           // White text on error
}

// Legacy colors for backward compatibility (can be removed later if not used)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Legacy dark theme colors (keeping for compatibility)
val PrimaryPurple = Color(0xFFBB86FC)
val SecondaryTeal = Color(0xFF03DAC6)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
