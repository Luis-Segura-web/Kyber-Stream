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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
// --- ¡CORRECCIÓN! Se añade la importación que faltaba ---
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.R
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.ui.player.UniversalPlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    viewModel: SeriesDetailsViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToSeries: (Int) -> Unit
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
        }
    }
    BackHandler(enabled = uiState.isPlayerVisible) {
        if (uiState.isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            viewModel.hidePlayer()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (!uiState.isFullScreen && !uiState.isInPipMode) paddingValues else PaddingValues(0.dp))
        ) {
            val errorMessage = uiState.error
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (errorMessage != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else {
                SeriesPlayerSection(viewModel = viewModel, audioManager = audioManager, onNavigateUp = onNavigateUp)

                AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { SeriesInfo(uiState = uiState, viewModel = viewModel) }

                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item { 
                            Text(
                                text = "Episodios",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        item {
                            SeasonTabs(
                                seasons = uiState.seasons,
                                selectedSeasonNumber = uiState.selectedSeasonNumber,
                                onSeasonSelected = viewModel::selectSeason
                            )
                        }

                        val episodes = uiState.episodesBySeason[uiState.selectedSeasonNumber] ?: emptyList()
                        if (episodes.isEmpty() && !uiState.isLoading) {
                            item {
                                Text(
                                    "No hay episodios para esta temporada.",
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            items(episodes, key = { it.id }) { episode ->
                                EpisodeListItem(
                                    episode = episode,
                                    playbackState = uiState.playbackStates[episode.id],
                                    onPlayClick = { viewModel.playEpisode(episode) },
                                    isImageLoading = uiState.isLoadingImages && episode.imageUrl.isNullOrBlank()
                                )
                            }
                        }

                        if (uiState.availableRecommendations.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            item {
                                SeriesCarousel(
                                    title = "Series Similares",
                                    series = uiState.availableRecommendations,
                                    onSeriesClick = onNavigateToSeries
                                )
                            }
                        }

                        // 📎 Créditos a TMDB
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            TMDBCreditsSectionSeries()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesPlayerSection(viewModel: SeriesDetailsViewModel, audioManager: AudioManager, onNavigateUp: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    val playerModifier = if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)

    Box(modifier = playerModifier) {
        AnimatedVisibility(
            visible = uiState.isPlayerVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerHost(
                playerEngine = viewModel.playerCoordinator.getCurrentEngine(),
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
                UniversalPlayerControls(
                    isVisible = isVisible,
                    onAnyInteraction = onAnyInteraction,
                    onRequestPipMode = onRequestPipMode,
                    isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                    isMuted = uiState.isMuted,
                    isFavorite = uiState.isFavorite,
                    isFullScreen = uiState.isFullScreen,
                    streamTitle = uiState.currentlyPlayingEpisode?.title ?: "Episodio",
                    audioTracks = uiState.availableAudioTracks,
                    subtitleTracks = uiState.availableSubtitleTracks,
                    showAudioMenu = uiState.showAudioMenu,
                    showSubtitleMenu = uiState.showSubtitleMenu,
                    onToggleAudioMenu = viewModel::toggleAudioMenu,
                    onToggleSubtitleMenu = viewModel::toggleSubtitleMenu,
                    onSelectAudioTrack = viewModel::selectAudioTrack,
                    onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    showSeekBar = true,
                    showNextPrevious = true,
                    showSeekJumps = false,
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
                    onSeekForward = { /* salto +10s no mostrado en series cuando hay next/prev */ },
                    onSeekBackward = { /* salto -10s no mostrado en series cuando hay next/prev */ },
                    onToggleMute = { viewModel.onToggleMute(audioManager) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onToggleFullScreen = {
                        activity?.requestedOrientation = if (uiState.isFullScreen) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onToggleAspectRatio = viewModel::toggleAspectRatio,
                    onSeek = viewModel::seekTo,
                    // Add retry parameters
                    playerStatus = uiState.playerStatus,
                    retryAttempt = uiState.retryAttempt,
                    maxRetryAttempts = uiState.maxRetryAttempts,
                    retryMessage = uiState.retryMessage,
                    onRetry = viewModel::retryCurrentEpisode
                )
            }
        }
        AnimatedVisibility(
            visible = !uiState.isPlayerVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SeriesHeader(
                backdropUrl = uiState.backdropUrl,
                title = uiState.title,
                isFavorite = uiState.isFavorite,
                onNavigateUp = onNavigateUp,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onPlayClick = { viewModel.continueWatching() },
                lastWatchedEpisode = viewModel.getLastWatchedEpisode()
            )
        }
    }
}

@Composable
fun SeriesHeader(
    backdropUrl: String?, 
    title: String, 
    isFavorite: Boolean, 
    onNavigateUp: () -> Unit, 
    onToggleFavorite: () -> Unit, 
    onPlayClick: () -> Unit,
    lastWatchedEpisode: Episode?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Altura fija para evitar cambios de layout
            .background(Color.Black)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Backdrop de la serie",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradiente para mejorar legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 0f,
                        endY = 300.dp.value * 3f
                    )
                )
        )

        // 🔙 Botón de retroceso (arriba izquierda)
        IconButton(
            onClick = onNavigateUp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = Color.White
            )
        }

        // ❤️ Botón de favorito (arriba derecha)
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorito",
                tint = Color.White
            )
        }

        // ▶️ Botón de play (centrado) con lógica de continuar viendo
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = if (lastWatchedEpisode != null) "Continuar viendo" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
            }

            if (lastWatchedEpisode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Continuar viendo",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Ep. ${lastWatchedEpisode.episodeNum}: ${lastWatchedEpisode.title}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 🏷️ Título (abajo izquierda, con sombra para legibilidad)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 28.sp
            )
        }
    }
}

@Composable
fun SeriesInfo(uiState: SeriesDetailsUiState, viewModel: SeriesDetailsViewModel) {
    Column {
        // 🎞️ Sección: Miniatura + Metadatos rápidos
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 📸 Miniatura (poster pequeño, tamaño fijo)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Poster de la serie",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp) // Altura fija para evitar cambios de layout
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Column con metadatos rápidos
            Column(modifier = Modifier.weight(1f)) {
                // 🎬 Título (otra vez, por claridad)
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // ⭐ Calificación
                if (uiState.rating != null && uiState.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingBar(rating = uiState.rating / 2, maxRating = 5)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(uiState.rating / 2).format(1)}/5",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 🗓️ Año y otros metadatos
                if (!uiState.firstAirYear.isNullOrBlank()) {
                    Text(
                        text = "Año: ${uiState.firstAirYear}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Certificación si está disponible
                if (!uiState.certification.isNullOrBlank()) {
                    Text(
                        text = "Clasificación: ${uiState.certification}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        // ▶️ Botones de acción (Reproducir / Continuar)
        Spacer(modifier = Modifier.height(16.dp))

        SeriesActionButtonsSection(
            uiState = uiState,
            viewModel = viewModel,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 📖 Descripción de la serie
        Spacer(modifier = Modifier.height(16.dp))

        ExpandableDescriptionSection(
            description = uiState.plot,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 🎭 Reparto principal
        if (uiState.cast.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            CastSection(cast = uiState.cast, onActorClick = { /* TODO: Handle actor click */ })
        }
    }
}

@Composable
private fun SeriesActionButtonsSection(
    uiState: SeriesDetailsUiState,
    viewModel: SeriesDetailsViewModel,
    modifier: Modifier = Modifier
) {
    val continueEpisode = viewModel.getContinueWatchingEpisode()
    val lastWatchedEpisode = viewModel.getLastWatchedEpisode()
    val allEpisodes = uiState.episodesBySeason.values.flatten()
    val firstEpisode = allEpisodes.minWithOrNull(compareBy<Episode>({ it.season }, { it.episodeNum }))
    val hasProgress = continueEpisode != null && continueEpisode != firstEpisode

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botón principal de reproducir/continuar
        Button(
            onClick = {
                viewModel.continueWatching()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasProgress) "Continuar viendo" else "Comenzar serie",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Botón para reiniciar la serie
        if (hasProgress) {
            OutlinedButton(
                onClick = {
                    firstEpisode?.let { viewModel.playEpisode(it) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Empezar desde el inicio",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            lastWatchedEpisode?.let { episode ->
                Text(
                    text = "Último episodio visto: Ep. ${episode.episodeNum} - ${episode.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SeriesCarousel(title: String, series: List<Series>, onSeriesClick: (Int) -> Unit) {
    if (series.isNotEmpty()) {
        Column(modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(series, key = { it.seriesId }) { seriesItem ->
                    EnhancedSeriesPosterItem(
                        series = seriesItem,
                        onClick = { onSeriesClick(seriesItem.seriesId) }
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedSeriesPosterItem(
    series: Series,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(series.cover)
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Rating overlay at top
            if (series.rating5Based > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Calificación",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "%.1f".format(series.rating5Based),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Series name at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(top = 16.dp, bottom = 8.dp, start = 6.dp, end = 6.dp)
            ) {
                Text(
                    text = series.name,
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 12.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TMDBCreditsSectionSeries() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Créditos a TMDB",
            style = MaterialTheme.typography.titleSmall, // Smaller title
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp) // Reduced padding
        )

        Text(
            text = "Esta serie utiliza datos e imágenes proporcionados por TMDb.",
            style = MaterialTheme.typography.bodySmall, // Smaller text
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        OutlinedButton(
            onClick = { /* Open TMDb link */ },
            modifier = Modifier.padding(top = 4.dp) // Reduced padding
        ) {
            Text(
                text = "Ir a TMDb",
                style = MaterialTheme.typography.labelSmall, // Smaller button text
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Extension function for formatting numbers  
private fun Double.format(digits: Int) = "%.${digits}f".format(this)

/**
 * Expandable description section with "read more/less" functionality for series
 */
@Composable
fun ExpandableDescriptionSection(
    description: String?,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val descriptionText = if (description.isNullOrBlank()) {
        "Sin descripción disponible."
    } else {
        description
    }
    
    val isPlaceholder = description.isNullOrBlank()
    val shouldShowToggle = !isPlaceholder && description.length > 150
    
    Column(modifier = modifier) {
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (isPlaceholder) FontStyle.Italic else FontStyle.Normal,
            color = if (isPlaceholder) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            } else {
                LocalContentColor.current
            },
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp
        )
        
        if (shouldShowToggle) {
            Text(
                text = if (isExpanded) "Leer menos" else "Leer más",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { isExpanded = !isExpanded }
            )
        }
    }
}

@Composable
fun InfoHeader(title: String, posterUrl: String?, year: String?, rating: Double?, certification: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(posterUrl).crossfade(true).build(),
            contentDescription = "Poster de la serie", contentScale = ContentScale.Crop,
            modifier = Modifier.width(100.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Title with translucent background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!year.isNullOrBlank()) {
                    Text(text = year, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!certification.isNullOrBlank()) {
                    Text(text = certification, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (rating != null && rating > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                RatingBar(rating = rating / 2, maxRating = 5)
            }
        }
    }
}

@Composable
fun RatingBar(rating: Double, maxRating: Int = 5, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        val fullStars = floor(rating).toInt()
        val halfStar = ceil(rating) > rating && (rating - fullStars) > 0.25
        val emptyStars = maxRating - fullStars - if (halfStar) 1 else 0
        repeat(fullStars) { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)) }
        if (halfStar) { Icon(Icons.AutoMirrored.Filled.StarHalf, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)) }
        repeat(emptyStars) { Icon(Icons.Filled.StarBorder, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun CastSection(cast: List<TMDbCastMember>, onActorClick: (TMDbCastMember) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Reparto Principal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cast) { member ->
                Column(
                    modifier = Modifier.width(80.dp).clickable { onActorClick(member) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(member.getFullProfileUrl()).crossfade(true).fallback(R.drawable.ic_person_placeholder).error(R.drawable.ic_person_placeholder).placeholder(R.drawable.ic_person_placeholder).build(),
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = member.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 14.sp)
                    if (!member.character.isNullOrBlank()) {
                        Text(text = member.character, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationsSection(recommendations: List<Series>, onSeriesClick: (Series) -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text("Series Similares", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recommendations, key = { it.seriesId }) { series ->
                Card(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { onSeriesClick(series) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(series.cover).crossfade(true).build(),
                            contentDescription = series.name, 
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(140.dp)
                                .aspectRatio(2f / 3f)
                        )
                        
                        // Rating overlay at top
                        if (series.rating5Based > 0) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Calificación",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "%.1f".format(series.rating5Based),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // Series name at bottom - updated to match Movies/Series style
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Black.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                                .padding(top = 16.dp, bottom = 8.dp, start = 6.dp, end = 6.dp)
                        ) {
                            Text(
                                text = series.name,
                                color = Color.White,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 12.sp,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                TabRowDefaults.PrimaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]))
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
fun EpisodeListItem(
    episode: Episode,
    playbackState: Pair<Long, Long>?,
    onPlayClick: () -> Unit,
    isImageLoading: Boolean = false
) {
    val position = playbackState?.first ?: 0L
    val duration = playbackState?.second ?: 0L

    val progress = if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(episode.imageUrl)
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = "Imagen del episodio ${episode.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Mostrar indicador de carga sobre la imagen si es necesario
            if (isImageLoading && episode.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNum}. ${episode.title}",
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )

            if (progress > 0.01f) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (progress > 0.95f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            }
        }

        IconButton(onClick = onPlayClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir episodio")
        }
    }
}
