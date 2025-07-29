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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    var systemVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var screenBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }
    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
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
        }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val vlcListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> isPlaying = true
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.TimeChanged -> {
                    currentPosition = event.timeChanged
                    duration = mediaPlayer.length
                }
                MediaPlayer.Event.Buffering -> Log.d("PlayerScreen", "VLC Event: Buffering (Progress: ${event.buffering}%)")
                MediaPlayer.Event.EncounteredError -> Log.e("PlayerScreen", "VLC Event: Error encountered!")
            }
        }
        mediaPlayer.setEventListener(vlcListener)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mediaPlayer.setEventListener(null)
            if (originalBrightness >= 0) {
                activity?.window?.let { window ->
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = originalBrightness
                    window.attributes = layoutParams
                }
            }
            Log.d("PlayerScreen", "DisposableEffect: Player resources released.")
        }
    }

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

    var currentAspectRatioIndex by remember { mutableIntStateOf(0) }
    val aspectRatioModes = remember { listOf("FIT_SCREEN", "FILL_SCREEN", "16:9", "4:3") }

    fun toggleAspectRatio() {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatioModes.size
        val nextMode = aspectRatioModes[currentAspectRatioIndex]
        when (nextMode) {
            "FIT_SCREEN" -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(0.0f)
            }
            "FILL_SCREEN" -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(1.0f)
            }
            "16:9" -> {
                mediaPlayer.setAspectRatio("16:9")
                mediaPlayer.setScale(0.0f)
            }
            "4:3" -> {
                mediaPlayer.setAspectRatio("4:3")
                mediaPlayer.setScale(0.0f)
            }
        }
    }

    val onSeek: (Long) -> Unit = { position ->
        mediaPlayer.time = position
    }

    val onAnyInteraction: () -> Unit = {
        controlsVisible = true
    }

    // --- ¡CORRECCIÓN DE FUGA DE MEMORIA! ---
    LaunchedEffect(streamUrl) {
        Log.d("PlayerScreen", "Loading stream: $streamUrl")
        // Liberamos el 'Media' anterior antes de crear y asignar uno nuevo.
        mediaPlayer.media?.release()
        val media = Media(mediaPlayer.libVLC, streamUrl.toUri())
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
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControls(
                modifier = Modifier.fillMaxSize(),
                isVisible = true,
                isPlaying = isPlaying,
                isMuted = isMuted,
                isFavorite = false,
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
                onPlayPause = {
                    if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                },
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
                onSetBrightness = { br ->
                    screenBrightness = br
                },
                onToggleAudioMenu = {},
                onToggleSubtitleMenu = {},
                onToggleVideoMenu = {},
                onSelectAudioTrack = {},
                onSelectSubtitleTrack = {},
                onSelectVideoTrack = {},
                onPictureInPicture = ::enterPictureInPictureMode,
                onToggleAspectRatio = ::toggleAspectRatio,
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek,
                onAnyInteraction = onAnyInteraction
            )
        }
    }
}
