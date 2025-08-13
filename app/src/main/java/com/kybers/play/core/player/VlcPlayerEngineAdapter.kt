package com.kybers.play.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.MediaPlayer

/**
 * Adaptador que envuelve un MediaPlayer de VLC para funcionar como PlayerEngine
 * Esto permite usar el sistema legacy VLC con la nueva arquitectura de PlayerHost
 */
class VlcPlayerEngineAdapter(
    private val mediaPlayer: MediaPlayer
) : PlayerEngine {

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

    init {
        setupEventListener()
    }

    private fun setupEventListener() {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    _state.value = PlayerState.PREPARING
                }
                MediaPlayer.Event.Playing -> {
                    _state.value = PlayerState.PLAYING
                    _error.value = null
                }
                MediaPlayer.Event.Paused -> {
                    _state.value = PlayerState.PAUSED
                }
                MediaPlayer.Event.Stopped -> {
                    _state.value = PlayerState.IDLE
                }
                MediaPlayer.Event.EndReached -> {
                    _state.value = PlayerState.ENDED
                }
                MediaPlayer.Event.EncounteredError -> {
                    _state.value = PlayerState.ERROR
                    _error.value = PlayerError(
                        code = -1,
                        message = "Error de reproducción en VLC"
                    )
                }
                MediaPlayer.Event.Buffering -> {
                    _bufferPercentage.value = event.buffering.toInt()
                    if (event.buffering < 100f) {
                        _state.value = PlayerState.BUFFERING
                    }
                }
                MediaPlayer.Event.PositionChanged -> {
                    val duration = _duration.value
                    if (duration > 0) {
                        _position.value = (event.positionChanged * duration).toLong()
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    _duration.value = event.lengthChanged
                }
            }
        }
    }

    override suspend fun setMedia(mediaSpec: MediaSpec) {
        // Este método no se usa en el adaptador ya que el media ya está configurado en PlayerManager
    }

    override suspend fun play() {
        mediaPlayer.play()
    }

    override suspend fun pause() {
        mediaPlayer.pause()
    }

    override suspend fun stop() {
        mediaPlayer.stop()
        _state.value = PlayerState.IDLE
        _position.value = 0L
        _duration.value = 0L
        _bufferPercentage.value = 0
    }

    override suspend fun seekTo(positionMs: Long) {
        val duration = _duration.value
        if (duration > 0) {
            val position = positionMs.toFloat() / duration.toFloat()
            mediaPlayer.position = position
            _position.value = positionMs
        }
    }

    override suspend fun setVolume(volume: Float) {
        val vlcVolume = (volume * 100).toInt().coerceIn(0, 100)
        mediaPlayer.setVolume(vlcVolume)
    }

    override suspend fun release() {
        // No liberamos aquí porque es responsabilidad de PlayerManager
        mediaPlayer.setEventListener(null)
    }

    override fun getCapabilities(): PlayerCapabilities {
        return PlayerCapabilities(
            supportedFormats = setOf(
                "mp4", "avi", "mkv", "mov", "flv", "webm", "m4v",
                "ts", "m3u8", "mpd", "rtmp", "rtsp", "http", "https"
            ),
            supportsHardwareAcceleration = true,
            supportsCasting = false,
            supportsSubtitles = true,
            maxBufferSize = 8000
        )
    }

    /**
     * Obtiene el MediaPlayer original para interoperabilidad
     */
    fun getMediaPlayer(): MediaPlayer = mediaPlayer
}