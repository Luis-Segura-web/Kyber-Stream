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
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.kybers.play.ui.player.MoviePlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun MovieDetailsScreen(
    viewModel: MovieDetailsViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToMovie: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { movieId ->
            onNavigateToMovie(movieId)
        }
    }

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
        } else {
            viewModel.hidePlayer()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoadingDetails && uiState.movie == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                MoviePlayerSection(
                    viewModel = viewModel,
                    onToggleFullScreen = {
                        activity?.requestedOrientation = if (uiState.isFullScreen) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onNavigateUp = onNavigateUp
                )
                AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                    MovieDetailsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToMovie = onNavigateToMovie
                    )
                }
            }
        }
        if (uiState.showActorMoviesDialog) {
            ActorFilmographyDialog(
                actorName = uiState.selectedActorName,
                biography = uiState.selectedActorBio,
                availableMovies = uiState.availableActorMovies,
                unavailableMovies = uiState.unavailableActorMovies,
                isLoading = uiState.isActorMoviesLoading,
                onDismiss = { viewModel.onDismissActorMoviesDialog() },
                onMovieClick = { movieId ->
                    viewModel.onDismissActorMoviesDialog()
                    onNavigateToMovie(movieId)
                }
            )
        }
    }
}

@Composable
fun MoviePlayerSection(viewModel: MovieDetailsViewModel, onToggleFullScreen: () -> Unit, onNavigateUp: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    Box(
        modifier = if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        AnimatedContent(
            targetState = uiState.isPlayerVisible,
            label = "PlayerOrPoster",
            transitionSpec = { fadeIn() togetherWith fadeOut() }
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
                            isVisible = isVisible, onAnyInteraction = onAnyInteraction,
                            onRequestPipMode = onRequestPipMode, isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                            isMuted = uiState.isMuted, isFavorite = uiState.isFavorite,
                            isFullScreen = uiState.isFullScreen, streamTitle = uiState.title,
                            systemVolume = uiState.systemVolume, maxSystemVolume = uiState.maxSystemVolume,
                            screenBrightness = uiState.screenBrightness, audioTracks = uiState.availableAudioTracks,
                            subtitleTracks = uiState.availableSubtitleTracks, videoTracks = uiState.availableVideoTracks,
                            showAudioMenu = uiState.showAudioMenu, showSubtitleMenu = uiState.showSubtitleMenu,
                            showVideoMenu = uiState.showVideoMenu, currentPosition = uiState.currentPosition,
                            duration = uiState.duration,
                            onClose = {
                                if (uiState.isFullScreen) {
                                    (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    viewModel.hidePlayer()
                                }
                            },
                            onPlayPause = viewModel::togglePlayPause,
                            onToggleMute = { viewModel.onToggleMute(audioManager) },
                            onToggleFavorite = viewModel::toggleFavorite, onToggleFullScreen = onToggleFullScreen,
                            onSetVolume = { vol -> viewModel.setSystemVolume(vol, audioManager) },
                            onSetBrightness = viewModel::setScreenBrightness, onToggleAudioMenu = viewModel::toggleAudioMenu,
                            onToggleSubtitleMenu = viewModel::toggleSubtitleMenu, onToggleVideoMenu = viewModel::toggleVideoMenu,
                            onSelectAudioTrack = viewModel::selectAudioTrack, onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                            onSelectVideoTrack = viewModel::selectVideoTrack, onToggleAspectRatio = viewModel::toggleAspectRatio,
                            onSeek = viewModel::seekTo
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = uiState.backdropUrl ?: uiState.posterUrl,
                        contentDescription = "Backdrop de la película",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                    Icon(
                        imageVector = Icons.Filled.PlayCircleOutline, contentDescription = "Reproducir",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(80.dp).clickable { viewModel.startPlayback(false) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !uiState.isPlayerVisible && !uiState.isFullScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
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
fun ColumnScope.MovieDetailsContent(uiState: MovieDetailsUiState, viewModel: MovieDetailsViewModel, onNavigateToMovie: (Int) -> Unit) {
    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        InfoHeader(title = uiState.title, posterUrl = uiState.posterUrl, year = uiState.releaseYear, rating = uiState.rating)
        ActionButtons(
            playbackPosition = uiState.playbackPosition, isFavorite = uiState.isFavorite,
            onPlay = { viewModel.startPlayback(false) }, onContinue = { viewModel.startPlayback(true) },
            onToggleFavorite = viewModel::toggleFavorite
        )
        Text(text = uiState.plot ?: "Cargando descripción...", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        if (uiState.cast.isNotEmpty()) {
            CastSection(cast = uiState.cast, onActorClick = { actor -> viewModel.onActorSelected(actor) })
        }
        if (uiState.availableRecommendations.isNotEmpty()) {
            RecommendationsSection(
                recommendations = uiState.availableRecommendations,
                onMovieClick = { movie -> viewModel.onRecommendationSelected(movie) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun InfoHeader(title: String, posterUrl: String?, year: String?, rating: Double?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = posterUrl, contentDescription = "Poster de la película", contentScale = ContentScale.Crop,
            modifier = Modifier.width(100.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            if (year != null) {
                Text(text = "Año: $year", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rating != null && rating > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                RatingBar(rating = rating / 2, maxRating = 5)
            }
        }
    }
}

@Composable
fun ActionButtons(playbackPosition: Long, isFavorite: Boolean, onPlay: () -> Unit, onContinue: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playbackPosition > 10000) {
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Continuar (${formatTime(playbackPosition)})")
            }
        } else {
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Reproducir")
            }
        }
        OutlinedButton(
            onClick = onToggleFavorite, modifier = Modifier.size(50.dp),
            shape = CircleShape, contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Añadir a favoritos",
                tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun RatingBar(rating: Double, maxRating: Int = 5) {
    Row {
        val fullStars = floor(rating).toInt()
        val halfStar = ceil(rating) > rating && (rating - fullStars) > 0.25
        val emptyStars = maxRating - fullStars - if (halfStar) 1 else 0
        repeat(fullStars) { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107)) }
        if (halfStar) { Icon(Icons.Filled.StarHalf, contentDescription = null, tint = Color(0xFFFFC107)) }
        repeat(emptyStars) { Icon(Icons.Filled.StarBorder, contentDescription = null, tint = Color(0xFFFFC107)) }
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
                        model = member.getFullProfileUrl(), contentDescription = member.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = member.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun RecommendationsSection(recommendations: List<Movie>, onMovieClick: (Movie) -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text("Recomendaciones Disponibles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recommendations, key = { it.streamId }) { movie ->
                Column(modifier = Modifier.width(120.dp).clickable { onMovieClick(movie) }) {
                    AsyncImage(
                        model = movie.streamIcon, contentDescription = movie.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = movie.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorFilmographyDialog(
    actorName: String,
    biography: String?,
    availableMovies: List<Movie>,
    unavailableMovies: List<TMDbMovieResult>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onMovieClick: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(actorName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        }
                    )
                }
            ) { padding ->
                if (isLoading) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
                        if (!biography.isNullOrBlank()) {
                            item {
                                BiographySection(biography = biography)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (availableMovies.isNotEmpty()) {
                            item {
                                Text("Películas Disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(availableMovies, key = { "avail-${it.streamId}" }) { movie ->
                                MovieListItem(
                                    title = movie.name,
                                    posterUrl = movie.streamIcon,
                                    onClick = { onMovieClick(movie.streamId) }
                                )
                            }
                        }

                        if (unavailableMovies.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Otras Películas (No disponibles)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(unavailableMovies, key = { "unavail-${it.id}" }) { movie ->
                                MovieListItem(
                                    title = movie.title ?: "Título no disponible",
                                    posterUrl = movie.getFullPosterUrl(),
                                    onClick = { /* No hacer nada */ },
                                    isAvailable = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BiographySection(biography: String) {
    var isExpanded by remember { mutableStateOf(false) }
    val collapsedMaxLines = 4

    Column {
        Text(
            text = biography,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (biography.lines().size > collapsedMaxLines) {
            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text(if (isExpanded) "Leer menos" else "Leer más")
            }
        }
    }
}

@Composable
fun MovieListItem(title: String, posterUrl: String?, onClick: () -> Unit, isAvailable: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isAvailable, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(60.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isAvailable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.5f)
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return ""
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
