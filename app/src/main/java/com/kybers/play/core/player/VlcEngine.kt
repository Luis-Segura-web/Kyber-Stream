package com.kybers.play.core.player

import android.app.Application
import android.util.Log
import com.kybers.play.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import javax.inject.Inject

/**
 * Implementación de PlayerEngine usando LibVLC, integrado con el sistema de lease
 */
class VlcEngine(
    private val application: Application,
    private val settingsDataStore: SettingsDataStore
) : PlayerEngine {
    
    companion object {
        private const val TAG = "VlcEngine"
    }
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentMedia: Media? = null
    
    // Estados observables
    private val _state = MutableStateFlow(PlayerState.IDLE)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    
    private val _error = MutableStateFlow<PlayerError?>(null)
    override val error: StateFlow<PlayerError?> = _error.asStateFlow()
    
    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _bufferPercentage = MutableStateFlow(0)
    override val bufferPercentage: StateFlow<Int> = _bufferPercentage.asStateFlow()

    /**
     * Inicializa VLC si no está ya inicializado
     */
    private fun initializeVlc() {
        if (libVLC == null) {
            // TODO: Get VLC options from SettingsDataStore
            val options = ArrayList<String>()
            libVLC = LibVLC(application, options)
            Log.d(TAG, "LibVLC initialized with options: ${options.joinToString(", ")}")
        }
        
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer(libVLC!!)
            setupEventListener()
            Log.d(TAG, "MediaPlayer initialized")
        }
    }

    /**
     * Configura el listener de eventos de VLC
     */
    private fun setupEventListener() {
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    Log.d(TAG, "VLC Event: Opening")
                    _state.value = PlayerState.PREPARING
                }
                MediaPlayer.Event.Playing -> {
                    Log.d(TAG, "VLC Event: Playing")
                    _state.value = PlayerState.PLAYING
                    _error.value = null // Limpiar error anterior
                }
                MediaPlayer.Event.Paused -> {
                    Log.d(TAG, "VLC Event: Paused")
                    _state.value = PlayerState.PAUSED
                }
                MediaPlayer.Event.Stopped -> {
                    Log.d(TAG, "VLC Event: Stopped")
                    _state.value = PlayerState.IDLE
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "VLC Event: End reached")
                    _state.value = PlayerState.ENDED
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "VLC Event: Error encountered")
                    _state.value = PlayerState.ERROR
                    _error.value = PlayerError(
                        code = -1,
                        message = "Error de reproducción en VLC"
                    )
                }
                MediaPlayer.Event.Buffering -> {
                    Log.d(TAG, "VLC Event: Buffering (${event.buffering}%)")
                    _bufferPercentage.value = event.buffering.toInt()
                    if (event.buffering < 100f) {
                        _state.value = PlayerState.BUFFERING
                    }
                }
                MediaPlayer.Event.PositionChanged -> {
                    // Actualizar posición basada en la duración total
                    val duration = _duration.value
                    if (duration > 0) {
                        _position.value = (event.positionChanged * duration).toLong()
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    _duration.value = event.lengthChanged
                    Log.d(TAG, "Duration changed: ${event.lengthChanged}ms")
                }
                else -> {
                    // Ignorar otros eventos
                }
            }
        }
    }

    override suspend fun setMedia(mediaSpec: MediaSpec) {
        try {
            initializeVlc()
            
            // Liberar media anterior si existe
            releaseCurrentMedia()
            
            // Crear nuevo media con opciones de red
            val media = Media(libVLC!!, android.net.Uri.parse(mediaSpec.url))
            
            // Aplicar opciones específicas del media
            if (mediaSpec.forceSoftwareDecoding) {
                media.addOption("--avcodec-hw=none")
                Log.d(TAG, "Software decoding forced for this media")
            }
            
            // Aplicar headers de red
            mediaSpec.getNetworkOptions().forEach { (key, value) ->
                when (key.lowercase()) {
                    "user-agent" -> media.addOption("--http-user-agent=$value")
                    "referer" -> media.addOption("--http-referrer=$value")
                    // Otros headers pueden requerir configuración específica en VLC
                }
            }
            
            // Establecer el media
            mediaPlayer!!.media = media
            currentMedia = media
            
            _state.value = PlayerState.READY
            Log.d(TAG, "Media set successfully: ${mediaSpec.title ?: mediaSpec.url}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set media", e)
            _state.value = PlayerState.ERROR
            _error.value = PlayerError(
                code = -2,
                message = "Error al configurar el media: ${e.message}",
                cause = e
            )
            throw e
        }
    }

    override suspend fun play() {
        try {
            mediaPlayer?.play()
            Log.d(TAG, "Play command sent to VLC")
            
            // Enviar telemetría (si XLogClient está disponible)
            currentMedia?.let { media ->
                try {
                    com.kybers.play.util.XLogClient.sendVideoPlay(application, media.uri.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send telemetry", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            _state.value = PlayerState.ERROR
            _error.value = PlayerError(
                code = -3,
                message = "Error al iniciar reproducción: ${e.message}",
                cause = e
            )
            throw e
        }
    }

    override suspend fun pause() {
        try {
            mediaPlayer?.pause()
            Log.d(TAG, "Pause command sent to VLC")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause playback", e)
        }
    }

    override suspend fun stop() {
        try {
            mediaPlayer?.stop()
            releaseCurrentMedia()
            _state.value = PlayerState.IDLE
            _position.value = 0L
            _duration.value = 0L
            _bufferPercentage.value = 0
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop playback", e)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            val duration = _duration.value
            if (duration > 0) {
                val position = positionMs.toFloat() / duration.toFloat()
                mediaPlayer?.position = position
                _position.value = positionMs
                Log.d(TAG, "Seek to position: ${positionMs}ms (${position * 100}%)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
        }
    }

    override suspend fun setVolume(volume: Float) {
        try {
            val vlcVolume = (volume * 100).toInt().coerceIn(0, 100)
            mediaPlayer?.setVolume(vlcVolume)
            Log.d(TAG, "Volume set to: $vlcVolume%")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
        }
    }

    override suspend fun release() {
        try {
            Log.d(TAG, "Releasing VLC resources")
            
            mediaPlayer?.let { player ->
                player.setEventListener(null)
                if (player.isPlaying) {
                    player.stop()
                }
                releaseCurrentMedia()
                player.release()
            }
            
            libVLC?.release()
            
            mediaPlayer = null
            libVLC = null
            currentMedia = null
            
            _state.value = PlayerState.IDLE
            _position.value = 0L
            _duration.value = 0L
            _bufferPercentage.value = 0
            _error.value = null
            
            Log.d(TAG, "VLC resources released successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during VLC resource release", e)
        }
    }

    override fun getCapabilities(): PlayerCapabilities {
        return PlayerCapabilities(
            supportedFormats = setOf(
                "mp4", "avi", "mkv", "mov", "flv", "webm", "m4v",
                "ts", "m3u8", "mpd", "rtmp", "rtsp", "http", "https"
            ),
            supportsHardwareAcceleration = true,
            supportsCasting = false, // VLC no soporta casting directo
            supportsSubtitles = true,
            maxBufferSize = 8000 // ms
        )
    }

    /**
     * Libera el media actual de manera segura
     */
    private fun releaseCurrentMedia() {
        try {
            currentMedia?.let { media ->
                mediaPlayer?.media = null
                media.release()
                currentMedia = null
                Log.d(TAG, "Current media released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing current media", e)
        }
    }
}
