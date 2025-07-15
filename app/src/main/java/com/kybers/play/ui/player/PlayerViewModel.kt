package com.kybers.play.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * ViewModel para gestionar la instancia de ExoPlayer.
 * Usamos AndroidViewModel para tener acceso al contexto de la aplicación,
 * que es necesario para inicializar ExoPlayer.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // La instancia de ExoPlayer que será utilizada por la UI.
    // La hacemos 'lateinit' porque la inicializaremos en el bloque init.
    lateinit var player: Player
        private set

    init {
        // ¡CORRECCIÓN CLAVE AQUÍ! Configuración de ExoPlayer simplificada y compatible con Media3.
        player = ExoPlayer.Builder(getApplication())
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build() // ¡IMPORTANTE! Eliminadas las configuraciones de setAudioSink, AudioCapabilities y setOffloadMode.
    }

    /**
     * Este método se llama automáticamente cuando el ViewModel está a punto de ser destruido.
     * Es el lugar perfecto para liberar los recursos del reproductor.
     */
    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
