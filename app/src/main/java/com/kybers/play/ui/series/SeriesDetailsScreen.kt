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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.kybers.play.R
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.ui.player.MoviePlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus
import kotlin.math.ceil
import kotlin.math.floor

/**
 * --- ¡PANTALLA FINAL CON TODAS LAS MEJORAS! ---
 * - Muestra la imagen del episodio (buscada en TMDB si es necesario).
 * - Elimina el texto de la duración de la lista.
 * - La barra de progreso se basa en la duración real del video.
 */
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
    Scaffold { paddingValues ->
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
                SeriesPlayerSection(viewModel = viewModel, audioManager = audioManager, onNavigateUp = onNavigateUp)

                AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                    SeriesDetailsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToSeries = onNavigateToSeries
                    )
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
                    isFavorite = false,
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
                    showNextPreviousButtons = true,
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
                    onSeekForward = {},
                    onSeekBackward = {},
                    onToggleMute = { viewModel.onToggleMute(audioManager) },
                    onToggleFavorite = {},
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
        }
        AnimatedVisibility(
            visible = !uiState.isPlayerVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uiState.backdropUrl ?: uiState.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Backdrop de la serie",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline, contentDescription = "Reproducir",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(80.dp).clickable {
                        val firstEpisode = uiState.episodesBySeason[uiState.selectedSeasonNumber]?.firstOrNull()
                        firstEpisode?.let { viewModel.playEpisode(it) }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesDetailsContent(
    uiState: SeriesDetailsUiState,
    viewModel: SeriesDetailsViewModel,
    onNavigateToSeries: (Int) -> Unit
) {
    val tabTitles = listOf("EPISODIOS", "INFO")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.selectedTabIndex) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.selectedTabIndex == index,
                    onClick = { viewModel.onTabSelected(index) },
                    text = { Text(text = title) }
                )
            }
        }

        when (uiState.selectedTabIndex) {
            0 -> EpisodesContent(uiState = uiState, viewModel = viewModel)
            1 -> InfoContent(uiState = uiState, onNavigateToSeries = onNavigateToSeries)
        }
    }
}

@Composable
fun EpisodesContent(uiState: SeriesDetailsUiState, viewModel: SeriesDetailsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    // --- ¡CAMBIO CLAVE! Pasamos el estado de reproducción completo ---
                    playbackState = uiState.playbackStates[episode.id],
                    onPlayClick = { viewModel.playEpisode(episode) }
                )
            }
        }
    }
}

@Composable
fun InfoContent(uiState: SeriesDetailsUiState, onNavigateToSeries: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        InfoHeader(
            title = uiState.title,
            posterUrl = uiState.posterUrl,
            year = uiState.firstAirYear,
            rating = uiState.rating,
            certification = uiState.certification
        )
        val plotText = if (uiState.plot.isNullOrBlank()) "Sin descripción disponible." else uiState.plot
        Text(
            text = plotText,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (uiState.plot.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
            color = if (uiState.plot.isNullOrBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else LocalContentColor.current,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (uiState.cast.isNotEmpty()) {
            CastSection(cast = uiState.cast, onActorClick = { /* TODO */ })
        }
        if (uiState.availableRecommendations.isNotEmpty()) {
            RecommendationsSection(
                recommendations = uiState.availableRecommendations,
                onSeriesClick = { series -> onNavigateToSeries(series.seriesId) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
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
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                Column(modifier = Modifier.width(120.dp).clickable { onSeriesClick(series) }) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(series.cover).crossfade(true).build(),
                        contentDescription = series.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = series.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
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

// --- ¡COMPONENTE DE LA LISTA DE EPISODIOS TOTALMENTE RENOVADO! ---
@Composable
fun EpisodeListItem(
    episode: Episode,
    playbackState: Pair<Long, Long>?,
    onPlayClick: () -> Unit
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
        // 1. Imagen del episodio
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(episode.imageUrl)
                .crossfade(true)
                .fallback(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .build(),
            contentDescription = "Imagen del episodio ${episode.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 2. Columna con la información y la barra de progreso
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNum}. ${episode.title}",
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )

            // Mostramos la barra de progreso solo si hay progreso visible.
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

        // 3. Botón de reproducción
        IconButton(onClick = onPlayClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir episodio")
        }
    }
}
