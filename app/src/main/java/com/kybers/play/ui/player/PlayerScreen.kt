package com.kybers.play.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.videolan.libvlc.Media
import androidx.core.net.toUri

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel, streamUrl: String, streamTitle: String) {
    val context = LocalContext.current
    val mediaPlayer = playerViewModel.mediaPlayer
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(100) }
    var brightness by remember { mutableFloatStateOf(0.5f) }


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
        val media = Media(mediaPlayer.libVLC, streamUrl.toUri())
        mediaPlayer.media = media
        mediaPlayer.play()
        isPlaying = true
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
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        PlayerControls(
            modifier = Modifier.fillMaxSize(),
            isVisible = controlsVisible,
            isPlaying = isPlaying,
            isMuted = isMuted,
            isFavorite = false, // Not applicable for single streams
            isFullScreen = isFullScreen,
            streamTitle = streamTitle,
            volume = volume,
            brightness = brightness,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            videoTracks = emptyList(),
            showAudioMenu = false,
            showSubtitleMenu = false,
            showVideoMenu = false,
            onClose = { (context as? Activity)?.finish() },
            onPlayPause = {
                if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                isPlaying = mediaPlayer.isPlaying
            },
            onNext = { /* No action */ },
            onPrevious = { /* No action */ },
            onToggleMute = {
                isMuted = !isMuted
                mediaPlayer.volume = if (isMuted) 0 else volume
            },
            onToggleFavorite = { /* No action */ },
            onToggleFullScreen = ::toggleFullScreen,
            onSetVolume = { vol ->
                volume = vol
                mediaPlayer.volume = vol
                isMuted = vol == 0
            },
            onSetBrightness = { br ->
                brightness = br
                val activity = context as? Activity
                val window = activity?.window
                val layoutParams = window?.attributes
                layoutParams?.screenBrightness = br
                window?.attributes = layoutParams
            },
            onToggleAudioMenu = {},
            onToggleSubtitleMenu = {},
            onToggleVideoMenu = {},
            onSelectAudioTrack = {},
            onSelectSubtitleTrack = {},
            onSelectVideoTrack = {}
        )
    }
}
