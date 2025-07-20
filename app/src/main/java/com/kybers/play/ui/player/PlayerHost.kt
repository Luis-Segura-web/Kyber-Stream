package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
 * El "marco" o "anfitrión" del reproductor.
 *
 * @param onEnterPipMode Un callback que se ejecutará para entrar en modo PiP,
 * después de que el Host se haya encargado de ocultar los controles.
 * @param controls Un slot que define la UI de los controles. Ahora recibe un
 * callback `onRequestPipMode` para iniciar la transición.
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

    LaunchedEffect(pipRequest) {
        if (pipRequest) {
            controlsVisible = false
            delay(300)
            onEnterPipMode()
            pipRequest = false
        }
    }

    LaunchedEffect(controlsVisible, playerStatus, lastInteractionTime) {
        if (controlsVisible && playerStatus == PlayerStatus.PLAYING) {
            delay(controlTimeoutMillis)
            if (System.currentTimeMillis() - lastInteractionTime >= controlTimeoutMillis) {
                controlsVisible = false
            }
        }
    }

    val resetControlTimer: () -> Unit = {
        if (!controlsVisible) controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    controlsVisible = !controlsVisible
                    lastInteractionTime = System.currentTimeMillis()
                })
            }
    ) {
        VLCPlayer(
            mediaPlayer = mediaPlayer,
            modifier = Modifier.fillMaxSize()
        )

        // ¡CORRECCIÓN! Se eliminan los nombres de los argumentos al llamar a la función 'controls'.
        controls(
            controlsVisible,
            resetControlTimer,
            { pipRequest = true }
        )

        AnimatedVisibility(
            visible = playerStatus == PlayerStatus.BUFFERING,
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
