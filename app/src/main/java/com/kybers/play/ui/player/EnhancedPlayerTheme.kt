package com.kybers.play.ui.player

import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kybers.play.ui.theme.*

/**
 * Enhanced theming for existing player components
 * Provides blue theme integration for current player controls
 */
object EnhancedPlayerTheme {
    
    /**
     * Enhanced slider colors for player progress and volume
     */
    @Composable
    fun sliderColors() = SliderDefaults.colors(
        thumbColor = BlueUIColors.PlayerControlActive,
        activeTrackColor = BlueUIColors.PlayerProgressTrack,
        inactiveTrackColor = BlueUIColors.PlayerProgressBackground,
        activeTickColor = BlueUIColors.PlayerControlActive.copy(alpha = 0.7f),
        inactiveTickColor = BlueUIColors.PlayerProgressBackground.copy(alpha = 0.7f)
    )
    
    /**
     * Player control button background colors
     */
    val controlButtonBackground = BlueUIColors.PlayerBackground.copy(alpha = 0.6f)
    val controlButtonActiveBackground = BlueUIColors.PlayerControlActive.copy(alpha = 0.9f)
    
    /**
     * Player overlay and background colors
     */
    val overlayBackground = BlueUIColors.PlayerBackground
    val gradientOverlay = listOf(
        BlueUIColors.PlayerBackground.copy(alpha = 0.6f),
        Color.Transparent,
        Color.Transparent,
        BlueUIColors.PlayerBackground.copy(alpha = 0.8f)
    )
    
    /**
     * Text colors for player UI
     */
    val primaryTextColor = Color.White
    val secondaryTextColor = Color.White.copy(alpha = DesignTokens.Alpha.medium)
    
    /**
     * Icon colors
     */
    val iconColor = Color.White
    val activeIconColor = BlueUIColors.PlayerControlActive
    val inactiveIconColor = BlueUIColors.PlayerControlInactive
    
    /**
     * Control sizes using design tokens
     */
    val buttonSize = DesignTokens.Size.playerControlSize
    val largeButtonSize = DesignTokens.Size.playerControlSize + 8.dp
    val iconSize = DesignTokens.Size.iconMd
    val largeIconSize = DesignTokens.Size.iconLg
    
    /**
     * Spacing values
     */
    val spacing = DesignTokens.Spacing
    val cornerRadius = DesignTokens.CornerRadius
    val elevation = DesignTokens.Elevation
}

/**
 * Extension functions to easily apply enhanced theming to existing components
 */

// Helper function for consistent time formatting across all player components
fun formatPlayerTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

// Helper function for progress calculation
fun calculateProgress(currentPosition: Long, duration: Long): Float {
    return if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
}

// Helper function for time from progress
fun timeFromProgress(progress: Float, duration: Long): Long {
    return (progress * duration).toLong().coerceIn(0L, duration)
}