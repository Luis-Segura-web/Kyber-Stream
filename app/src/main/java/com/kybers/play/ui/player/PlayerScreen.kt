package com.kybers.play.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel, streamUrl: String) {
    val player = playerViewModel.player
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var controlsVisible by remember { mutableStateOf(true) }

    // Obtenemos la configuración actual para detectar cambios de orientación.
    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    // Función para cambiar la orientación de la pantalla.
    fun toggleFullScreen() {
        val activity = context as? Activity ?: return
        activity.requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED // Vuelve a la orientación por defecto (vertical)
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE // Pone la pantalla en horizontal
        }
    }

    LaunchedEffect(streamUrl) {
        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    controllerAutoShow = false
                    controllerHideOnTouch = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ¡CAMBIO CLAVE AQUÍ! El Box que detecta los toques y contiene los controles
        // ahora se dibuja *después* del AndroidView en el mismo Box padre.
        // Esto asegura que los eventos de toque sean capturados por esta capa.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible // Alterna la visibilidad de los controles
                        }
                    )
                }
        ) {
            // Los controles del reproductor se dibujan encima de esta capa.
            PlayerControls(
                player = player,
                controlsVisible = controlsVisible,
                onControlsVisibilityChanged = { controlsVisible = it },
                isFullScreen = isFullScreen,
                onToggleFullScreen = { toggleFullScreen() },
                channelName = null,
                onPreviousChannel = { /* No hay navegación de canal anterior aquí */ },
                onNextChannel = { /* No hay navegación de canal siguiente aquí */ },
                isTvChannel = false, // Para PlayerScreen, asumimos que no es un canal de TV a menos que se especifique
                onToggleResizeMode = { /* No hay lógica de ajuste de pantalla en esta pantalla genérica */ }
            )
        }
    }
}
