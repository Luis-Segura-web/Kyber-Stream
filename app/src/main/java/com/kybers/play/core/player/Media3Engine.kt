package com.kybers.play.core.player

import android.app.Application
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.util.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación de PlayerEngine usando Media3 (ExoPlayer) como reproductor principal
 */
class Media3Engine(
    private val application: Application,
    private val preferenceManager: PreferenceManager
) : PlayerEngine {
    
    companion object {
        private const val TAG = "Media3Engine"
    }
    
    private var exoPlayer: ExoPlayer? = null
    private var currentMediaSpec: MediaSpec? = null
    
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
     * Inicializa ExoPlayer si no está ya inicializado
     */
    private fun initializeExoPlayer() {
        if (exoPlayer == null) {
            val trackSelector = DefaultTrackSelector(application).apply {
                // Configurar preferencias de track basadas en configuraciones del usuario
                setParameters(
                    buildUponParameters()
                        .setPreferredAudioLanguage("es") // Preferir español
                        .setForceHighestSupportedBitrate(false) // Adaptativo por defecto
                )
            }
            
            exoPlayer = ExoPlayer.Builder(application)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(createMediaSourceFactory())
                .build()
                .apply {
                    setupPlayerListener()
                }
            
            SecureLog.d(TAG, "ExoPlayer initialized with adaptive streaming")
        }
    }

    /**
     * Crea la factory de media sources con configuraciones optimizadas
     */
    private fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(application).apply {
            // Configurar factory basada en preferencias del usuario
            // Se puede extender para configuraciones específicas de HLS, DASH, etc.
        }
    }

    /**
     * Configura el listener de eventos de ExoPlayer
     */
    private fun setupPlayerListener() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                SecureLog.d(TAG, "ExoPlayer state changed: $playbackState")
                _state.value = when (playbackState) {
                    Player.STATE_IDLE -> PlayerState.IDLE
                    Player.STATE_BUFFERING -> PlayerState.BUFFERING
                    Player.STATE_READY -> {
                        if (exoPlayer?.playWhenReady == true) PlayerState.PLAYING else PlayerState.READY
                    }
                    Player.STATE_ENDED -> PlayerState.ENDED
                    else -> PlayerState.IDLE
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady && exoPlayer?.playbackState == Player.STATE_READY) {
                    _state.value = PlayerState.PLAYING
                } else if (!playWhenReady) {
                    _state.value = PlayerState.PAUSED
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                SecureLog.e(TAG, "ExoPlayer error: ${error.message}", error)
                _state.value = PlayerState.ERROR
                _error.value = PlayerError(
                    code = error.errorCode,
                    message = "Error de reproducción: ${error.message}",
                    cause = error
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                SecureLog.d(TAG, "Video size changed: ${videoSize.width}x${videoSize.height}")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _position.value = newPosition.positionMs
            }
        })
    }

    override suspend fun setMedia(mediaSpec: MediaSpec) {
        try {
            initializeExoPlayer()
            currentMediaSpec = mediaSpec
            
            // Crear MediaItem con headers si están presentes
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(mediaSpec.url)
            
            // Aplicar headers HTTP si están presentes
            if (mediaSpec.getNetworkOptions().isNotEmpty()) {
                val headers = mediaSpec.getNetworkOptions()
                // En Media3, los headers se manejan a través de DataSource factory
                // Para esta implementación simplificada, solo guardamos las opciones para uso posterior
                SecureLog.d(TAG, "Headers configured: ${headers.keys}")
            }
            
            val mediaItem = mediaItemBuilder.build()
            
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            
            _state.value = PlayerState.PREPARING
            _error.value = null // Limpiar error anterior
            
            SecureLog.logStreamUrl(TAG, mediaSpec.url, "Media3 preparing")
            
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to set media", e)
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
            exoPlayer?.play()
            SecureLog.d(TAG, "Play command sent to ExoPlayer")
            
            // Enviar telemetría si está disponible
            currentMediaSpec?.let { mediaSpec ->
                try {
                    com.kybers.play.util.XLogClient.sendVideoPlay(application, mediaSpec.url)
                } catch (e: Exception) {
                    SecureLog.w(TAG, "Failed to send telemetry", e)
                }
            }
            
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to start playback", e)
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
            exoPlayer?.pause()
            SecureLog.d(TAG, "Pause command sent to ExoPlayer")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to pause playback", e)
        }
    }

    override suspend fun stop() {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            _state.value = PlayerState.IDLE
            _position.value = 0L
            _duration.value = 0L
            _bufferPercentage.value = 0
            currentMediaSpec = null
            SecureLog.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to stop playback", e)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            exoPlayer?.seekTo(positionMs)
            _position.value = positionMs
            SecureLog.d(TAG, "Seek to position: ${positionMs}ms")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to seek", e)
        }
    }

    override suspend fun setVolume(volume: Float) {
        try {
            exoPlayer?.volume = volume.coerceIn(0f, 1f)
            SecureLog.d(TAG, "Volume set to: $volume")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to set volume", e)
        }
    }

    override suspend fun release() {
        try {
            SecureLog.d(TAG, "Releasing ExoPlayer resources")
            
            exoPlayer?.let { player ->
                player.clearVideoSurface()
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            
            exoPlayer = null
            currentMediaSpec = null
            
            _state.value = PlayerState.IDLE
            _position.value = 0L
            _duration.value = 0L
            _bufferPercentage.value = 0
            _error.value = null
            
            SecureLog.d(TAG, "ExoPlayer resources released successfully")
            
        } catch (e: Exception) {
            SecureLog.e(TAG, "Error during ExoPlayer resource release", e)
        }
    }

    override fun getCapabilities(): PlayerCapabilities {
        return PlayerCapabilities(
            supportedFormats = setOf(
                "mp4", "m4a", "fmp4", "webm", "mkv", "mp3", "ogg", "wav", "flac",
                "aac", "opus", "flv", "ts", "m3u8", "mpd", "ism", "avi", "mov",
                "wmv", "3gp", "mxf", "rtmp", "rtsp", "http", "https"
            ),
            supportsHardwareAcceleration = true,
            supportsCasting = true, // Media3 tiene soporte nativo para casting
            supportsSubtitles = true,
            maxBufferSize = 50000 // ms (más optimizado que VLC)
        )
    }

    /**
     * Obtiene la instancia de ExoPlayer para integración con UI (PlayerView)
     */
    fun getExoPlayer(): ExoPlayer? = exoPlayer

    /**
     * Actualiza métricas de posición y duración en tiempo real
     */
    private fun updatePlaybackMetrics() {
        exoPlayer?.let { player ->
            _position.value = player.currentPosition
            _duration.value = player.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            _bufferPercentage.value = player.bufferedPercentage
        }
    }
}