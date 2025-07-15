package com.kybers.play.ui.channels

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.ui.player.PlayerControls
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout // Importación para los modos de ajuste

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    onFullScreenToggled: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val player = viewModel.player
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity

    var controlsVisible by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    // Estado para el modo de ajuste de pantalla
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }


    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = uiState.isPlayerVisible) {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            viewModel.hidePlayer()
        }
    }

    LaunchedEffect(isFullScreen) {
        onFullScreenToggled(isFullScreen)
        activity?.let {
            val window = it.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.screenTitle)
                        }
                    },
                    navigationIcon = {
                        if (uiState.isPlayerVisible) {
                            IconButton(onClick = {
                                if (isFullScreen) {
                                    isFullScreen = false
                                } else {
                                    viewModel.hidePlayer()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                            }
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
                .then(if (!isFullScreen) Modifier.padding(paddingValues) else Modifier)
        ) {
            AnimatedVisibility(
                visible = uiState.isPlayerVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                controllerAutoShow = false
                                controllerHideOnTouch = false
                                resizeMode = currentResizeMode // Aplica el modo de ajuste
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        controlsVisible = !controlsVisible
                                    }
                                )
                            }
                    ) {
                        PlayerControls(
                            player = player,
                            controlsVisible = controlsVisible,
                            onControlsVisibilityChanged = { controlsVisible = it },
                            isFullScreen = isFullScreen,
                            onToggleFullScreen = {
                                isFullScreen = !isFullScreen
                                controlsVisible = true
                            },
                            channelName = uiState.currentlyPlaying?.name,
                            onPreviousChannel = { viewModel.playPreviousChannel() },
                            onNextChannel = { viewModel.playNextChannel() },
                            isFavorite = uiState.currentlyPlaying?.streamId?.toString() in uiState.favoriteChannelIds,
                            onToggleFavorite = {
                                uiState.currentlyPlaying?.streamId?.toString()?.let { channelId ->
                                    viewModel.toggleFavorite(channelId)
                                }
                            },
                            isTvChannel = uiState.isCurrentlyPlayingTvChannel,
                            // ¡CAMBIO CLAVE AQUÍ! Lógica para alternar entre todos los modos de ajuste
                            onToggleResizeMode = {
                                currentResizeMode = when (currentResizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH // Nuevo modo
                                    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT // Nuevo modo
                                    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> AspectRatioFrameLayout.RESIZE_MODE_FIT // Vuelve al inicio
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                        )
                    }
                }
            }

            if (!isFullScreen) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar canales...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") }
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                        ,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Categoría de Favoritos
                        val favoriteChannels = uiState.categories
                            .flatMap { it.channels }
                            .filter { it.streamId.toString() in uiState.favoriteChannelIds }

                        if (favoriteChannels.isNotEmpty() || uiState.isFavoritesCategoryExpanded) {
                            stickyHeader {
                                CategoryHeader(
                                    categoryName = "Favoritos",
                                    isExpanded = uiState.isFavoritesCategoryExpanded,
                                    isLoading = false,
                                    onHeaderClick = {
                                        viewModel.onFavoritesCategoryToggled()
                                        if (!uiState.isFavoritesCategoryExpanded) {
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                )
                            }
                            if (uiState.isFavoritesCategoryExpanded) {
                                items(favoriteChannels) { channel ->
                                    ChannelListItem(
                                        channel = channel,
                                        onChannelClick = { viewModel.onChannelSelected(it) },
                                        isFavorite = true,
                                        onToggleFavorite = { viewModel.toggleFavorite(it.streamId.toString()) }
                                    )
                                }
                            }
                        }

                        var categoryIndexOffset = 0
                        if (favoriteChannels.isNotEmpty() || uiState.isFavoritesCategoryExpanded) {
                            categoryIndexOffset++
                            if (uiState.isFavoritesCategoryExpanded) {
                                categoryIndexOffset += favoriteChannels.size
                            }
                        }


                        uiState.categories.forEachIndexed { index, expandableCategory ->
                            val headerIndex = categoryIndexOffset + index
                            stickyHeader {
                                CategoryHeader(
                                    categoryName = expandableCategory.category.categoryName,
                                    isExpanded = expandableCategory.isExpanded,
                                    isLoading = expandableCategory.isLoading,
                                    onHeaderClick = {
                                        val wasExpanded = expandableCategory.isExpanded
                                        viewModel.onCategoryToggled(expandableCategory.category.categoryId)
                                        if (!wasExpanded) {
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(headerIndex)
                                            }
                                        }
                                    }
                                )
                            }
                            if (expandableCategory.isExpanded) {
                                items(expandableCategory.channels) { channel ->
                                    ChannelListItem(
                                        channel = channel,
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
        }
    }
}

@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    isLoading: Boolean,
    onHeaderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
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
    onChannelClick: (LiveStream) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (LiveStream) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onChannelClick(channel) },
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
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = channel.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = { onToggleFavorite(channel) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
