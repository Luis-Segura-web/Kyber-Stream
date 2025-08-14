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

    /** Modo actual de relación de aspecto solicitado por la UI */
    val aspectMode: StateFlow<VideoAspectMode>
    
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

    // --- Gestión de pistas ---

    /** Lista de pistas de audio disponibles y selección actual */
    fun listAudioTracks(): List<EngineTrack>

    /** Lista de pistas de subtítulos disponibles y selección actual */
    fun listSubtitleTracks(): List<EngineTrack>

    /** Selecciona una pista de audio por id (semántica dependiente del motor) */
    suspend fun selectAudioTrack(id: Int)

    /** Selecciona una pista de subtítulos por id; use -1 para desactivar */
    suspend fun selectSubtitleTrack(id: Int)

    /** Cambia la relación de aspecto del video */
    suspend fun setAspectRatio(mode: VideoAspectMode)
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

/**
 * Información de una pista de media expuesta por el motor.
 * El campo id es opaco y específico del motor; úsese solo para seleccionar.
 */
data class EngineTrack(
    val id: Int,
    val name: String,
    val isSelected: Boolean
)

/** Modos de relación de aspecto aceptados por los motores */
enum class VideoAspectMode {
    FIT_SCREEN,
    FILL_SCREEN,
    ASPECT_16_9,
    ASPECT_4_3
}