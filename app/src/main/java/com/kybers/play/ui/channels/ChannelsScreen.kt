package com.kybers.play.ui.channels

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.ui.player.PlayerControls
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    onFullScreenToggled: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var isFullScreen by remember { mutableStateOf(false) }

    DisposableEffect(uiState.isPlayerVisible) {
        val window = activity?.window
        if (uiState.isPlayerVisible) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(isFullScreen, uiState.isPlayerVisible) {
        onFullScreenToggled(isFullScreen)
        activity?.let {
            val window = it.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                it.requestedOrientation = if (!uiState.isPlayerVisible) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = uiState.isPlayerVisible) {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            viewModel.hidePlayer()
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen && !uiState.isPlayerVisible) {
                TopAppBar(
                    title = { Text("Canales") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PlayerSection(
                viewModel = viewModel,
                isFullScreen = isFullScreen,
                onToggleFullScreen = { isFullScreen = !isFullScreen }
            )

            if (!isFullScreen) {
                ChannelListSection(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PlayerSection(
    viewModel: ChannelsViewModel,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    Box {
        AnimatedVisibility(visible = uiState.isPlayerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = viewModel.player
                            useController = false
                        }
                    },
                    update = { view ->
                        view.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )

                PlayerControls(
                    modifier = Modifier.fillMaxSize(),
                    player = viewModel.player,
                    controlsVisible = controlsVisible,
                    isFullScreen = isFullScreen,
                    isTvChannel = uiState.isCurrentlyPlayingTvChannel,
                    channelName = uiState.currentlyPlaying?.name,
                    isFavorite = uiState.currentlyPlaying?.streamId.toString() in uiState.favoriteChannelIds,
                    hasSubtitles = uiState.hasSubtitles,
                    uiState = uiState,
                    onToggleFullScreen = onToggleFullScreen,
                    onPreviousChannel = viewModel::playPreviousChannel,
                    onNextChannel = viewModel::playNextChannel,
                    onToggleFavorite = {
                        uiState.currentlyPlaying?.streamId?.toString()?.let(viewModel::toggleFavorite)
                    },
                    onSetResizeMode = { mode, name ->
                        resizeMode = mode
                        viewModel.showPlayerToast("Ajuste: $name")
                    },
                    onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
                    onSelectAudioTrack = viewModel::selectAudioTrack,
                    onSelectSubtitle = viewModel::selectSubtitle
                )

                AnimatedVisibility(
                    visible = uiState.playerToastMessage != null,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = uiState.playerToastMessage ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListSection(viewModel: ChannelsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()

    OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = { viewModel.onSearchQueryChanged(it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar canales...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") }
    )

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        stickyHeader {
            CategoryHeader(
                categoryName = "Favoritos",
                isExpanded = uiState.isFavoritesCategoryExpanded,
                isSelected = uiState.currentlyPlaying?.let { it.streamId.toString() in uiState.favoriteChannelIds } ?: false,
                onHeaderClick = { viewModel.onFavoritesCategoryToggled() }
            )
        }

        if (uiState.isFavoritesCategoryExpanded) {
            val favoriteChannels = viewModel.getFavoriteChannels()
            if (favoriteChannels.isEmpty()) {
                item {
                    Text(
                        text = "Aún no tienes canales favoritos.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(favoriteChannels, key = { "fav-${it.streamId}" }) { channel ->
                    ChannelListItem(
                        channel = channel,
                        isSelected = channel.streamId == uiState.currentlyPlaying?.streamId,
                        onChannelClick = { viewModel.onChannelSelected(channel.copy(categoryId = "favorites")) },
                        isFavorite = true,
                        onToggleFavorite = { viewModel.toggleFavorite(it.streamId.toString()) }
                    )
                }
            }
        }

        uiState.categories.forEach { expandableCategory ->
            stickyHeader {
                CategoryHeader(
                    categoryName = expandableCategory.category.categoryName,
                    isExpanded = expandableCategory.isExpanded,
                    isSelected = expandableCategory.category.categoryId == uiState.currentlyPlayingCategoryId,
                    onHeaderClick = { viewModel.onCategoryToggled(expandableCategory.category.categoryId) }
                )
            }
            if (expandableCategory.isExpanded) {
                items(expandableCategory.channels, key = { it.streamId }) { channel ->
                    ChannelListItem(
                        channel = channel,
                        isSelected = channel.streamId == uiState.currentlyPlaying?.streamId,
                        onChannelClick = { viewModel.onChannelSelected(it) },
                        isFavorite = channel.streamId.toString() in uiState.favoriteChannelIds,
                        onToggleFavorite = { viewModel.toggleFavorite(it.streamId.toString()) }
                    )
                }
            }
        }
    }
}


@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    isSelected: Boolean,
    onHeaderClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onHeaderClick)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (isExpanded) "Contraer" else "Expandir",
                modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
            )
        }
    }
}

@Composable
fun ChannelListItem(
    channel: LiveStream,
    isSelected: Boolean,
    onChannelClick: (LiveStream) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (LiveStream) -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onChannelClick(channel) }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(channel.streamIcon)
                .crossfade(true)
                .error(android.R.drawable.stat_notify_error)
                .build(),
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = channel.name,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = { onToggleFavorite(channel) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
