package com.kybers.play.ui.channels

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.ui.components.ScrollIndicator
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.ui.player.ChannelPlayerControls
import com.kybers.play.ui.player.PlayerHost
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.SortOrder
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    onPlayerUiStateChanged: (isFullScreen: Boolean, isInPipMode: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val lazyListState = rememberLazyListState()

    LaunchedEffect(uiState.isFullScreen, uiState.isInPipMode) {
        onPlayerUiStateChanged(uiState.isFullScreen, uiState.isInPipMode)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val isInPip = activity?.isInPictureInPictureMode ?: false
                viewModel.setInPipMode(isInPip)

                if (event == Lifecycle.Event.ON_STOP && !isInPip && uiState.isPlayerVisible) {
                    viewModel.hidePlayer()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var favoriteChannels by remember { mutableStateOf(emptyList<LiveStream>()) }
    LaunchedEffect(uiState.searchQuery, uiState.channelSortOrder, uiState.favoriteChannelIds, uiState.isFavoritesCategoryExpanded, uiState.categories) {
        favoriteChannels = viewModel.getFavoriteChannels()
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToItemEvent.collectLatest { targetId ->
            var targetIndex = -1
            var currentIndex = 0

            if (targetId == "favorites") {
                targetIndex = 0
            } else {
                currentIndex++
                if (uiState.isFavoritesCategoryExpanded) {
                    currentIndex += favoriteChannels.size
                }
                for (category in uiState.categories) {
                    if (category.category.categoryId == targetId) {
                        targetIndex = currentIndex
                        break
                    }
                    currentIndex++
                    if (category.isExpanded) {
                        currentIndex += category.channels.size
                    }
                }
            }
            if (targetIndex != -1) {
                lazyListState.animateScrollToItem(targetIndex)
            }
        }
    }

    SystemVolumeReceiver(audioManager) { volume: Int ->
        viewModel.updateSystemVolume(volume)
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

    com.kybers.play.ui.theme.ResponsiveScaffold(
        topBar = {
            AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                ImprovedChannelTopBar(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshChannelsManually() },
                    onToggleCategoryVisibility = { viewModel.toggleCategoryVisibility() },
                    onSortCategories = { viewModel.toggleSortMenu(true) }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (!uiState.isFullScreen && !uiState.isInPipMode) paddingValues else PaddingValues(0.dp))
        ) {
            PlayerSection(
                viewModel = viewModel,
                onPictureInPicture = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val aspectRatio = Rational(16, 9)
                        val pipParams = PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build()
                        activity?.enterPictureInPictureMode(pipParams)
                    }
                }
            )

            AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode) {
                ChannelListSection(viewModel = viewModel, lazyListState = lazyListState, favoriteChannels = favoriteChannels)
            }
        }
    }

    if (uiState.showSortMenu) {
        SortOptionsDialog(
            currentCategorySortOrder = uiState.categorySortOrder,
            currentChannelSortOrder = uiState.channelSortOrder,
            onCategorySortOrderSelected = { order: SortOrder -> viewModel.setCategorySortOrder(order) },
            onChannelSortOrderSelected = { order: SortOrder -> viewModel.setChannelSortOrder(order) },
            onDismiss = { viewModel.toggleSortMenu(false) }
        )
    }
}

@Composable
private fun PlayerSection(
    viewModel: ChannelsViewModel,
    onPictureInPicture: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val playerModifier = if (uiState.isPlayerVisible) {
        if (uiState.isFullScreen) Modifier.fillMaxSize() else Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    } else {
        Modifier.height(0.dp)
    }

    if (uiState.isPlayerVisible) {
        PlayerHost(
            mediaPlayer = viewModel.mediaPlayer,
            modifier = playerModifier,
            playerStatus = uiState.playerStatus,
            onEnterPipMode = onPictureInPicture,
            controls = { isVisible, onAnyInteraction, onRequestPipMode ->
                ChannelPlayerControls(
                    isVisible = isVisible,
                    onAnyInteraction = onAnyInteraction,
                    onRequestPipMode = onRequestPipMode,
                    isPlaying = uiState.playerStatus == PlayerStatus.PLAYING,
                    isMuted = uiState.isMuted,
                    isFavorite = uiState.currentlyPlaying?.let { it.streamId.toString() in uiState.favoriteChannelIds } ?: false,
                    isFullScreen = uiState.isFullScreen,
                    streamTitle = uiState.currentlyPlaying?.name ?: "Stream",
                    systemVolume = uiState.systemVolume,
                    maxSystemVolume = uiState.maxSystemVolume,
                    screenBrightness = uiState.screenBrightness,
                    audioTracks = uiState.availableAudioTracks,
                    subtitleTracks = uiState.availableSubtitleTracks,
                    showAudioMenu = uiState.showAudioMenu,
                    showSubtitleMenu = uiState.showSubtitleMenu,
                    onClose = {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        viewModel.hidePlayer()
                    },
                    onPlayPause = {
                        if (uiState.playerStatus == PlayerStatus.PLAYING) viewModel.mediaPlayer.pause() else viewModel.mediaPlayer.play()
                    },
                    onNext = viewModel::playNextChannel,
                    onPrevious = viewModel::playPreviousChannel,
                    onToggleMute = { viewModel.onToggleMute(audioManager) },
                    onToggleFavorite = { uiState.currentlyPlaying?.let { viewModel.toggleFavorite(it.streamId.toString()) } },
                    onToggleFullScreen = {
                        activity?.requestedOrientation = if (uiState.isFullScreen) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onSetVolume = { vol -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0) },
                    onSetBrightness = viewModel::setScreenBrightness,
                    onToggleAudioMenu = viewModel::toggleAudioMenu,
                    onToggleSubtitleMenu = viewModel::toggleSubtitleMenu,
                    onSelectAudioTrack = viewModel::selectAudioTrack,
                    onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                    onToggleAspectRatio = viewModel::toggleAspectRatio,
                    // Add retry parameters
                    playerStatus = uiState.playerStatus,
                    retryAttempt = uiState.retryAttempt,
                    maxRetryAttempts = uiState.maxRetryAttempts,
                    retryMessage = uiState.retryMessage,
                    onRetry = viewModel::retryCurrentChannel
                )
            }
        )
    }
}

@Composable
private fun SystemVolumeReceiver(audioManager: AudioManager, onVolumeChange: (Int) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    onVolumeChange(volume)
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListSection(
    viewModel: ChannelsViewModel,
    lazyListState: LazyListState,
    favoriteChannels: List<LiveStream>
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { query: String -> viewModel.onSearchQueryChanged(query) },
            onClear = { viewModel.onSearchQueryChanged("") }
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Siempre mostrar categoría de favoritos
            stickyHeader(key = "favorites") {
                CategoryHeader(
                    categoryName = "Favoritos",
                    isExpanded = uiState.isFavoritesCategoryExpanded,
                    onHeaderClick = { viewModel.onFavoritesCategoryToggled() },
                    itemCount = favoriteChannels.size
                )
            }
            if (uiState.isFavoritesCategoryExpanded) {
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
                    items(
                        items = favoriteChannels,
                        key = { channel -> "fav-${channel.num}-${channel.categoryId}-${channel.streamId}" }
                    ) { channel ->
                        ChannelListItem(
                            channel = channel,
                            isSelected = channel.streamId == uiState.currentlyPlaying?.streamId,
                            onChannelClick = { selectedChannel: LiveStream -> viewModel.onChannelSelected(selectedChannel.copy(categoryId = "favorites")) },
                            isFavorite = true,
                            onToggleFavorite = { favoriteChannel: LiveStream -> viewModel.toggleFavorite(favoriteChannel.streamId.toString()) }
                        )
                    }
                }
            }

            // Solo mostrar categorías si no están ocultas
            if (!uiState.areCategoriesHidden) {
                uiState.categories.forEach { expandableCategory ->
                    stickyHeader(key = expandableCategory.category.categoryId) {
                        CategoryHeader(
                            categoryName = expandableCategory.category.categoryName,
                            isExpanded = expandableCategory.isExpanded,
                            onHeaderClick = { viewModel.onCategoryToggled(expandableCategory.category.categoryId) },
                            itemCount = expandableCategory.channels.size
                        )
                    }
                    if (expandableCategory.isExpanded) {
                        items(
                            items = expandableCategory.channels,
                            key = { channel -> "channel-${channel.num}-${channel.categoryId}-${channel.streamId}" }
                        ) { channel ->
                            ChannelListItem(
                                channel = channel,
                                isSelected = channel.streamId == uiState.currentlyPlaying?.streamId,
                                onChannelClick = { selectedChannel: LiveStream -> viewModel.onChannelSelected(selectedChannel) },
                                isFavorite = channel.streamId.toString() in uiState.favoriteChannelIds,
                                onToggleFavorite = { favoriteChannel: LiveStream -> viewModel.toggleFavorite(favoriteChannel.streamId.toString()) }
                            )
                        }
                    }
                }
            } else {
                // Cuando las categorías están ocultas, mostrar mensaje informativo
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Categorías ocultas",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Solo se muestran los canales favoritos. Usa el botón de visibilidad para mostrar todas las categorías.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
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
            .padding(horizontal = 16.dp, vertical = 4.dp), // Reducido de 8dp a 4dp
        placeholder = {
            Text(
                "Buscar canales...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Buscar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Limpiar búsqueda",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}


@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    itemCount: Int? = null
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "rotation"
    )

    // Categoría pegajosa con fondo opaco que no permite ver contenido detrás
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onHeaderClick)
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono minimalista
            Icon(
                imageVector = if (categoryName.contains("favoritos", ignoreCase = true))
                    Icons.Default.Star
                else
                    Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Título con soporte para 2 líneas
            Text(
                text = if (itemCount != null) "$categoryName ($itemCount)" else categoryName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Flecha minimalista
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (isExpanded) "Contraer" else "Expandir",
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.primary
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { onChannelClick(channel) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo del canal
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.streamIcon)
                    .crossfade(true)
                    .error(android.R.drawable.stat_notify_error)
                    .build(),
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Información del canal - máximo espacio posible
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Nombre del canal con máximo 3 líneas
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // EPG minimalista
                MinimalEpgInfo(
                    currentEvent = channel.currentEpgEvent,
                    nextEvent = channel.nextEpgEvent
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón de favoritos
            IconButton(
                onClick = { onToggleFavorite(channel) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MinimalEpgInfo(
    currentEvent: EpgEvent?,
    nextEvent: EpgEvent?
) {
    // Si no hay información de EPG, no mostrar nada (sin mensaje)
    if (currentEvent == null) return

    val startTime = formatTimestampToHour(currentEvent.startTimestamp)
    val progress = calculateEpgProgress(currentEvent.startTimestamp, currentEvent.stopTimestamp)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Programa actual: icono reloj - Hora local de inicio - nombre del programa
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = startTime,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = currentEvent.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Barra de progreso EPG
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        // Próximo programa (si existe): icono reloj - Hora local de inicio - nombre del programa siguiente
        if (nextEvent != null) {
            val nextStartTime = formatTimestampToHour(nextEvent.startTimestamp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = nextStartTime,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = nextEvent.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun calculateEpgProgress(start: Long, end: Long): Float {
    val now = System.currentTimeMillis() / 1000
    if (now < start || start >= end) return 0f
    if (now > end) return 1f

    val totalDuration = (end - start).toFloat()
    val elapsed = (now - start).toFloat()
    return (elapsed / totalDuration).coerceIn(0f, 1f)
}

private fun formatTimestampToHour(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-MX")).apply {
        timeZone = TimeZone.getDefault()
    }
    return sdf.format(Date(timestamp * 1000))
}

@Composable
fun SortOptionsDialog(
    currentCategorySortOrder: SortOrder,
    currentChannelSortOrder: SortOrder,
    onCategorySortOrderSelected: (SortOrder) -> Unit,
    onChannelSortOrderSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opciones de Ordenación") },
        text = {
            Column {
                Text("Ordenar Categorías por:", style = MaterialTheme.typography.titleSmall)
                SortOrder.values().forEach { order ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySortOrderSelected(order) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (order == currentCategorySortOrder),
                            onClick = { onCategorySortOrderSelected(order) }
                        )
                        Text(text = order.toLocalizedName())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Ordenar Canales por:", style = MaterialTheme.typography.titleSmall)
                SortOrder.values().forEach { order ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChannelSortOrderSelected(order) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (order == currentChannelSortOrder),
                            onClick = { onChannelSortOrderSelected(order) }
                        )
                        Text(text = order.toLocalizedName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun SortOrder.toLocalizedName(): String {
    return when (this) {
        SortOrder.DEFAULT -> "Por Defecto"
        SortOrder.AZ -> "Alfabético (A-Z)"
        SortOrder.ZA -> "Alfabético (Z-A)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImprovedChannelTopBar(
    uiState: ChannelsUiState,
    onRefresh: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    onSortCategories: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp)
    ) {
        Column {
            // Top section with logo and title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo and title section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // App logo placeholder (you can replace with actual logo)
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "TV en Vivo",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        )
                        Text(
                            text = "${uiState.totalChannelCount} canales",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        )
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refresh button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Hide categories button
                    IconButton(
                        onClick = onToggleCategoryVisibility,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Ocultar categorías",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Sort button
                    IconButton(
                        onClick = onSortCategories,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Ordenar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom section with update info
            if (uiState.lastUpdatedTimestamp > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Ult. Act: ${formatTimestamp(uiState.lastUpdatedTimestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }

                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Nunca"
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
    return sdf.format(Date(timestamp))
}
