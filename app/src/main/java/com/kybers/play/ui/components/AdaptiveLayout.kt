package com.kybers.play.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Utility for adaptive UI components based on screen size and orientation
 */
object AdaptiveLayout {
    
    @Composable
    fun getColumnsForScreen(): Int {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp
        
        return when {
            screenWidth > 900 -> 5  // Very large screens
            screenWidth > 700 -> 4  // Large tablets
            screenWidth > 500 -> 3  // Tablets/large phones
            else -> 3               // Standard phones
        }
    }
    
    @Composable
    fun getItemSpacing(): Dp {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp
        
        return when {
            screenWidth > 800 -> 12.dp
            screenWidth > 600 -> 10.dp
            else -> 8.dp
        }
    }
    
    @Composable
    fun getContentPadding(): Dp {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp
        
        return when {
            screenWidth > 800 -> 20.dp
            screenWidth > 600 -> 16.dp
            else -> 12.dp
        }
    }
}