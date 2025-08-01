package com.kybers.play.ui.player

import java.lang.IllegalArgumentException

/**
 * Enum para las opciones de ordenamiento.
 * ¡MOVIMOS ESTE ENUM AQUÍ!
 * Ahora está en una ubicación central para que tanto la pantalla de Canales como la de Películas
 * puedan usarlo sin crear dependencias extrañas entre ellas.
 */
enum class SortOrder {
    DEFAULT, // Orden original del servicio
    AZ,      // Alfabético A-Z
    ZA       // Alfabético Z-A
}

/**
 * Enum para representar el estado actual del reproductor.
 */
enum class PlayerStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    ERROR,
    PAUSED,
    RETRYING,        // New - retry in progress
    RETRY_FAILED,    // New - all retries exhausted
    LOADING          // New - distinct from buffering (initial load)
}

/**
 * Enum para los modos de relación de aspecto.
 */
enum class AspectRatioMode {
    FIT_SCREEN,   // Ajustar a la pantalla (por defecto de VLC, escala 0.0f)
    FILL_SCREEN,  // Llenar la pantalla (puede recortar, escala 1.0f si no hay ratio específico)
    ASPECT_16_9,  // Relación de aspecto 16:9
    ASPECT_4_3    // Relación de aspecto 4:3
}

/**
 * Clase de datos para representar la información de una pista (audio, subtítulo, video).
 *
 * @param id El identificador único de la pista.
 * @param name El nombre legible de la pista (ej. "Español", "720p").
 * @param isSelected Verdadero si esta es la pista actualmente activa.
 */
data class TrackInfo(
    val id: Int,
    val name: String,
    val isSelected: Boolean
)

/**
 * Función de extensión para convertir un String (guardado en las preferencias) a un valor del enum SortOrder.
 * Si el String no es válido, devuelve SortOrder.DEFAULT para evitar crashes.
 */
fun String?.toSortOrder(): SortOrder {
    return when (this) {
        "AZ" -> SortOrder.AZ
        "ZA" -> SortOrder.ZA
        else -> SortOrder.DEFAULT
    }
}

/**
 * Función de extensión para convertir un String a un AspectRatioMode.
 */
fun String.toAspectRatioMode(): AspectRatioMode {
    return try {
        AspectRatioMode.valueOf(this)
    } catch (e: IllegalArgumentException) {
        AspectRatioMode.FIT_SCREEN
    }
}
