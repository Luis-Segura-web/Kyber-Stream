package com.kybers.play.ui.series

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Series
import com.kybers.play.ui.player.MoviePlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus

/**
 * --- ¡PANTALLA COMPLETAMENTE RENOVADA! ---
 * Ahora integra el reproductor de video y su lógica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    viewModel: SeriesDetailsViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- MANEJO DEL CICLO DE VIDA Y ESTADOS DE LA VENTANA ---

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        DisposableEffect(activity) {
            val onPipModeChanged = Consumer<PictureInPictureModeChangedInfo> { info ->
                viewModel.setInPipMode(inPip = info.isInPictureInPictureMode)
            }
            activity?.addOnPictureInPictureModeChangedListener(onPipModeChanged)
            onDispose { activity?.removeOnPictureInPictureModeChangedListener(onPipModeChanged) }
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

    LaunchedEffect(uiState.isFullScreen, uiState.showAudioMenu, uiState.showSubtitleMenu) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val shouldBeImmersive = uiState.isFullScreen || uiState.showAudioMenu || uiState.showSubtitleMenu
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
        } else {
            viewModel.hidePlayer()
        }
    }

    // --- COMPOSICIÓN DE LA UI ---

    Scaffold(
        topBar = {
            // La TopAppBar solo es visible si el reproductor no está en pantalla completa o PiP
            AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                TopAppBar(
                    title = {
                        Text(
                            uiState.seriesInfo?.name ?: "Cargando...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                .padding(if (!uiState.isFullScreen && !uiState.isInPipMode) paddingValues else PaddingValues(0.dp))
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (uiState.error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else {
                // El contenido principal se divide en el reproductor y los detalles
                SeriesPlayerSection(viewModel = viewModel, audioManager = audioManager)

                AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                    uiState.seriesInfo?.let { series ->
                        SeriesDetailsContent(
                            series = series,
                            uiState = uiState,
                            onSeasonSelected = viewModel::selectSeason,
                            onPlayEpisode = { episode -> viewModel.playEpisode(episode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesPlayerSection(viewModel: SeriesDetailsViewModel, audioManager: AudioManager) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    val playerModifier = if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)

    Box(modifier = playerModifier) {
        if (uiState.isPlayerVisible) {
            PlayerHost(
                mediaPlayer = viewModel.mediaPlayer,
                modifier = Modifier.fillMaxSize(),
                playerStatus = uiState.playerStatus,
                onEnterPipMode = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                        )
                    }
                }
            ) { isVisible, onAnyInteraction, onRequestPipMode ->
                MoviePlayerControls(
                    isVisible = isVisible,
                    onAnyInteraction = onAnyInteraction,
                    onRequestPipMode = onRequestPipMode,
                    isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                    isMuted = uiState.isMuted,
                    isFavorite = false, // No aplica a episodios individuales
                    isFullScreen = uiState.isFullScreen,
                    streamTitle = uiState.currentlyPlayingEpisode?.title ?: "Episodio",
                    systemVolume = uiState.systemVolume,
                    maxSystemVolume = uiState.maxSystemVolume,
                    screenBrightness = uiState.screenBrightness,
                    audioTracks = uiState.availableAudioTracks,
                    subtitleTracks = uiState.availableSubtitleTracks,
                    showAudioMenu = uiState.showAudioMenu,
                    showSubtitleMenu = uiState.showSubtitleMenu,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    showNextPreviousButtons = true, // ¡CLAVE! Mostramos los botones de episodio
                    onClose = {
                        if (uiState.isFullScreen) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            viewModel.hidePlayer()
                        }
                    },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNextEpisode,
                    onPrevious = viewModel::playPreviousEpisode,
                    onSeekForward = {}, // No se usa
                    onSeekBackward = {}, // No se usa
                    onToggleMute = { viewModel.onToggleMute(audioManager) },
                    onToggleFavorite = { /* No aplica */ },
                    onToggleFullScreen = {
                        activity?.requestedOrientation = if (uiState.isFullScreen) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onSetVolume = { vol -> viewModel.setSystemVolume(vol, audioManager) },
                    onSetBrightness = viewModel::setScreenBrightness,
                    onToggleAudioMenu = viewModel::toggleAudioMenu,
                    onToggleSubtitleMenu = viewModel::toggleSubtitleMenu,
                    onSelectAudioTrack = viewModel::selectAudioTrack,
                    onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                    onToggleAspectRatio = viewModel::toggleAspectRatio,
                    onSeek = viewModel::seekTo
                )
            }
        } else {
            // Muestra la cabecera con el backdrop cuando el reproductor no está visible
            uiState.seriesInfo?.let {
                SeriesDetailHeader(series = it, onPlayClick = {
                    // Al hacer clic en el play grande, reproducimos el primer episodio de la temporada
                    val firstEpisode = uiState.episodesBySeason[uiState.selectedSeasonNumber]?.firstOrNull()
                    firstEpisode?.let { episode -> viewModel.playEpisode(episode) }
                })
            }
        }
    }
}


@Composable
fun SeriesDetailsContent(
    series: Series,
    uiState: SeriesDetailsUiState,
    onSeasonSelected: (Int) -> Unit,
    onPlayEpisode: (Episode) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = series.plot ?: "Sin descripción disponible.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            SeasonTabs(
                seasons = uiState.seasons,
                selectedSeasonNumber = uiState.selectedSeasonNumber,
                onSeasonSelected = onSeasonSelected
            )
        }
        val episodes = uiState.episodesBySeason[uiState.selectedSeasonNumber] ?: emptyList()
        items(episodes, key = { it.id }) { episode ->
            EpisodeListItem(episode = episode, onPlayClick = { onPlayEpisode(episode) })
        }
    }
}

@Composable
fun SeriesDetailHeader(series: Series, onPlayClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(series.backdropPath?.firstOrNull() ?: series.cover)
                .crossfade(true)
                .build(),
            contentDescription = "Backdrop de la serie",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 200f
                    )
                )
        )
        // Botón de Play grande en el centro
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "Reproducir Serie",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(80.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(series.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = "Póster de la serie",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color.Yellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%.1f / 5.0".format(series.rating5Based),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = series.releaseDate ?: "",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun SeasonTabs(
    seasons: List<com.kybers.play.data.remote.model.Season>,
    selectedSeasonNumber: Int,
    onSeasonSelected: (Int) -> Unit
) {
    val selectedTabIndex = seasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }.coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            if (selectedTabIndex < tabPositions.size) {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
                )
            }
        }
    ) {
        seasons.forEach { season ->
            Tab(
                selected = season.seasonNumber == selectedSeasonNumber,
                onClick = { onSeasonSelected(season.seasonNumber) },
                text = { Text(text = "Temporada ${season.seasonNumber}") }
            )
        }
    }
}

@Composable
fun EpisodeListItem(episode: Episode, onPlayClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = episode.episodeNum.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = episode.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.duration?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onPlayClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir episodio")
        }
    }
}
