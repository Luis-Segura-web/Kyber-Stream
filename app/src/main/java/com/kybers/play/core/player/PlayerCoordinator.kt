package com.kybers.play.core.player

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinador central que orquesta PlayerEngine y garantiza el cumplimiento
 * de la política de single-connection mediante StreamingLeaseManager
 */
@Singleton
class PlayerCoordinator @Inject constructor(
    private val selector: PlayerSelector,
    private val lease: StreamingLeaseManager
) {
    
    companion object {
        private const val TAG = "PlayerCoordinator"
    }
    
    private var engine: PlayerEngine? = null
    private var currentOwnerId: String? = null
    private var currentMediaSpec: MediaSpec? = null
    
    private val _coordinatorState = MutableStateFlow<CoordinatorState>(CoordinatorState.Idle)
    val coordinatorState: StateFlow<CoordinatorState> = _coordinatorState.asStateFlow()

    /**
     * Intenta reproducir media, respetando el lease de streaming
     * @param mediaSpec Especificación del media a reproducir
     * @return PlayResult indicando el resultado de la operación
     */
    suspend fun play(mediaSpec: MediaSpec): PlayResult {
        val ownerId = mediaSpec.generateOwnerId()
        Log.d(TAG, "Attempting to play media with ownerId: $ownerId")
        
        val acquired = lease.tryAcquire(ownerId)
        if (!acquired) {
            Log.w(TAG, "Could not acquire lease for $ownerId")
            return PlayResult.LeaseUnavailable
        }

        return try {
            stopInternal() // Garantiza que no hay overlap
            
            engine = selector.createEngine(mediaSpec)
            currentOwnerId = ownerId
            currentMediaSpec = mediaSpec
            
            _coordinatorState.value = CoordinatorState.Preparing
            
            engine!!.setMedia(mediaSpec)
            engine!!.play()
            
            _coordinatorState.value = CoordinatorState.Playing(ownerId, mediaSpec)
            Log.d(TAG, "Media playback started successfully for $ownerId")
            
            PlayResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            stopInternal()
            lease.release(ownerId)
            _coordinatorState.value = CoordinatorState.Error(e.message ?: "Error desconocido")
            PlayResult.Error(e.message ?: "Error al reproducir el contenido")
        }
    }

    /**
     * Fuerza la reproducción, liberando cualquier lease anterior
     * @param mediaSpec Especificación del media a reproducir
     */
    suspend fun forcePlay(mediaSpec: MediaSpec): PlayResult {
        val ownerId = mediaSpec.generateOwnerId()
        Log.d(TAG, "Force playing media with ownerId: $ownerId")
        
        // Liberar el lease anterior y adquirir por fuerza
        lease.forceAcquire(ownerId)
        
        return try {
            stopInternal()
            
            engine = selector.createEngine(mediaSpec)
            currentOwnerId = ownerId
            currentMediaSpec = mediaSpec
            
            _coordinatorState.value = CoordinatorState.Preparing
            
            engine!!.setMedia(mediaSpec)
            engine!!.play()
            
            _coordinatorState.value = CoordinatorState.Playing(ownerId, mediaSpec)
            Log.d(TAG, "Force playback started successfully for $ownerId")
            
            PlayResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start force playback", e)
            stopInternal()
            lease.release(ownerId)
            _coordinatorState.value = CoordinatorState.Error(e.message ?: "Error desconocido")
            PlayResult.Error(e.message ?: "Error al reproducir el contenido")
        }
    }

    /**
     * Cambia de canal con cambio "hard": libera antes de preparar el nuevo
     * @param mediaSpec Nuevo media a reproducir
     */
    suspend fun switchChannel(mediaSpec: MediaSpec): PlayResult {
        val newOwnerId = mediaSpec.generateOwnerId()
        Log.d(TAG, "Switching channel to ownerId: $newOwnerId")
        
        // Liberar el lease actual si existe
        currentOwnerId?.let { lease.release(it) }
        
        // Adquirir nuevo lease
        lease.forceAcquire(newOwnerId)
        
        return try {
            // Pausar y liberar motor actual
            engine?.pause()
            engine?.release()
            
            // Crear nuevo motor
            engine = selector.createEngine(mediaSpec)
            currentOwnerId = newOwnerId
            currentMediaSpec = mediaSpec
            
            _coordinatorState.value = CoordinatorState.Preparing
            
            engine!!.setMedia(mediaSpec)
            engine!!.play()
            
            _coordinatorState.value = CoordinatorState.Playing(newOwnerId, mediaSpec)
            Log.d(TAG, "Channel switched successfully to $newOwnerId")
            
            PlayResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch channel", e)
            stopInternal()
            lease.release(newOwnerId)
            _coordinatorState.value = CoordinatorState.Error(e.message ?: "Error desconocido")
            PlayResult.Error(e.message ?: "Error al cambiar de canal")
        }
    }

    /**
     * Pausa la reproducción actual
     */
    suspend fun pause() {
        try {
            engine?.pause()
            _coordinatorState.value = CoordinatorState.Paused
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    /**
     * Reanuda la reproducción
     */
    suspend fun resume() {
        try {
            engine?.play()
            currentOwnerId?.let { ownerId ->
                currentMediaSpec?.let { mediaSpec ->
                    _coordinatorState.value = CoordinatorState.Playing(ownerId, mediaSpec)
                }
            }
            Log.d(TAG, "Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * Detiene la reproducción y libera el lease
     * @param ownerId ID del propietario que solicita detener
     */
    suspend fun stop(ownerId: String) {
        if (currentOwnerId == ownerId) {
            Log.d(TAG, "Stopping playback for $ownerId")
            stopInternal()
            lease.release(ownerId)
            _coordinatorState.value = CoordinatorState.Idle
        } else {
            Log.w(TAG, "Stop requested by $ownerId but current owner is $currentOwnerId")
        }
    }

    /**
     * Detiene toda reproducción (limpieza del sistema)
     */
    suspend fun stopAll() {
        Log.d(TAG, "Stopping all playback (system cleanup)")
        stopInternal()
        lease.forceAcquire("system_cleanup")
        lease.release("system_cleanup")
        _coordinatorState.value = CoordinatorState.Idle
    }

    /**
     * Obtiene el motor actual si existe
     */
    fun getCurrentEngine(): PlayerEngine? = engine

    /**
     * Verifica si hay reproducción activa
     */
    fun isPlaying(): Boolean = coordinatorState.value is CoordinatorState.Playing

    /**
     * Detiene internamente sin tocar el lease
     */
    private suspend fun stopInternal() {
        try {
            engine?.release()
            engine = null
            currentOwnerId = null
            currentMediaSpec = null
            Log.d(TAG, "Internal stop completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during internal stop", e)
        }
    }

    /**
     * Estados del coordinador
     */
    sealed interface CoordinatorState {
        data object Idle : CoordinatorState
        data object Preparing : CoordinatorState
        data class Playing(val ownerId: String, val mediaSpec: MediaSpec) : CoordinatorState
        data object Paused : CoordinatorState
        data class Error(val message: String) : CoordinatorState
    }

    /**
     * Resultados de operaciones de reproducción
     */
    sealed interface PlayResult {
        data object Success : PlayResult
        data object LeaseUnavailable : PlayResult
        data class Error(val message: String) : PlayResult
    }
}