package com.kybers.play.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

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
        // Creamos la instancia de ExoPlayer al iniciar el ViewModel.
        player = ExoPlayer.Builder(getApplication()).build()
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
