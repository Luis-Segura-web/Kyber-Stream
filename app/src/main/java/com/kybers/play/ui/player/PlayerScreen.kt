package com.kybers.play.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log // Importación necesaria para Log
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
    // State to control the visibility of player controls
    var controlsVisible by remember { mutableStateOf(true) }
    // State to track if the player is currently playing
    var isPlaying by remember { mutableStateOf(true) }
    // State to track if the player is muted
    var isMuted by remember { mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) }

    // State for system volume
    var systemVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // State for screen brightness
    var screenBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }

    // Observe orientation changes to determine fullscreen mode
    val configuration = LocalConfiguration.current
    val isFullScreen by remember(configuration) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    // States for player position and duration
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Lifecycle observer for automatic Picture-in-Picture mode.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Enter PiP mode if the activity is stopped and not already in fullscreen
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

    // Keep screen on while player is active and manage VLC event listeners
    DisposableEffect(Unit) {
        // Keep the screen from turning off
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // VLC media player event listener
        val vlcListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    Log.d("PlayerScreen", "VLC Event: Playing")
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying = false
                    Log.d("PlayerScreen", "VLC Event: Paused")
                }
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> {
                    isPlaying = false
                    Log.d("PlayerScreen", "VLC Event: EndReached/Stopped")
                }
                MediaPlayer.Event.TimeChanged -> {
                    // Update current position and duration for the progress bar
                    currentPosition = event.timeChanged
                    duration = mediaPlayer.length
                }
                MediaPlayer.Event.Buffering -> {
                    // Handle buffering state if needed, though PlayerControls already shows a spinner
                    Log.d("PlayerScreen", "VLC Event: Buffering (Progress: ${event.buffering}%)")
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e("PlayerScreen", "VLC Event: Error encountered!")
                    // Potentially show an error message to the user
                }
            }
        }
        mediaPlayer.setEventListener(vlcListener)

        onDispose {
            // Clear the KEEP_SCREEN_ON flag when the composable is disposed
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Remove the event listener to prevent memory leaks
            mediaPlayer.setEventListener(null)
            // Restore original brightness if it was set
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

    // Apply brightness changes when in fullscreen mode
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

    // Handle back button press when in fullscreen mode
    BackHandler(enabled = isFullScreen) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // Function to toggle fullscreen mode (landscape/portrait)
    fun toggleFullScreen() {
        activity?.requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        Log.d("PlayerScreen", "Toggling Fullscreen. New orientation requested.")
    }

    // Function to manually enter Picture-in-Picture mode.
    fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity?.enterPictureInPictureMode(pipParams)
            Log.d("PlayerScreen", "Entering Picture-in-Picture mode.")
        } else {
            Log.w("PlayerScreen", "Picture-in-Picture not supported on this Android version.")
        }
    }

    // Function to cycle through aspect ratio modes for the player.
    var currentAspectRatioIndex by remember { mutableIntStateOf(0) }
    val aspectRatioModes = remember { listOf("FIT_SCREEN", "FILL_SCREEN", "16:9", "4:3") }

    fun toggleAspectRatio() {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatioModes.size
        val nextMode = aspectRatioModes[currentAspectRatioIndex]
        Log.d("PlayerScreen", "Toggling Aspect Ratio to: $nextMode")

        when (nextMode) {
            "FIT_SCREEN" -> {
                mediaPlayer.setAspectRatio(null) // Resets to video's original aspect ratio
                mediaPlayer.setScale(0.0f) // 0.0f usually means "fit to screen"
            }
            "FILL_SCREEN" -> {
                mediaPlayer.setAspectRatio(null) // Resets to video's original aspect ratio
                mediaPlayer.setScale(1.0f) // 1.0f usually means "fill screen" (may crop)
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

    // Function to seek in the stream
    val onSeek: (Long) -> Unit = { position ->
        mediaPlayer.time = position
        Log.d("PlayerScreen", "Seeking to: $position ms")
    }

    // Callback for any interaction to reset the controls visibility timer
    val onAnyInteraction: () -> Unit = {
        controlsVisible = true
        // In a real scenario, you'd also reset a timer here to hide controls after inactivity.
        // For this specific PlayerScreen, the timer logic is simplified or handled externally.
    }

    // Effect to load the stream when the URL changes
    LaunchedEffect(streamUrl) {
        Log.d("PlayerScreen", "Loading stream: $streamUrl")
        val media = Media(mediaPlayer.libVLC, streamUrl.toUri())
        mediaPlayer.media = media
        mediaPlayer.play()
        // Apply initial aspect ratio. This is a workaround to ensure the initial mode is applied.
        toggleAspectRatio()
        toggleAspectRatio()
    }

    // --- UI Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Toggle controls visibility on tap
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        // VLC video player composable
        VLCPlayer(
            mediaPlayer = mediaPlayer,
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f) // Fixed aspect ratio for portrait mode
        )

        // Player controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControls(
                modifier = Modifier.fillMaxSize(),
                isVisible = true, // Always true when AnimatedVisibility shows it
                isPlaying = isPlaying,
                isMuted = isMuted,
                isFavorite = false, // Not applicable for single VOD stream
                isFullScreen = isFullScreen,
                streamTitle = streamTitle,
                systemVolume = systemVolume,
                maxSystemVolume = maxSystemVolume,
                screenBrightness = screenBrightness,
                audioTracks = emptyList(), // Not implemented for VOD in this example
                subtitleTracks = emptyList(), // Not implemented for VOD in this example
                videoTracks = emptyList(), // Not implemented for VOD in this example
                showAudioMenu = false,
                showSubtitleMenu = false,
                showVideoMenu = false,
                onClose = {
                    // If in fullscreen, exit fullscreen; otherwise, finish activity
                    if (isFullScreen) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        activity?.finish()
                    }
                },
                onPlayPause = {
                    if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                },
                onNext = { /* No action for single VOD stream */ },
                onPrevious = { /* No action for single VOD stream */ },
                onToggleMute = {
                    isMuted = !isMuted
                    audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
                    Log.d("PlayerScreen", "Mute toggled: $isMuted")
                },
                onToggleFavorite = { /* No action for single VOD stream */ },
                onToggleFullScreen = ::toggleFullScreen,
                onSetVolume = { vol ->
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                    systemVolume = vol
                    isMuted = vol == 0 // Update mute state based on volume
                    Log.d("PlayerScreen", "Volume set to: $vol")
                },
                onSetBrightness = { br ->
                    screenBrightness = br
                    Log.d("PlayerScreen", "Brightness set to: $br")
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
