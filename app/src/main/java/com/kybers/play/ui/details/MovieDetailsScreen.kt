package com.kybers.play.ui.details

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.ui.player.PlayerControls
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.VLCPlayer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    viewModel: MovieDetailsViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

    DisposableEffect(uiState.screenBrightness, uiState.isFullScreen) {
        val window = activity?.window
        val layoutParams = window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: -1f

        if (uiState.isFullScreen) {
            layoutParams?.screenBrightness = uiState.screenBrightness
            window?.attributes = layoutParams
        }

        onDispose {
            if (uiState.isFullScreen) {
                layoutParams?.screenBrightness = originalBrightness
                window?.attributes = layoutParams
            }
        }
    }

    BackHandler(enabled = uiState.isPlayerVisible) {
        if (uiState.isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            viewModel.hidePlayer()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && uiState.isPlayerVisible) {
                viewModel.hidePlayer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = !uiState.isFullScreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.movie?.name ?: "Detalles",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (!uiState.isFullScreen) paddingValues else PaddingValues(0.dp))
        ) {
            MoviePlayerSection(
                viewModel = viewModel,
                audioManager = audioManager,
                onToggleFullScreen = {
                    activity?.requestedOrientation = if (uiState.isFullScreen) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            )

            AnimatedVisibility(visible = !uiState.isFullScreen) {
                when {
                    uiState.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.movie != null -> {
                        MovieDetailsContent(
                            movie = uiState.movie!!,
                            isFavorite = uiState.isFavorite,
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onPlay = { viewModel.startPlayback(uiState.movie!!) }
                        )
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No se encontraron detalles de la película.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoviePlayerSection(
    viewModel: MovieDetailsViewModel,
    audioManager: AudioManager,
    onToggleFullScreen: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // ¡LA CORRECCIÓN! Obtenemos el contexto y la actividad aquí, en el ámbito Composable.
    val context = LocalContext.current
    val activity = context as? Activity

    val controlTimeoutMillis = 5000L

    LaunchedEffect(controlsVisible, uiState.playerStatus, lastInteractionTime) {
        if (controlsVisible && uiState.playerStatus == PlayerStatus.PLAYING) {
            delay(controlTimeoutMillis)
            if (System.currentTimeMillis() - lastInteractionTime >= controlTimeoutMillis) {
                controlsVisible = false
            }
        }
    }

    val resetControlTimer: () -> Unit = {
        if (!controlsVisible) controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    val playerModifier = if (uiState.isPlayerVisible) {
        if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    } else {
        Modifier.height(0.dp)
    }

    Box(
        modifier = playerModifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    controlsVisible = !controlsVisible
                    lastInteractionTime = System.currentTimeMillis()
                })
            }
    ) {
        if (uiState.isPlayerVisible) {
            VLCPlayer(
                mediaPlayer = viewModel.mediaPlayer,
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PlayerControls(
                    modifier = Modifier.fillMaxSize(),
                    isVisible = true,
                    isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                    isMuted = uiState.isMuted,
                    isFavorite = uiState.isFavorite,
                    isFullScreen = uiState.isFullScreen,
                    streamTitle = uiState.movie?.name ?: "Película",
                    systemVolume = uiState.systemVolume,
                    maxSystemVolume = uiState.maxSystemVolume,
                    screenBrightness = uiState.screenBrightness,
                    audioTracks = uiState.availableAudioTracks,
                    subtitleTracks = uiState.availableSubtitleTracks,
                    videoTracks = uiState.availableVideoTracks,
                    showAudioMenu = uiState.showAudioMenu,
                    showSubtitleMenu = uiState.showSubtitleMenu,
                    showVideoMenu = uiState.showVideoMenu,
                    onClose = {
                        resetControlTimer()
                        if (uiState.isFullScreen) {
                            // Usamos la variable 'activity' que ya capturamos.
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                        viewModel.hidePlayer()
                    },
                    onPlayPause = { resetControlTimer(); viewModel.togglePlayPause() },
                    onNext = { resetControlTimer() },
                    onPrevious = { resetControlTimer() },
                    onToggleMute = { resetControlTimer(); viewModel.onToggleMute(audioManager) },
                    onToggleFavorite = { resetControlTimer(); viewModel.toggleFavorite() },
                    onToggleFullScreen = { resetControlTimer(); onToggleFullScreen() },
                    onSetVolume = { vol -> resetControlTimer(); viewModel.setSystemVolume(vol, audioManager) },
                    onSetBrightness = { br -> resetControlTimer(); viewModel.setScreenBrightness(br) },
                    onToggleAudioMenu = { show -> resetControlTimer(); viewModel.toggleAudioMenu(show) },
                    onToggleSubtitleMenu = { show -> resetControlTimer(); viewModel.toggleSubtitleMenu(show) },
                    onToggleVideoMenu = { show -> resetControlTimer(); viewModel.toggleVideoMenu(show) },
                    onSelectAudioTrack = { id -> resetControlTimer(); viewModel.selectAudioTrack(id) },
                    onSelectSubtitleTrack = { id -> resetControlTimer(); viewModel.selectSubtitleTrack(id) },
                    onSelectVideoTrack = { id -> resetControlTimer(); viewModel.selectVideoTrack(id) },
                    onPictureInPicture = { },
                    onToggleAspectRatio = { resetControlTimer(); viewModel.toggleAspectRatio() },
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    onSeek = { pos -> viewModel.seekTo(pos) },
                    onAnyInteraction = resetControlTimer
                )
            }

            if (uiState.playerStatus == PlayerStatus.BUFFERING) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else if (uiState.playerStatus == PlayerStatus.ERROR) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error al cargar la película", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { uiState.movie?.let { viewModel.startPlayback(it) } }) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailsContent(
    movie: Movie,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(movie.streamIcon)
                    .crossfade(true)
                    .error(android.R.drawable.stat_notify_error)
                    .build(),
                contentDescription = "Póster de ${movie.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Reproducir")
                }

                Spacer(modifier = Modifier.height(8.dp))

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Añadir a favoritos",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Descripción", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "La información detallada de la película no está disponible a través de esta API.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
