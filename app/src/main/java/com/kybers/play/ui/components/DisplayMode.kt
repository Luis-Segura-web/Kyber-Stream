package com.kybers.play.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridOn

/**
 * Enum que define los diferentes modos de visualización para el contenido.
 */
enum class DisplayMode {
    LIST,    // Vista de lista (vertical)
    GRID     // Vista de cuadrícula (grid)
}

/**
 * Extension function para convertir string a DisplayMode
 */
fun String.toDisplayMode(): DisplayMode {
    return when (this.uppercase()) {
        "GRID" -> DisplayMode.GRID
        "LIST" -> DisplayMode.LIST
        else -> DisplayMode.LIST // Default
    }
}

/**
 * Extension function para obtener el nombre localizado del modo de visualización
 */
fun DisplayMode.toLocalizedName(): String {
    return when (this) {
        DisplayMode.LIST -> "Lista"
        DisplayMode.GRID -> "Cuadrícula"
    }
}

/**
 * Extension function para obtener el ícono correspondiente al modo
 */
fun DisplayMode.getIcon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        DisplayMode.LIST -> Icons.AutoMirrored.Filled.ViewList
        DisplayMode.GRID -> Icons.Filled.GridOn
    }
}
