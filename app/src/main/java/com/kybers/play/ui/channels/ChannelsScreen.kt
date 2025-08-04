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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
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
import com.kybers.play.ui.movies.ExpandableMovieCategory
import com.kybers.play.ui.series.ExpandableSeriesCategory
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.runtime.saveable.rememberSaveable
import com.kybers.play.ui.components.DisplayModeToggle

/**
 * The main screen for browsing and watching live TV channels.
 *
 * This screen displays a list of channels, categorized and searchable. It includes a video player
 * for watching selected channels, with controls for playback, volume, and full-screen mode.
 * Users can also manage favorite channels and customize the visibility of channel categories.
 *
 * @param viewModel The [ChannelsViewModel] that provides the data and logic for this screen.
 * @param onPlayerUiStateChanged A callback that notifies the parent composable of changes to the
 * player's UI state, such as entering or exiting full-screen or Picture-in-Picture mode.
 */
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

    var showCategoryVisibilityScreen by rememberSaveable { mutableStateOf(false) }

    com.kybers.play.ui.theme.ResponsiveScaffold(
        topBar = {
            AnimatedVisibility(visible = !uiState.isFullScreen && !uiState.isInPipMode && !uiState.isPlayerVisible) {
                ImprovedChannelTopBar(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshChannelsManually() },
                    onToggleCategoryVisibility = { showCategoryVisibilityScreen = true },
                    onSortCategories = { viewModel.toggleSortMenu(true) }
                )
            }
        }
    ) { paddingValues ->
        if (showCategoryVisibilityScreen) {
            CategoryVisibilityScreen(
                allCategories = uiState.masterCategoryList,
                hiddenCategoryIds = uiState.hiddenCategoryIds,
                onBack = { showCategoryVisibilityScreen = false },
                onSave = { ids ->
                    viewModel.setHiddenCategories(ids)
                    showCategoryVisibilityScreen = false
                }
            )
        } else {
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

/**
 * A search bar composable that allows users to filter the list of channels.
 *
 * @param query The current search query.
 * @param onQueryChange A callback that is invoked when the search query changes.
 * @param onClear A callback that is invoked when the clear button is clicked.
 */
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


/**
 * A composable that displays a category header.
 *
 * The header displays the category name and an expand/collapse icon.
 *
 * @param categoryName The name of the category.
 * @param isExpanded Whether the category is currently expanded.
 * @param onHeaderClick A callback that is invoked when the header is clicked.
 * @param itemCount The number of items in the category.
 */
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

/**
 * A composable that displays a single channel in the list.
 *
 * @param channel The channel to display.
 * @param isSelected Whether the channel is currently selected.
 * @param onChannelClick A callback that is invoked when the channel is clicked.
 * @param isFavorite Whether the channel is a favorite.
 * @param onToggleFavorite A callback that is invoked when the favorite button is clicked.
 */
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

/**
 * A composable that displays minimal EPG (Electronic Program Guide) information.
 *
 * @param currentEvent The current EPG event.
 * @param nextEvent The next EPG event.
 */
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

/**
 * A dialog that allows the user to select sort options for categories and channels.
 *
 * @param currentCategorySortOrder The currently selected sort order for categories.
 * @param currentChannelSortOrder The currently selected sort order for channels.
 * @param onCategorySortOrderSelected A callback that is invoked when a category sort order is selected.
 * @param onChannelSortOrderSelected A callback that is invoked when a channel sort order is selected.
 * @param onDismiss A callback that is invoked when the dialog is dismissed.
 */
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
                SortOrder.entries.forEach { order ->
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
                SortOrder.entries.forEach { order ->
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

/**
 * Converts a [SortOrder] enum to a localized string.
 *
 * @return The localized string representation of the sort order.
 */
@Composable
fun SortOrder.toLocalizedName(): String {
    return when (this) {
        SortOrder.DEFAULT -> "Por Defecto"
        SortOrder.AZ -> "Alfabético (A-Z)"
        SortOrder.ZA -> "Alfabético (Z-A)"
    }
}

/**
 * An improved top app bar for the channels screen.
 *
 * @param uiState The current UI state.
 * @param onRefresh A callback that is invoked when the refresh button is clicked.
 * @param onToggleCategoryVisibility A callback that is invoked when the category visibility button is clicked.
 * @param onSortCategories A callback that is invoked when the sort button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImprovedChannelTopBar(
    uiState: ChannelsUiState,
    onRefresh: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    onSortCategories: () -> Unit,
) {
    val hasHiddenCategories = uiState.hiddenCategoryIds.isNotEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Sección principal - Logo, título y botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo y título más compactos
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TV en Vivo",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.totalChannelCount} canales",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }

                // Botones de acción más compactos
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Display mode toggle button
                    DisplayModeToggle(
                        currentMode = uiState.displayMode,
                        onModeChanged = { mode ->  }
                    )

                    // Refresh button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Hide categories button
                    IconButton(
                        onClick = onToggleCategoryVisibility,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = if (hasHiddenCategories) Icons.Filled.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (hasHiddenCategories) "Mostrar categorías ocultas" else "Ocultar categorías",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Sort button
                    IconButton(
                        onClick = onSortCategories,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Ordenar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Sección inferior con info de actualización (solo si es necesaria)
            if (uiState.lastUpdatedTimestamp > 0 || uiState.isRefreshing) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (uiState.lastUpdatedTimestamp > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Ult. Act: ${formatTimestamp(uiState.lastUpdatedTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }

                    if (uiState.isRefreshing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Actualizando...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
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

/**
 * A screen for managing the visibility of channel categories.
 *
 * @param allCategories The list of all expandable categories (unfiltered).
 * @param hiddenCategoryIds A set of IDs for the categories that are currently hidden.
 * @param onBack A callback that is invoked when the back button is clicked.
 * @param onSave A callback that is invoked when the save button is clicked with the new set of hidden IDs.
 * @param contentType The type of content being managed (e.g., "canales", "películas", "series").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CategoryVisibilityScreen(
    allCategories: List<T>,
    hiddenCategoryIds: Set<String>,
    onBack: () -> Unit,
    onSave: (Set<String>) -> Unit,
    contentType: String = "categorías"
) where T : Any {
    var selectedHidden by remember { mutableStateOf(hiddenCategoryIds) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Gestión de Categorías",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.shadow(4.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Header reducido
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Seleccione las categorías de $contentType a ocultar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (selectedHidden.isNotEmpty()) {
                        Text(
                            text = "${selectedHidden.size}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Lista de categorías mejorada
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(allCategories, key = {
                    when (it) {
                        is ExpandableCategory -> it.category.categoryId
                        is ExpandableMovieCategory -> it.category.categoryId
                        is ExpandableSeriesCategory -> it.category.categoryId
                        else -> it.hashCode().toString()
                    }
                }) { cat ->
                    val categoryId = when (cat) {
                        is ExpandableCategory -> cat.category.categoryId
                        is ExpandableMovieCategory -> cat.category.categoryId
                        is ExpandableSeriesCategory -> cat.category.categoryId
                        else -> ""
                    }

                    val categoryName = when (cat) {
                        is ExpandableCategory -> cat.category.categoryName
                        is ExpandableMovieCategory -> cat.category.categoryName
                        is ExpandableSeriesCategory -> cat.category.categoryName
                        else -> "Categoría desconocida"
                    }

                    val itemCount = when (cat) {
                        is ExpandableCategory -> cat.channels.size
                        is ExpandableMovieCategory -> cat.movies.size
                        is ExpandableSeriesCategory -> cat.series.size
                        else -> 0
                    }

                    val isChecked = selectedHidden.contains(categoryId)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedHidden = if (isChecked) {
                                    selectedHidden - categoryId
                                } else {
                                    selectedHidden + categoryId
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isChecked) 4.dp else 1.dp
                        ),
                        border = if (isChecked)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedHidden = if (checked) {
                                        selectedHidden + categoryId
                                    } else {
                                        selectedHidden - categoryId
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.error,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkmarkColor = MaterialTheme.colorScheme.onError
                                )
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (isChecked)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )

                                val itemLabel = when (contentType) {
                                    "películas" -> if (itemCount != 1) "películas" else "película"
                                    "series" -> if (itemCount != 1) "series" else "serie"
                                    else -> if (itemCount != 1) "canales" else "canal"
                                }

                                Text(
                                    text = "$itemCount $itemLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isChecked) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = "Oculta",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Botones en la parte inferior
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Botones principales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { onBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { onSave(selectedHidden) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Aceptar")
                    }
                }

                // Botón de deseleccionar todo
                TextButton(
                    onClick = { selectedHidden = emptySet() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Deseleccionar todo",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
