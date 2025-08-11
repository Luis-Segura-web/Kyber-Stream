package com.kybers.play.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import com.kybers.play.core.player.PlayerCoordinator
import com.kybers.play.data.preferences.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observador de ciclo de vida para gestionar la reproducción según las preferencias del usuario
 * y cumplir con la política de single-connection
 */
@Singleton
class PlaybackLifecycleObserver @Inject constructor(
    private val playerCoordinator: PlayerCoordinator,
    private val preferenceManager: PreferenceManager
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "PlaybackLifecycleObserver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var stopOnBackground: Boolean = true

    init {
        // Observar cambios en las preferencias
        scope.launch {
            // Cargar configuración inicial
            stopOnBackground = preferenceManager.getStopOnBackground()
            Log.d(TAG, "Lifecycle observer initialized - stopOnBackground: $stopOnBackground")
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "Lifecycle: onStart - app visible")
        // No hacer nada específico en onStart
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d(TAG, "Lifecycle: onResume - app in foreground")
        // Actualizar configuración por si cambió
        stopOnBackground = preferenceManager.getStopOnBackground()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "Lifecycle: onPause - app pausing")
        // No pausar automáticamente - permitir que la reproducción continúe
        // a menos que sea explícitamente configurado por el usuario
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "Lifecycle: onStop - app went to background")
        
        // Detener reproducción solo si está configurado por el usuario
        if (stopOnBackground) {
            scope.launch {
                playerCoordinator.stopAll()
                Log.d(TAG, "Playback stopped due to background (user preference)")
            }
        } else {
            Log.d(TAG, "Playback continues in background (user preference)")
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.d(TAG, "Lifecycle: onDestroy - cleaning up all resources")
        
        // Siempre limpiar en destroy para evitar memory leaks
        scope.launch {
            playerCoordinator.stopAll()
            Log.d(TAG, "All playback resources released on destroy")
        }
    }

    /**
     * Actualiza la configuración de detener en background
     */
    fun updateStopOnBackgroundSetting(stop: Boolean) {
        stopOnBackground = stop
        preferenceManager.saveStopOnBackground(stop)
        Log.d(TAG, "Stop on background setting updated: $stop")
    }
}