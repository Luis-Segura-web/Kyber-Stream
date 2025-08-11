package com.kybers.play.core.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Especificación de media para reproducción con toda la información necesaria
 */
data class MediaSpec(
    val url: String,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val referer: String? = null,
    val authorization: String? = null,
    val forceSoftwareDecoding: Boolean = false
) {
    /**
     * Genera un ID único basado en la URL para identificar la sesión
     */
    fun generateOwnerId(): String = "media:${url.hashCode()}"
    
    /**
     * Obtiene todas las opciones de red como mapa
     */
    fun getNetworkOptions(): Map<String, String> {
        val options = headers.toMutableMap()
        userAgent?.let { options["User-Agent"] = it }
        referer?.let { options["Referer"] = it }
        authorization?.let { options["Authorization"] = it }
        return options
    }
}

/**
 * Estados posibles del reproductor
 */
enum class PlayerState {
    IDLE,           // Sin media cargado
    PREPARING,      // Preparando media
    READY,          // Listo para reproducir
    PLAYING,        // Reproduciendo
    PAUSED,         // Pausado
    BUFFERING,      // Almacenando en búfer
    ERROR,          // Error de reproducción
    ENDED           // Reproducción terminada
}

/**
 * Información de error del reproductor
 */
data class PlayerError(
    val code: Int,
    val message: String,
    val cause: Throwable? = null
)

/**
 * Interfaz común para todos los motores de reproducción (Media3, VLC)
 */
interface PlayerEngine {
    
    /** Estado actual del reproductor */
    val state: StateFlow<PlayerState>
    
    /** Error actual si existe */
    val error: StateFlow<PlayerError?>
    
    /** Posición actual de reproducción en milisegundos */
    val position: StateFlow<Long>
    
    /** Duración total del media en milisegundos */
    val duration: StateFlow<Long>
    
    /** Nivel de búfer en porcentaje (0-100) */
    val bufferPercentage: StateFlow<Int>
    
    /**
     * Establece el media a reproducir
     * @param mediaSpec Especificación del media
     */
    suspend fun setMedia(mediaSpec: MediaSpec)
    
    /**
     * Inicia la reproducción
     */
    suspend fun play()
    
    /**
     * Pausa la reproducción
     */
    suspend fun pause()
    
    /**
     * Detiene la reproducción y libera el media
     */
    suspend fun stop()
    
    /**
     * Busca a una posición específica
     * @param positionMs Posición en milisegundos
     */
    suspend fun seekTo(positionMs: Long)
    
    /**
     * Establece el volumen
     * @param volume Volumen entre 0.0 y 1.0
     */
    suspend fun setVolume(volume: Float)
    
    /**
     * Libera todos los recursos del reproductor
     */
    suspend fun release()
    
    /**
     * Obtiene información sobre las capacidades del motor
     */
    fun getCapabilities(): PlayerCapabilities
}

/**
 * Capacidades y limitaciones de un motor de reproducción
 */
data class PlayerCapabilities(
    val supportedFormats: Set<String>,
    val supportsHardwareAcceleration: Boolean,
    val supportsCasting: Boolean,
    val supportsSubtitles: Boolean,
    val maxBufferSize: Int
)