package com.kybers.play.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
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
//import com.kybers.play.ui.channels.VLCPlayer
import org.videolan.libvlc.Media

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel, streamUrl: String, streamTitle: String) {
    val context = LocalContext.current
    val mediaPlayer = playerViewModel.mediaPlayer
    var controlsVisible by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    fun toggleFullScreen() {
        val activity = context as? Activity ?: return
        activity.requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(streamUrl) {
        val media = Media(mediaPlayer.libVLC, Uri.parse(streamUrl))
        mediaPlayer.media = media
        mediaPlayer.play()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        VLCPlayer(
            mediaPlayer = mediaPlayer,
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )

        PlayerControls(
            mediaPlayer = mediaPlayer,
            streamTitle = streamTitle,
            isVisible = controlsVisible,
            isFullScreen = isFullScreen,
            onToggleFullScreen = ::toggleFullScreen,
            onReplay = {
                mediaPlayer.stop()
                mediaPlayer.play()
            }
        )
    }
}
