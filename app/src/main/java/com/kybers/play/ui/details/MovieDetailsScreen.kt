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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
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
import coil.request.ImageRequest
import com.kybers.play.R
import com.kybers.play.data.remote.model.FilmographyItem
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
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

    Surface(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoadingDetails && uiState.movie == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                MoviePlayerSection(
                    viewModel = viewModel,
                    audioManager = audioManager,
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
                availableItems = uiState.availableFilmography,
                unavailableItems = uiState.unavailableFilmography,
                isLoading = uiState.isActorMoviesLoading,
                onDismiss = { viewModel.onDismissActorMoviesDialog() },
                onMovieClick = { movieId ->
                    viewModel.onDismissActorMoviesDialog()
                    onNavigateToMovie(movieId)
                },
                onUnavailableItemClick = { item -> viewModel.onUnavailableItemSelected(item) }
            )
        }

        if (uiState.showUnavailableDetailsDialog) {
            UnavailableItemDetailsDialog(
                isLoading = uiState.isUnavailableItemLoading,
                details = uiState.unavailableItemDetails,
                onDismiss = { viewModel.onDismissUnavailableDetailsDialog() }
            )
        }
    }
}

@Composable
fun MoviePlayerSection(
    viewModel: MovieDetailsViewModel,
    audioManager: AudioManager,
    onToggleFullScreen: () -> Unit,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier
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
                            isVisible = isVisible,
                            onAnyInteraction = onAnyInteraction,
                            onRequestPipMode = onRequestPipMode,
                            isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                            isMuted = uiState.isMuted,
                            isFavorite = uiState.isFavorite,
                            isFullScreen = uiState.isFullScreen,
                            streamTitle = uiState.title,
                            systemVolume = uiState.systemVolume,
                            maxSystemVolume = uiState.maxSystemVolume,
                            screenBrightness = uiState.screenBrightness,
                            audioTracks = uiState.availableAudioTracks,
                            subtitleTracks = uiState.availableSubtitleTracks,
                            videoTracks = emptyList(),
                            showAudioMenu = uiState.showAudioMenu,
                            showSubtitleMenu = uiState.showSubtitleMenu,
                            showVideoMenu = false,
                            currentPosition = uiState.currentPosition,
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
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleFullScreen = onToggleFullScreen,
                            onSetVolume = { vol -> viewModel.setSystemVolume(vol, audioManager) },
                            onSetBrightness = viewModel::setScreenBrightness,
                            onToggleAudioMenu = viewModel::toggleAudioMenu,
                            onToggleSubtitleMenu = viewModel::toggleSubtitleMenu,
                            onToggleVideoMenu = {},
                            onSelectAudioTrack = viewModel::selectAudioTrack,
                            onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                            onSelectVideoTrack = {},
                            onToggleAspectRatio = viewModel::toggleAspectRatio,
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
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)))
                    Icon(
                        imageVector = Icons.Filled.PlayCircleOutline, contentDescription = "Reproducir",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { viewModel.startPlayback(false) }
                    )
                    // Botones superpuestos SOLO en el póster
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
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
}

@Composable
fun ColumnScope.MovieDetailsContent(
    uiState: MovieDetailsUiState,
    viewModel: MovieDetailsViewModel,
    onNavigateToMovie: (Int) -> Unit
) {
    Column(modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())) {
        InfoHeader(title = uiState.title, posterUrl = uiState.posterUrl, year = uiState.releaseYear, rating = uiState.rating)
        ActionButtons(
            playbackPosition = uiState.playbackPosition, isFavorite = uiState.isFavorite,
            onPlay = { viewModel.startPlayback(false) }, onContinue = { viewModel.startPlayback(true) },
            onToggleFavorite = viewModel::toggleFavorite
        )
        val plotText = if (uiState.plot.isNullOrBlank()) "Sin descripción disponible." else uiState.plot
        Text(
            text = plotText,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = if (uiState.plot.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
            color = if (uiState.plot.isNullOrBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else LocalContentColor.current,
            modifier = Modifier.padding(16.dp)
        )
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
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (year != null) {
                    Text(text = year, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (rating != null && rating > 0) {
                    val ratingOutOfFive = if (rating > 10) rating / 20 else rating / 2
                    RatingBar(rating = ratingOutOfFive, maxRating = 5)
                }
            }
        }
    }
}

@Composable
fun ActionButtons(playbackPosition: Long, isFavorite: Boolean, onPlay: () -> Unit, onContinue: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                    modifier = Modifier
                        .width(80.dp)
                        .clickable { onActorClick(member) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(member.getFullProfileUrl())
                                .crossfade(true)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .error(R.drawable.ic_person_placeholder)
                                .build(),
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = member.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                    if (!member.character.isNullOrBlank()) {
                        Text(
                            text = member.character,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                Column(modifier = Modifier
                    .width(120.dp)
                    .clickable { onMovieClick(movie) }) {
                    AsyncImage(
                        model = movie.streamIcon, contentDescription = movie.name, contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = movie.name,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
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
    availableItems: List<EnrichedActorMovie>,
    unavailableItems: List<FilmographyItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onUnavailableItemClick: (FilmographyItem) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp)) {
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
                    Box(Modifier
                        .fillMaxSize()
                        .padding(padding), contentAlignment = Alignment.Center) {
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

                        if (availableItems.isNotEmpty()) {
                            item {
                                Text("Disponible en tu servicio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(availableItems, key = { "avail-${it.movie.streamId}" }) { item ->
                                EnrichedFilmographyListItem(
                                    item = item,
                                    onClick = { onMovieClick(item.movie.streamId) }
                                )
                            }
                        }

                        if (unavailableItems.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("No Disponible", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(unavailableItems, key = { "unavail-${it.id}" }) { item ->
                                FilmographyListItem(
                                    item = item,
                                    onClick = { onUnavailableItemClick(item) }
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
fun EnrichedFilmographyListItem(
    item: EnrichedActorMovie,
    onClick: () -> Unit
) {
    val details = item.details.details
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = details?.posterUrl ?: item.movie.streamIcon,
            contentDescription = item.movie.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(80.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.movie.name,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))

            val year = details?.releaseYear
            val cert = details?.certification
            val subInfo = listOfNotNull(year, cert).joinToString(" | ")

            if (subInfo.isNotBlank()) {
                Text(
                    text = subInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (details?.rating != null && details.rating > 0) {
                val ratingOutOfFive = if (details.rating > 10) details.rating / 20 else details.rating / 2
                RatingBar(
                    rating = ratingOutOfFive,
                    maxRating = 5,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = "PELÍCULA",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun FilmographyListItem(
    item: FilmographyItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = item.getFullPosterUrl(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(80.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            val mediaTypeLabel = if (item.mediaType == "movie") "PELÍCULA" else "SERIE"
            val mediaTypeColor = if (item.mediaType == "movie") MaterialTheme.colorScheme.primary else Color(0xFFE53935)
            Text(
                text = mediaTypeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(mediaTypeColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun UnavailableItemDetailsDialog(
    isLoading: Boolean,
    details: UnavailableItemDetails?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (details != null) {
                Column {
                    Box(contentAlignment = Alignment.TopEnd) {
                        AsyncImage(
                            model = details.backdropUrl,
                            contentDescription = "Backdrop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                        Box(modifier = Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape))
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AsyncImage(
                            model = details.posterUrl,
                            contentDescription = "Póster",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(details.title ?: "Sin título", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val subInfo = listOfNotNull(details.releaseYear, details.certification).joinToString(" | ")
                            if (subInfo.isNotBlank()) {
                                Text(subInfo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        details.rating?.let {
                            if (it > 0) RatingBar(rating = it / 2, maxRating = 5, modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.CenterHorizontally))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            details.overview ?: "Sin descripción disponible.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "NO DISPONIBLE EN TU SERVICIO",
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No se pudieron cargar los detalles.", textAlign = TextAlign.Center)
                }
            }
        }
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
