package com.kybers.play.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema de tokens de diseño para Kyber Stream
 * Proporciona consistencia visual en toda la aplicación
 */
object DesignTokens {
    
    // === ESPACIADO ===
    object Spacing {
        val none = 0.dp
        val xs = 4.dp      // Extra small
        val sm = 8.dp      // Small
        val md = 16.dp     // Medium
        val lg = 24.dp     // Large
        val xl = 32.dp     // Extra large
        val xxl = 48.dp    // Double extra large
        val xxxl = 64.dp   // Triple extra large
        
        // Espaciados específicos
        val cardPadding = md
        val screenPadding = md
        val sectionSpacing = lg
        val itemSpacing = sm
        val buttonPadding = md
    }
    
    // === ESQUINAS REDONDEADAS ===
    object CornerRadius {
        val none = 0.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val round = 50.dp  // Para botones circulares
        
        // Esquinas específicas
        val card = md
        val button = sm
        val sheet = lg
        val dialog = xl
    }
    
    // === ELEVACIONES ===
    object Elevation {
        val none = 0.dp
        val xs = 1.dp
        val sm = 2.dp
        val md = 4.dp
        val lg = 8.dp
        val xl = 12.dp
        val xxl = 16.dp
        
        // Elevaciones específicas
        val card = sm
        val button = md
        val modal = xl
        val tooltip = lg
        val fab = lg
        val navigationBar = md
    }
    
    // === TAMAÑOS ===
    object Size {
        val iconXs = 16.dp
        val iconSm = 20.dp
        val iconMd = 24.dp
        val iconLg = 32.dp
        val iconXl = 48.dp
        val iconXxl = 64.dp
        
        val buttonHeight = 48.dp
        val inputHeight = 56.dp
        val appBarHeight = 64.dp
        val fabSize = 56.dp
        val avatarSm = 32.dp
        val avatarMd = 48.dp
        val avatarLg = 64.dp
        
        // Tamaños específicos para contenido multimedia
        val posterWidth = 120.dp
        val posterHeight = 180.dp
        val bannerHeight = 200.dp
        val playerControlSize = 48.dp
    }
    
    // === OPACIDADES ===
    object Alpha {
        val transparent = 0f
        val disabled = 0.38f
        val medium = 0.6f
        val high = 0.87f
        val opaque = 1f
        
        // Overlays
        val overlay = 0.7f
        val modalOverlay = 0.5f
        val cardOverlay = 0.8f
    }
    
    // === DURACIONES DE ANIMACIÓN ===
    object Animation {
        const val fast = 150
        const val normal = 300
        const val slow = 500
        const val extraSlow = 800
        
        // Animaciones específicas
        const val fade = normal
        const val slide = normal
        const val scale = fast
        const val rotation = normal
        const val pageTransition = normal
    }
}