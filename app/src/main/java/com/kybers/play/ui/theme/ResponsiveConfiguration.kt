package com.kybers.play.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

enum class DeviceSize { 
    COMPACT,    // Teléfonos
    MEDIUM,     // Tablets pequeñas
    EXPANDED    // Tablets grandes/escritorio
}

@Composable
fun rememberDeviceSize(): DeviceSize {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    return when {
        screenWidth < 600.dp -> DeviceSize.COMPACT      // Teléfonos
        screenWidth < 840.dp -> DeviceSize.MEDIUM       // Tablets pequeñas
        else -> DeviceSize.EXPANDED                     // Tablets grandes/escritorio
    }
}