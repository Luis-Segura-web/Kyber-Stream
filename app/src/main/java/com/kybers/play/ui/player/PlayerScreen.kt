package com.kybers.play.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel, streamUrl: String, streamTitle: String) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Collect retry state from ViewModel
    val playerStatus by playerViewModel.playerStatus.collectAsState()
    val retryAttempt by playerViewModel.retryAttempt.collectAsState()
    val maxRetryAttempts by playerViewModel.maxRetryAttempts.collectAsState()
    val retryMessage by playerViewModel.retryMessage.collectAsState()
    val errorMessage by playerViewModel.errorMessage.collectAsState()
    
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) }
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }
    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        // Add lifecycle observer to PlayerViewModel
        playerViewModel.addLifecycleObserver(lifecycleOwner)
        
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !isFullScreen) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val aspectRatio = Rational(16, 9)
                    val pipParams = PictureInPictureParams.Builder()
                        .setAspectRatio(aspectRatio)
                        .build()
                    activity?.enterPictureInPictureMode(pipParams)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerViewModel.removeLifecycleObserver(lifecycleOwner)
        }
    }


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

    fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity?.enterPictureInPictureMode(pipParams)
        }
    }

    val onAnyInteraction: () -> Unit = {
        controlsVisible = true
    }

    // --- FIXED MEMORY LEAK: Using PlayerManager for safe media handling ---
    LaunchedEffect(streamUrl) {
        Log.i("PlayerScreen", "=== PLAYERSCREEN INICIANDO ===")
        Log.i("PlayerScreen", "URL: " + streamUrl.takeLast(30) + "...")
        Log.i("PlayerScreen", "Llamando a playerViewModel.playMedia()...")
        playerViewModel.playMedia(streamUrl)
        Log.i("PlayerScreen", "PlayerViewModel llamado correctamente")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        PlayerHost(
            playerEngine = playerViewModel.mediaPlayer,
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            playerStatus = playerStatus,
            onEnterPipMode = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val aspectRatio = Rational(16, 9)
                    val pipParams = PictureInPictureParams.Builder()
                        .setAspectRatio(aspectRatio)
                        .build()
                    activity?.enterPictureInPictureMode(pipParams)
                }
            }
        ) { isVisible, onAnyInteraction, onRequestPipMode ->
            PlayerControls(
                modifier = Modifier.fillMaxSize(),
                isVisible = isVisible,
                isPlaying = isPlaying,
                isMuted = isMuted,
                isFavorite = false,
                isFullScreen = isFullScreen,
                streamTitle = streamTitle,
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
                onPlayPause = { /* No action */ },
                onNext = { /* No action */ },
                onPrevious = { /* No action */ },
                onToggleMute = {
                    isMuted = !isMuted
                    if (isMuted) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    } else {
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, if (currentVolume > 0) currentVolume else maxVolume / 2, 0)
                    }
                },
                onToggleFavorite = { /* No action */ },
                onToggleFullScreen = ::toggleFullScreen,
                onToggleAudioMenu = {},
                onToggleSubtitleMenu = {},
                onToggleVideoMenu = {},
                onSelectAudioTrack = {},
                onSelectSubtitleTrack = {},
                onSelectVideoTrack = {},
                onPictureInPicture = ::enterPictureInPictureMode,
                onToggleAspectRatio = { /* No action */ },
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { /* No action */ },
                onAnyInteraction = onAnyInteraction,
                // Enhanced retry parameters
                playerStatus = playerStatus,
                retryAttempt = retryAttempt,
                maxRetryAttempts = maxRetryAttempts,
                retryMessage = retryMessage,
                onRetry = { playerViewModel.retryPlayback() }
            )
        }
    }
}
