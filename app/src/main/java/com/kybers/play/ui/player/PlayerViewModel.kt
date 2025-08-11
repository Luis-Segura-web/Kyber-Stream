package com.kybers.play.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kybers.play.player.PlayerManager
import org.videolan.libvlc.MediaPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val preferenceManager: com.kybers.play.data.preferences.PreferenceManager
) : AndroidViewModel(context as Application) {

    private val playerManager = PlayerManager(context as Application, viewModelScope, preferenceManager)
    
    // UI state for retry and error handling
    private val _playerStatus = MutableStateFlow(PlayerStatus.IDLE)
    val playerStatus: StateFlow<PlayerStatus> = _playerStatus.asStateFlow()
    
    private val _retryAttempt = MutableStateFlow(0)
    val retryAttempt: StateFlow<Int> = _retryAttempt.asStateFlow()
    
    private val _maxRetryAttempts = MutableStateFlow(3)
    val maxRetryAttempts: StateFlow<Int> = _maxRetryAttempts.asStateFlow()
    
    private val _retryMessage = MutableStateFlow<String?>(null)
    val retryMessage: StateFlow<String?> = _retryMessage.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        setupPlayerManager()
    }
    
    /**
     * Get the MediaPlayer instance from PlayerManager
     */
    val mediaPlayer: MediaPlayer
        get() = playerManager.getMediaPlayer()
    
    /**
     * Setup PlayerManager with retry callbacks
     */
    private fun setupPlayerManager() {
        playerManager.setRetryCallbacks(
            onRetryAttempt = { attempt, maxRetries ->
                _playerStatus.value = PlayerStatus.RETRYING
                _retryAttempt.value = attempt
                _maxRetryAttempts.value = maxRetries
                _retryMessage.value = "Reintentando... ($attempt/$maxRetries)"
                _errorMessage.value = null
            },
            onRetrySuccess = {
                _playerStatus.value = PlayerStatus.PLAYING
                _retryAttempt.value = 0
                _retryMessage.value = null
                _errorMessage.value = null
            },
            onRetryFailed = {
                _playerStatus.value = PlayerStatus.RETRY_FAILED
                _retryAttempt.value = 0
                _retryMessage.value = null
                _errorMessage.value = "Error de conexión. Verifica tu red e inténtalo de nuevo."
            },
            onError = { message ->
                _playerStatus.value = PlayerStatus.ERROR
                _errorMessage.value = message
            }
        )
    }
    
    /**
     * Play media using PlayerManager
     */
    fun playMedia(url: String) {
        android.util.Log.d("PlayerViewModel", "🎬 INICIO REPRODUCCIÓN - URL: ${url.takeLast(30)}...")
        android.util.Log.d("PlayerViewModel", "🎬 Delegando reproducción a PlayerManager...")
        _playerStatus.value = PlayerStatus.LOADING
        _errorMessage.value = null
        playerManager.playMedia(url)
        android.util.Log.d("PlayerViewModel", "🎬 Llamada a PlayerManager.playMedia() completada")
    }
    
    /**
     * Manually retry current media
     */
    fun retryPlayback() {
        _errorMessage.value = null
        playerManager.retryCurrentMedia()
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        playerManager.pause()
        _playerStatus.value = PlayerStatus.PAUSED
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        playerManager.resume()
        _playerStatus.value = PlayerStatus.PLAYING
    }
    
    /**
     * Stop playback
     */
    fun stop() {
        playerManager.stop()
        _playerStatus.value = PlayerStatus.IDLE
    }
    
    /**
     * Check if player is currently playing
     */
    fun isPlaying(): Boolean = playerManager.isPlaying()
    
    /**
     * Add lifecycle observer to PlayerManager
     */
    fun addLifecycleObserver(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(playerManager)
    }
    
    /**
     * Remove lifecycle observer from PlayerManager
     */
    fun removeLifecycleObserver(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        lifecycleOwner.lifecycle.removeObserver(playerManager)
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
