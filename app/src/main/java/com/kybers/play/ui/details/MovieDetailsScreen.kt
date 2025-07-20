package com.kybers.play.ui.details

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.kybers.play.data.model.MovieWithDetails
import com.kybers.play.ui.player.MoviePlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    viewModel: MovieDetailsViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        DisposableEffect(activity) {
            val onPipModeChanged = Consumer<PictureInPictureModeChangedInfo> { info ->
                viewModel.setInPipMode(inPip = info.isInPictureInPictureMode)
            }
            activity?.addOnPictureInPictureModeChangedListener(onPipModeChanged)
            onDispose {
                activity?.removeOnPictureInPictureModeChangedListener(onPipModeChanged)
            }
        }
    }

    DisposableEffect(uiState.isPlayerVisible) {
        val window = activity?.window
        if (uiState.isPlayerVisible) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val currentWindowBrightness = window?.attributes?.screenBrightness ?: -1f
            viewModel.setInitialSystemValues(
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                brightness = currentWindowBrightness
            )
        }
        onDispose {
            window?.attributes?.let {
                it.screenBrightness = uiState.originalBrightness
                window.attributes = it
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(uiState.screenBrightness) {
        val window = activity?.window
        if (uiState.isPlayerVisible) {
            val layoutParams = window?.attributes
            layoutParams?.screenBrightness = uiState.screenBrightness
            window?.attributes = layoutParams
        }
        onDispose {}
    }

    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape != uiState.isFullScreen) {
            viewModel.onToggleFullScreen()
        }
    }

    LaunchedEffect(uiState.isFullScreen, uiState.showAudioMenu, uiState.showSubtitleMenu, uiState.showVideoMenu) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val shouldBeImmersive = uiState.isFullScreen || uiState.showAudioMenu || uiState.showSubtitleMenu || uiState.showVideoMenu

        if (shouldBeImmersive) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = uiState.isPlayerVisible) {
        if (uiState.isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        viewModel.hidePlayer()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            MoviePlayerSection(
                viewModel = viewModel,
                onPlay = { viewModel.startPlayback() },
                onToggleFullScreen = {
                    activity?.requestedOrientation = if (uiState.isFullScreen) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                },
                onNavigateUp = onNavigateUp
            )

            AnimatedVisibility(visible = !uiState.isFullScreen) {
                uiState.movieWithDetails?.let { movieWithDetails ->
                    MovieDetailsInfoSection(
                        movieWithDetails = movieWithDetails,
                        isLoadingDetails = uiState.isLoadingDetails,
                        isFavorite = uiState.isFavorite,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onPlay = { viewModel.startPlayback() }
                    )
                } ?: run {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun MoviePlayerSection(
    viewModel: MovieDetailsViewModel,
    onPlay: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    Box {
        AnimatedContent(
            targetState = uiState.isPlayerVisible,
            label = "PlayerOrPoster",
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            modifier = if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) { isPlayerVisible ->
            if (isPlayerVisible) {
                PlayerHost(
                    mediaPlayer = viewModel.mediaPlayer,
                    modifier = Modifier.fillMaxSize(),
                    playerStatus = uiState.playerStatus,
                    onEnterPipMode = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            (context as? Activity)?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                            )
                        }
                    },
                    controls = { isVisible, onAnyInteraction, onRequestPipMode ->
                        MoviePlayerControls(
                            isVisible = isVisible,
                            onAnyInteraction = onAnyInteraction,
                            onRequestPipMode = onRequestPipMode,
                            isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                            isMuted = uiState.isMuted,
                            isFavorite = uiState.isFavorite,
                            isFullScreen = uiState.isFullScreen,
                            streamTitle = uiState.movieWithDetails?.movie?.name ?: "Película",
                            systemVolume = uiState.systemVolume,
                            maxSystemVolume = uiState.maxSystemVolume,
                            screenBrightness = uiState.screenBrightness,
                            audioTracks = uiState.availableAudioTracks,
                            subtitleTracks = uiState.availableSubtitleTracks,
                            videoTracks = uiState.availableVideoTracks,
                            showAudioMenu = uiState.showAudioMenu,
                            showSubtitleMenu = uiState.showSubtitleMenu,
                            showVideoMenu = uiState.showVideoMenu,
                            currentPosition = uiState.currentPosition,
                            duration = uiState.duration,
                            onClose = {
                                if (uiState.isFullScreen) {
                                    (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                                viewModel.hidePlayer()
                            },
                            onPlayPause = viewModel::togglePlayPause,
                            onToggleMute = { viewModel.onToggleMute(audioManager) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleFullScreen = onToggleFullScreen,
                            onSetVolume = { vol -> viewModel.setSystemVolume(vol, audioManager) },
                            onSetBrightness = viewModel::setScreenBrightness,
                            onToggleAudioMenu = viewModel::toggleAudioMenu,
                            onToggleSubtitleMenu = viewModel::toggleSubtitleMenu,
                            onToggleVideoMenu = viewModel::toggleVideoMenu,
                            onSelectAudioTrack = viewModel::selectAudioTrack,
                            onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                            onSelectVideoTrack = viewModel::selectVideoTrack,
                            onToggleAspectRatio = viewModel::toggleAspectRatio,
                            onSeek = viewModel::seekTo
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    // ¡CORRECCIÓN COIL! Verificamos que la URL no sea nula antes de llamar a AsyncImage.
                    val imageUrl = uiState.movieWithDetails?.details?.backdropUrl ?: uiState.movieWithDetails?.movie?.streamIcon
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Portada de la película",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayCircleOutline,
                        contentDescription = "Reproducir",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = !uiState.isFullScreen) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MovieDetailsInfoSection(
    movieWithDetails: MovieWithDetails,
    isLoadingDetails: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    val movie = movieWithDetails.movie
    val details = movieWithDetails.details

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = movie.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Reproducir")
            }
            OutlinedButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Añadir a favoritos",
                    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Descripción",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isLoadingDetails) {
            Text(
                text = "Cargando descripción...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = details?.plot ?: "No hay descripción disponible.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
