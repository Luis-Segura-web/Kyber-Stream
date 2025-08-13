package com.kybers.play.ui.movies

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.TextStyle
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
import com.kybers.play.data.remote.model.FilmographyItem
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.ui.player.MoviePlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
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
            if (uiState.isLoadingDetails) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.movie == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Película no encontrada.", modifier = Modifier.padding(16.dp))
                }
            } else {
                PlayerAndHeaderSection(
                    viewModel = viewModel,
                    audioManager = audioManager,
                    onNavigateUp = onNavigateUp
                )

                AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp) // Disminuir separación de 24dp a 16dp
                    ) {
                        item { MovieInfo(uiState = uiState, viewModel = viewModel) }

                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item { CollectionCarousel(uiState = uiState, onMovieClick = onNavigateToMovie) }

                        if (uiState.availableRecommendedMovies.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            item {
                                MovieCarousel(
                                    title = "Recomendadas",
                                    movies = uiState.availableRecommendedMovies,
                                    onMovieClick = onNavigateToMovie
                                )
                            }
                        }

                        if (uiState.availableSimilarMovies.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            item {
                                MovieCarousel(
                                    title = "Similares",
                                    movies = uiState.availableSimilarMovies,
                                    onMovieClick = onNavigateToMovie
                                )
                            }
                        }

                        // 📎 Créditos a TMDB
                        item {
                            Spacer(modifier = Modifier.height(8.dp)) // Reducir espaciado
                            TMDBCreditsSection()
                        }
                    }
                }
            }
        }
    }

    if (uiState.showActorMoviesDialog) {
        ActorMoviesDialog(
            actorName = uiState.selectedActorName,
            actorBio = uiState.selectedActorBio,
            isLoading = uiState.isActorMoviesLoading,
            availableMovies = uiState.availableFilmography,
            unavailableMovies = uiState.unavailableFilmography,
            onMovieSelected = { onNavigateToMovie(it.streamId) },
            onUnavailableMovieSelected = { viewModel.onUnavailableItemSelected(it) },
            onDismiss = { viewModel.onDismissActorMoviesDialog() }
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

@Composable
fun PlayerAndHeaderSection(
    viewModel: MovieDetailsViewModel,
    audioManager: AudioManager,
    onNavigateUp: () -> Unit
) {
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
                MoviePlayerControls(
                    isVisible = isVisible,
                    onAnyInteraction = onAnyInteraction,
                    onRequestPipMode = onRequestPipMode,
                    isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                    isMuted = uiState.isMuted,
                    isFavorite = uiState.isFavorite,
                    isFullScreen = uiState.isFullScreen,
                    streamTitle = uiState.title,
                    audioTracks = uiState.availableAudioTracks,
                    subtitleTracks = uiState.availableSubtitleTracks,
                    showAudioMenu = uiState.showAudioMenu,
                    showSubtitleMenu = uiState.showSubtitleMenu,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    showNextPreviousButtons = false,
                    onClose = {
                        if (uiState.isFullScreen) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            viewModel.hidePlayer()
                        }
                    },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = {},
                    onPrevious = {},
                    onSeekForward = viewModel::seekForward,
                    onSeekBackward = viewModel::seekBackward,
                    onToggleMute = { viewModel.onToggleMute(audioManager) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onToggleFullScreen = {
                        activity?.requestedOrientation = if (uiState.isFullScreen) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onToggleAudioMenu = viewModel::toggleAudioMenu,
                    onToggleSubtitleMenu = viewModel::toggleSubtitleMenu,
                    onSelectAudioTrack = { trackId ->
                        // Audio track selection is now handled by the PlayerCoordinator
                        // We'll need to update this logic
                    },
                    onSelectSubtitleTrack = { trackId ->
                        // Subtitle track selection is now handled by the PlayerCoordinator
                        // We'll need to update this logic
                    },
                    onToggleAspectRatio = viewModel::toggleAspectRatio,
                    onSeek = viewModel::seekTo,
                    // Add retry parameters
                    playerStatus = uiState.playerStatus,
                    retryAttempt = uiState.retryAttempt,
                    maxRetryAttempts = uiState.maxRetryAttempts,
                    retryMessage = uiState.retryMessage,
                    onRetry = viewModel::retryPlayback
                )
            }
        }
        AnimatedVisibility(
            visible = !uiState.isPlayerVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MovieHeader(
                backdropUrl = uiState.backdropUrl,
                title = uiState.title,
                isFavorite = uiState.isFavorite,
                onNavigateUp = onNavigateUp,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onPlayClick = { viewModel.startPlayback(continueFromLastPosition = false) }
            )
        }
    }
}

@Composable
fun MovieHeader(backdropUrl: String?, title: String, isFavorite: Boolean, onNavigateUp: () -> Unit, onToggleFavorite: () -> Unit, onPlayClick: () -> Unit) {
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
            contentDescription = "Backdrop de la película",
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

        // ▶️ Botón de play (centrado)
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
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
fun MovieInfo(uiState: MovieDetailsUiState, viewModel: MovieDetailsViewModel) {
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
                contentDescription = "Poster de la película",
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
                if (!uiState.releaseYear.isNullOrBlank()) {
                    Text(
                        text = "Año: ${uiState.releaseYear}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Duración si está disponible
                // Text(
                //     text = "Duración: ${uiState.duration ?: "N/A"}",
                //     style = MaterialTheme.typography.bodyMedium,
                //     color = MaterialTheme.colorScheme.onSurfaceVariant,
                //     modifier = Modifier.padding(bottom = 4.dp)
                // )
            }
        }

        // ▶️ Botones de acción (Reproducir / Continuar)
        Spacer(modifier = Modifier.height(16.dp))

        ActionButtonsSection(
            uiState = uiState,
            viewModel = viewModel,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 📖 Descripción de la película
        Spacer(modifier = Modifier.height(16.dp))

        ExpandableDescriptionSection(
            description = uiState.plot,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 🎭 Reparto principal
        if (uiState.cast.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            CastSection(cast = uiState.cast, onActorClick = { viewModel.onActorSelected(it) })
        }
    }
}

@Composable
private fun ActionButtonsSection(
    uiState: MovieDetailsUiState,
    viewModel: MovieDetailsViewModel,
    modifier: Modifier = Modifier
) {
    val hasProgress = uiState.playbackPosition > 0L

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botón principal de reproducir/continuar
        Button(
            onClick = {
                viewModel.startPlayback(continueFromLastPosition = hasProgress)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (hasProgress) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasProgress) "Continuar reproducción" else "Reproducir",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Botón secundario solo cuando hay progreso
        if (hasProgress) {
            OutlinedButton(
                onClick = { viewModel.startPlayback(continueFromLastPosition = false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reproducir desde el inicio",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RatingBar(rating: Double, maxRating: Int = 5) {
    Row {
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(member.getFullProfileUrl())
                            .crossfade(true)
                            .fallback(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .build(),
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
fun CollectionCarousel(uiState: MovieDetailsUiState, onMovieClick: (Int) -> Unit) {
    val collection = uiState.collection
    if (collection != null) {
        Column(modifier = Modifier.padding(top = 24.dp)) {
            // Evitar repetir "Colección" si ya está en el nombre
            val displayTitle = if (collection.name.contains("Colección", ignoreCase = true) ||
                                   collection.name.contains("Collection", ignoreCase = true)) {
                collection.name
            } else {
                "Colección: ${collection.name}"
            }

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Available collection movies
                items(uiState.availableCollectionMovies, key = { "available_${it.streamId}" }) { movie ->
                    EnhancedMoviePosterItem(
                        movie = movie,
                        onClick = { onMovieClick(movie.streamId) }
                    )
                }
                
                // Unavailable collection movies with "No Disponible" label
                items(uiState.unavailableCollectionMovies, key = { "unavailable_${it.id}" }) { tmdbMovie ->
                    UnavailableMoviePosterItem(
                        title = tmdbMovie.title,
                        posterPath = tmdbMovie.posterPath,
                        onClick = { /* Can't click on unavailable movies */ }
                    )
                }
            }
        }
    } else {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Colección: ", fontWeight = FontWeight.Bold)
            Text("No disponible")
        }
    }
}

@Composable
fun MovieCarousel(title: String, movies: List<Movie>, onMovieClick: (Int) -> Unit) {
    if (movies.isNotEmpty()) {
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
                items(movies, key = { it.streamId }) { movie ->
                    EnhancedMoviePosterItem(
                        movie = movie,
                        onClick = { onMovieClick(movie.streamId) }
                    )
                }
            }
        }
    }
}

@Composable
fun MoviePosterItem(posterUrl: String?, title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(posterUrl)
                .crossfade(true)
                .fallback(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Enhanced version with name and rating for recommendations/similar content
@Composable
fun EnhancedMoviePosterItem(
    movie: Movie,
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
                    .data(movie.streamIcon)
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Rating overlay at top
            if (movie.rating5Based > 0) {
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
                        text = "%.1f".format(movie.rating5Based),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Movie name at bottom - updated to match Movies/Series style
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
                    text = movie.name,
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

// Component for unavailable movies in collections
@Composable
fun UnavailableMoviePosterItem(
    title: String,
    posterPath: String?,
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
                    .data("https://image.tmdb.org/t/p/w342${posterPath}")
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Overlay to indicate unavailable status
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            
            // "No Disponible" label at center
            Card(
                modifier = Modifier.align(Alignment.Center),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "No Disponible",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // Movie name at bottom - matching style
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
                    text = title,
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
fun ActorMoviesDialog(
    actorName: String,
    actorBio: String?,
    isLoading: Boolean,
    availableMovies: List<EnrichedActorMovie>,
    unavailableMovies: List<FilmographyItem>,
    onMovieSelected: (Movie) -> Unit,
    onUnavailableMovieSelected: (FilmographyItem) -> Unit,
    onDismiss: () -> Unit
) {
    // Implementación del diálogo
}

@Composable
fun UnavailableItemDetailsDialog(
    isLoading: Boolean,
    details: UnavailableItemDetails?,
    onDismiss: () -> Unit
) {
    // Implementación del diálogo
}

@Composable
fun TMDBCreditsSection() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Créditos a TMDB",
            style = MaterialTheme.typography.titleSmall, // Smaller title
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp) // Reduced padding
        )

        Text(
            text = "Esta película utiliza datos e imágenes proporcionados por TMDb.",
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

// Función de extensión para formatear números
private fun Double.format(digits: Int) = "%.${digits}f".format(this)

/**
 * Expandable description section with "read more/less" functionality
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
