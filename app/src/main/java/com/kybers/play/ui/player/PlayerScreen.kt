package com.kybers.play.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kybers.play.ui.channels.ChannelsUiState

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel, streamUrl: String) {
    val player = playerViewModel.player
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var controlsVisible by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    fun toggleFullScreen() {
        val activity = context as? Activity ?: return
        activity.requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        // ¡MEJORA! El Box del reproductor ahora se adapta a la pantalla completa o a 16:9
        Box(
            modifier = if (isFullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                            }
                        )
                    }
            ) {
                PlayerControls(
                    modifier = Modifier.fillMaxSize(),
                    player = player,
                    controlsVisible = controlsVisible,
                    isFullScreen = isFullScreen,
                    isTvChannel = false,
                    channelName = "Stream",
                    isFavorite = false,
                    hasSubtitles = false,
                    uiState = ChannelsUiState(),
                    onToggleFullScreen = { toggleFullScreen() },
                    onPreviousChannel = { /* No-op */ },
                    onNextChannel = { /* No-op */ },
                    onToggleFavorite = { /* No-op */ },
                    onSetResizeMode = { _, _ -> /* No-op */ },
                    onSetPlaybackSpeed = { /* No-op */ },
                    onSelectAudioTrack = { /* No-op */ },
                    onSelectSubtitle = { /* No-op */ }
                )
            }
        }
    }
}
