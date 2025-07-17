package com.kybers.play.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel, streamUrl: String, streamTitle: String) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val mediaPlayer = playerViewModel.mediaPlayer
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) }

    // State for system volume
    var systemVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // State for screen brightness
    var screenBrightness by remember { mutableStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }


    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    // --- Effects and Listeners ---

    // Keep screen on while player is active and manage listeners
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val vlcListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> isPlaying = true
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> isPlaying = false
            }
        }
        mediaPlayer.setEventListener(vlcListener)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mediaPlayer.setEventListener(null)
            // Restore original brightness
            if (originalBrightness >= 0) {
                activity?.window?.let { window ->
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = originalBrightness
                    window.attributes = layoutParams
                }
            }
        }
    }

    // Apply brightness changes when in fullscreen
    DisposableEffect(screenBrightness, isFullScreen) {
        if (isFullScreen) {
            activity?.window?.let { window ->
                val layoutParams = window.attributes
                layoutParams.screenBrightness = screenBrightness
                window.attributes = layoutParams
            }
        }
        onDispose { }
    }

    // Back handler for fullscreen mode
    BackHandler(enabled = isFullScreen) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun toggleFullScreen() {
        activity?.requestedOrientation = if (isFullScreen) {
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

    // --- UI ---

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
            systemVolume = systemVolume,
            maxSystemVolume = maxSystemVolume,
            screenBrightness = screenBrightness,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            videoTracks = emptyList(),
            showAudioMenu = false,
            showSubtitleMenu = false,
            showVideoMenu = false,
            onClose = {
                if (isFullScreen) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    activity?.finish()
                }
            },
            onPlayPause = { if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play() },
            onNext = { /* No action */ },
            onPrevious = { /* No action */ },
            onToggleMute = {
                isMuted = !isMuted
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
            },
            onToggleFavorite = { /* No action */ },
            onToggleFullScreen = ::toggleFullScreen,
            onSetVolume = { vol ->
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                systemVolume = vol
                isMuted = vol == 0
            },
            onSetBrightness = { br -> screenBrightness = br },
            onToggleAudioMenu = {},
            onToggleSubtitleMenu = {},
            onToggleVideoMenu = {},
            onSelectAudioTrack = {},
            onSelectSubtitleTrack = {},
            onSelectVideoTrack = {}
        )
    }
}

