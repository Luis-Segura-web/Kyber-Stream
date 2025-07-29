package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import org.videolan.libvlc.MediaPlayer

/**
 * --- ¡ARCHIVO REFACTORIZADO Y CORREGIDO! ---
 * El "anfitrión" del reproductor. Su responsabilidad es gestionar el estado general
 * del reproductor (como la visibilidad de los controles) y proporcionar un "slot"
 * para que se puedan insertar diferentes tipos de controles (para películas, canales, etc.).
 *
 * @param mediaPlayer La instancia del MediaPlayer de VLC.
 * @param playerStatus El estado actual del reproductor (reproduciendo, pausado, etc.).
 * @param onEnterPipMode Callback para iniciar el modo Picture-in-Picture.
 * @param controls Un slot Composable que recibe el estado de visibilidad y los callbacks
 * necesarios para que los controles específicos funcionen.
 */
@Composable
fun PlayerHost(
    mediaPlayer: MediaPlayer,
    modifier: Modifier = Modifier,
    playerStatus: PlayerStatus,
    onEnterPipMode: () -> Unit,
    controls: @Composable (
        isVisible: Boolean,
        onAnyInteraction: () -> Unit,
        onRequestPipMode: () -> Unit
    ) -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pipRequest by remember { mutableStateOf(false) }

    val controlTimeoutMillis = 5000L

    // Efecto para gestionar la transición a modo Picture-in-Picture.
    // Oculta los controles suavemente antes de activar el modo PiP.
    LaunchedEffect(pipRequest) {
        if (pipRequest) {
            controlsVisible = false
            delay(300) // Espera a que la animación de ocultar termine
            onEnterPipMode()
            pipRequest = false
        }
    }

    // Efecto para ocultar los controles automáticamente después de un tiempo de inactividad.
    LaunchedEffect(controlsVisible, playerStatus, lastInteractionTime) {
        if (controlsVisible && playerStatus == PlayerStatus.PLAYING) {
            delay(controlTimeoutMillis)
            // Comprueba si ha pasado suficiente tiempo sin interacción para ocultar los controles.
            if (System.currentTimeMillis() - lastInteractionTime >= controlTimeoutMillis) {
                controlsVisible = false
            }
        }
    }

    // Callback que los controles pueden invocar para reiniciar el temporizador de ocultación.
    val resetControlTimer: () -> Unit = {
        if (!controlsVisible) controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                // Detecta un toque en cualquier parte de la pantalla para mostrar/ocultar los controles.
                detectTapGestures(onTap = {
                    controlsVisible = !controlsVisible
                    lastInteractionTime = System.currentTimeMillis()
                })
            }
    ) {
        // El reproductor de video de VLC que se muestra en el fondo.
        VLCPlayer(
            mediaPlayer = mediaPlayer,
            modifier = Modifier.fillMaxSize()
        )

        // --- ¡CORRECCIÓN DE SINTAXIS APLICADA! ---
        // Se mueve el último argumento (la lambda de los controles) fuera de los paréntesis.
        controls(
            controlsVisible,
            resetControlTimer
        ) { pipRequest = true }

        // Muestra un indicador de carga mientras el reproductor está en estado de buffering.
        AnimatedVisibility(
            visible = playerStatus == PlayerStatus.BUFFERING,
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
