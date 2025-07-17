package com.kybers.play.ui.channels

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.ui.player.PlayerControls
import com.kybers.play.ui.player.VLCPlayer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    onFullScreenToggled: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity

    // --- SIDE EFFECT HANDLING ---

    DisposableEffect(uiState.isPlayerVisible) {
        val window = activity?.window
        if (uiState.isPlayerVisible) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(uiState.isFullScreen) {
        onFullScreenToggled(uiState.isFullScreen)
        activity?.let {
            val window = it.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (uiState.isFullScreen) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(uiState.brightness) {
        val window = activity?.window
        val layoutParams = window?.attributes
        layoutParams?.screenBrightness = uiState.brightness
        window?.attributes = layoutParams
        onDispose {}
    }

    BackHandler(enabled = uiState.isPlayerVisible) {
        if (uiState.isFullScreen) {
            viewModel.onToggleFullScreen()
        } else {
            viewModel.hidePlayer()
        }
    }

    // --- MAIN UI ---

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isPlayerVisible) {
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
                .then(if (!uiState.isFullScreen) Modifier.padding(paddingValues) else Modifier)
        ) {
            PlayerSection(viewModel = viewModel)

            AnimatedVisibility(visible = !uiState.isFullScreen) {
                ChannelListSection(viewModel = viewModel)
            }
        }
    }
}


@Composable
private fun PlayerSection(viewModel: ChannelsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible, uiState.playerStatus) {
        if (controlsVisible && uiState.playerStatus == PlayerStatus.PLAYING) {
            delay(5000) // 5 seconds
            controlsVisible = false
        }
    }

    val playerModifier = if (uiState.isPlayerVisible) {
        if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    } else {
        Modifier.height(0.dp)
    }

    Box(
        modifier = playerModifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        if (uiState.isPlayerVisible) {
            VLCPlayer(
                mediaPlayer = viewModel.mediaPlayer,
                modifier = Modifier.fillMaxSize()
            )

            PlayerControls(
                modifier = Modifier.fillMaxSize(),
                isVisible = controlsVisible,
                isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                isMuted = uiState.isMuted,
                isFavorite = uiState.currentlyPlaying?.let { it.streamId.toString() in uiState.favoriteChannelIds } ?: false,
                isFullScreen = uiState.isFullScreen,
                streamTitle = uiState.currentlyPlaying?.name ?: "Stream",
                volume = uiState.volume,
                brightness = uiState.brightness,
                audioTracks = uiState.availableAudioTracks,
                subtitleTracks = uiState.availableSubtitleTracks,
                videoTracks = uiState.availableVideoTracks,
                showAudioMenu = uiState.showAudioMenu,
                showSubtitleMenu = uiState.showSubtitleMenu,
                showVideoMenu = uiState.showVideoMenu,
                onClose = { viewModel.hidePlayer() },
                onPlayPause = { if (viewModel.mediaPlayer.isPlaying) viewModel.mediaPlayer.pause() else viewModel.mediaPlayer.play() },
                onNext = { viewModel.playNextChannel() },
                onPrevious = { viewModel.playPreviousChannel() },
                onToggleMute = { viewModel.toggleMute() },
                onToggleFavorite = { uiState.currentlyPlaying?.let { viewModel.toggleFavorite(it.streamId.toString()) } },
                onToggleFullScreen = { viewModel.onToggleFullScreen() },
                onSetVolume = { viewModel.setVolume(it) },
                onSetBrightness = { viewModel.setBrightness(it) },
                onToggleAudioMenu = { viewModel.toggleAudioMenu(it) },
                onToggleSubtitleMenu = { viewModel.toggleSubtitleMenu(it) },
                onToggleVideoMenu = { viewModel.toggleVideoMenu(it) },
                onSelectAudioTrack = { viewModel.selectAudioTrack(it) },
                onSelectSubtitleTrack = { viewModel.selectSubtitleTrack(it) },
                onSelectVideoTrack = { viewModel.selectVideoTrack(it) }
            )

            if (uiState.playerStatus == PlayerStatus.BUFFERING) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else if (uiState.playerStatus == PlayerStatus.ERROR) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Error al cargar el canal", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.retryPlayback() }) {
                        Text("Reintentar")
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

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.onSearchQueryChanged(it) },
            onClear = { viewModel.onSearchQueryChanged("") }
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            stickyHeader {
                CategoryHeader(
                    categoryName = "Favoritos",
                    isExpanded = uiState.isFavoritesCategoryExpanded,
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
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar canales...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        )
    )
}


@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
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
