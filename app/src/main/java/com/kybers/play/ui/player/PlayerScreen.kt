package com.kybers.play.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    var systemVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // State for screen brightness
    var screenBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }


    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    // Observador del ciclo de vida para el modo PiP automático.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !isFullScreen) { // Entrar en PiP si no está en pantalla completa
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

    // Función para entrar en modo Picture-in-Picture manualmente.
    fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity?.enterPictureInPictureMode(pipParams)
        }
    }

    // ¡NUEVO! Función para cambiar la relación de aspecto del reproductor.
    // Como PlayerScreen es para VOD y no tiene un ViewModel de canales,
    // implementamos una lógica simple de ciclo para la relación de aspecto aquí.
    var currentAspectRatioIndex by remember { mutableIntStateOf(0) }
    val aspectRatioModes = remember { listOf("FIT_SCREEN", "FILL_SCREEN", "16:9", "4:3") }

    fun toggleAspectRatio() {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatioModes.size
        val nextMode = aspectRatioModes[currentAspectRatioIndex]

        when (nextMode) {
            "FIT_SCREEN" -> {
                mediaPlayer.setAspectRatio(null) // Restablece a la relación de aspecto original del video
                mediaPlayer.setScale(0.0f) // 0.0f suele significar "ajustar a la pantalla"
            }
            "FILL_SCREEN" -> {
                mediaPlayer.setAspectRatio(null) // Restablece a la relación de aspecto original del video
                mediaPlayer.setScale(1.0f) // 1.0f suele significar "llenar la pantalla" (puede recortar)
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

    LaunchedEffect(streamUrl) {
        val media = Media(mediaPlayer.libVLC, streamUrl.toUri())
        mediaPlayer.media = media
        mediaPlayer.play()
        // Aplicar la relación de aspecto inicial al cargar el stream.
        toggleAspectRatio() // Llama una vez para establecer el modo por defecto o el guardado (si lo hubiera).
        toggleAspectRatio() // Llama una segunda vez para ciclar al primer modo visible (FIT_SCREEN)
        // Esto es un workaround para asegurar que el modo inicial se aplique.
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
            onSelectVideoTrack = {},
            onPictureInPicture = ::enterPictureInPictureMode,
            onToggleAspectRatio = ::toggleAspectRatio // ¡CORRECCIÓN! Pasamos el callback
        )
    }
}
