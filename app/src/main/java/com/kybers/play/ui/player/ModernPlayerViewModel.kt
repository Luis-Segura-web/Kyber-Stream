package com.kybers.play.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.kybers.play.core.player.PlayerCoordinator
import com.kybers.play.core.player.MediaSpec
import com.kybers.play.core.player.StreamingLeaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel modernizado que utiliza el nuevo sistema de PlayerCoordinator
 * con cumplimiento de single-connection policy
 */
@HiltViewModel
class ModernPlayerViewModel @Inject constructor(
    application: Application,
    private val playerCoordinator: PlayerCoordinator,
    private val leaseManager: StreamingLeaseManager
) : AndroidViewModel(application) {

    // Estados del reproductor
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        // Observar estados del coordinador y lease manager
        observePlayerStates()
    }

    /**
     * Observa los estados del sistema de reproducción
     */
    private fun observePlayerStates() {
        viewModelScope.launch {
            playerCoordinator.coordinatorState.collect { coordinatorState ->
                _uiState.value = _uiState.value.copy(
                    coordinatorState = coordinatorState
                )
            }
        }

        viewModelScope.launch {
            leaseManager.state.collect { leaseState ->
                _uiState.value = _uiState.value.copy(
                    leaseState = leaseState,
                    cooldownRemainingMs = when (leaseState) {
                        is StreamingLeaseManager.LeaseState.None -> leaseManager.getCooldownRemainingMs()
                        else -> 0L
                    }
                )
            }
        }
    }

    /**
     * Intenta reproducir un media
     */
    fun playMedia(
        url: String,
        title: String? = null,
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        referer: String? = null
    ) {
        viewModelScope.launch {
            val mediaSpec = MediaSpec(
                url = url,
                title = title,
                headers = headers,
                userAgent = userAgent,
                referer = referer
            )

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = playerCoordinator.play(mediaSpec)) {
                is PlayerCoordinator.PlayResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentMedia = mediaSpec
                    )
                }
                is PlayerCoordinator.PlayResult.LeaseUnavailable -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showLeaseDialog = true,
                        pendingMedia = mediaSpec
                    )
                }
                is PlayerCoordinator.PlayResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Fuerza la reproducción, liberando cualquier lease anterior
     */
    fun forcePlay() {
        val pendingMedia = _uiState.value.pendingMedia ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                showLeaseDialog = false,
                pendingMedia = null
            )

            when (val result = playerCoordinator.forcePlay(pendingMedia)) {
                is PlayerCoordinator.PlayResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentMedia = pendingMedia
                    )
                }
                is PlayerCoordinator.PlayResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                else -> {
                    // No debería ocurrir con forcePlay
                }
            }
        }
    }

    /**
     * Cancela la reproducción pendiente
     */
    fun cancelPendingPlay() {
        _uiState.value = _uiState.value.copy(
            showLeaseDialog = false,
            pendingMedia = null
        )
    }

    /**
     * Cambia de canal (para IPTV live)
     */
    fun switchChannel(
        url: String,
        title: String? = null,
        headers: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val mediaSpec = MediaSpec(url = url, title = title, headers = headers)
            
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = playerCoordinator.switchChannel(mediaSpec)) {
                is PlayerCoordinator.PlayResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentMedia = mediaSpec
                    )
                }
                is PlayerCoordinator.PlayResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                else -> {
                    // No debería ocurrir con switchChannel
                }
            }
        }
    }

    /**
     * Pausa la reproducción
     */
    fun pause() {
        viewModelScope.launch {
            playerCoordinator.pause()
        }
    }

    /**
     * Reanuda la reproducción
     */
    fun resume() {
        viewModelScope.launch {
            playerCoordinator.resume()
        }
    }

    /**
     * Detiene la reproducción
     */
    fun stop() {
        viewModelScope.launch {
            val currentMedia = _uiState.value.currentMedia
            if (currentMedia != null) {
                playerCoordinator.stop(currentMedia.generateOwnerId())
                _uiState.value = _uiState.value.copy(currentMedia = null)
            }
        }
    }

    /**
     * Limpia mensajes de error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Verifica si hay reproducción activa
     */
    fun isPlaying(): Boolean = playerCoordinator.isPlaying()

    override fun onCleared() {
        super.onCleared()
        // El PlayerCoordinator es singleton y se limpia en el lifecycle observer
    }
}

/**
 * Estado de la UI del reproductor
 */
data class PlayerUiState(
    val isLoading: Boolean = false,
    val currentMedia: MediaSpec? = null,
    val pendingMedia: MediaSpec? = null,
    val showLeaseDialog: Boolean = false,
    val errorMessage: String? = null,
    val coordinatorState: PlayerCoordinator.CoordinatorState = PlayerCoordinator.CoordinatorState.Idle,
    val leaseState: StreamingLeaseManager.LeaseState = StreamingLeaseManager.LeaseState.None,
    val cooldownRemainingMs: Long = 0L
)