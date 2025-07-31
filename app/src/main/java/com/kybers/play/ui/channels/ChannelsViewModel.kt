package com.kybers.play.ui.channels

import android.app.Application
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.player.AspectRatioMode
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.SortOrder
import com.kybers.play.ui.player.TrackInfo
import com.kybers.play.ui.player.toAspectRatioMode
import com.kybers.play.ui.player.toSortOrder
import com.kybers.play.player.MediaManager
import com.kybers.play.player.RetryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpandableCategory(
    val category: Category,
    val channels: List<LiveStream> = emptyList(),
    val isExpanded: Boolean = false,
    val epgLoaded: Boolean = false
)

data class ChannelsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val categories: List<ExpandableCategory> = emptyList(),
    val currentlyPlaying: LiveStream? = null,
    val isPlayerVisible: Boolean = false,
    val screenTitle: String = "Canales",
    val currentChannelIndex: Int = -1,
    val favoriteChannelIds: Set<String> = emptySet(),
    val isFavoritesCategoryExpanded: Boolean = false,
    val playerStatus: PlayerStatus = PlayerStatus.IDLE,
    val isFullScreen: Boolean = false,
    val isMuted: Boolean = false,
    val systemVolume: Int = 0,
    val maxSystemVolume: Int = 1,
    val screenBrightness: Float = 0.5f,
    val originalBrightness: Float = -1f,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val showAudioMenu: Boolean = false,
    val showSubtitleMenu: Boolean = false,
    val categorySortOrder: SortOrder = SortOrder.DEFAULT,
    val channelSortOrder: SortOrder = SortOrder.DEFAULT,
    val showSortMenu: Boolean = false,
    val currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT_SCREEN,
    val isInPipMode: Boolean = false,
    val lastUpdatedTimestamp: Long = 0L,
    val isRefreshing: Boolean = false,
    val isEpgUpdating: Boolean = false,
    val epgUpdateMessage: String? = null,
    val totalChannelCount: Int = 0,
    val retryAttempt: Int = 0,
    val maxRetryAttempts: Int = 3,
    val retryMessage: String? = null
)

open class ChannelsViewModel(
    application: Application,
    private val liveRepository: LiveRepository,
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager,
    private val parentalControlManager: ParentalControlManager
) : AndroidViewModel(application) {

    private lateinit var libVLC: LibVLC
    lateinit var mediaPlayer: MediaPlayer
        private set

    private val mediaManager = MediaManager()
    private lateinit var retryManager: RetryManager

    private val _uiState = MutableStateFlow(ChannelsUiState())
    open val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())

    private var epgCacheMap: Map<Int, List<EpgEvent>> = emptyMap()

    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()

    private lateinit var vlcPlayerListener: MediaPlayer.EventListener

    init {
        setupRetryManager()
        setupVLC()
        val savedCategorySortOrder = preferenceManager.getSortOrder("category").toSortOrder()
        val savedChannelSortOrder = preferenceManager.getSortOrder("channel").toSortOrder()
        val savedAspectRatioMode = preferenceManager.getAspectRatioMode().toAspectRatioMode()
        val lastSyncTime = syncManager.getLastSyncTimestamp(currentUser.id, SyncManager.ContentType.LIVE_TV)

        _uiState.update {
            it.copy(
                categorySortOrder = savedCategorySortOrder,
                channelSortOrder = savedChannelSortOrder,
                currentAspectRatioMode = savedAspectRatioMode,
                lastUpdatedTimestamp = lastSyncTime
            )
        }
        loadInitialChannelsAndPreloadEpg()
        startEpgUpdater()
        
        viewModelScope.launch {
            _favoriteChannelIds.collect { favorites ->
                _uiState.update { it.copy(favoriteChannelIds = favorites) }
                filterAndSortCategories()
            }
        }
        
        // React to parental control changes
        viewModelScope.launch {
            parentalControlManager.blockedCategoriesState.collect { _ ->
                // Re-filter categories when blocked categories change
                filterAndSortCategories()
            }
        }
    }

    private fun startEpgUpdater() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)

                val currentState = _uiState.value
                if (currentState.isLoading || (!currentState.isFavoritesCategoryExpanded && currentState.categories.none { it.isExpanded })) {
                    continue
                }

                var changed = false
                val newCategories = currentState.categories.map { category ->
                    if (category.isExpanded && category.epgLoaded) {
                        val newEnrichedChannels = liveRepository.enrichChannelsWithEpg(category.channels, epgCacheMap)
                        if (newEnrichedChannels != category.channels) changed = true
                        category.copy(channels = newEnrichedChannels)
                    } else {
                        category
                    }
                }

                if (changed) {
                    _uiState.update { it.copy(categories = newCategories) }
                }
            }
        }
    }

    private fun setupVLC() {
        // Set up the VLC event listener now that retryManager is initialized
        vlcPlayerListener = MediaPlayer.EventListener { event ->
            val currentState = _uiState.value.playerStatus
            val newStatus = when (event.type) {
                MediaPlayer.Event.Playing -> {
                    updateTrackInfo()
                    PlayerStatus.PLAYING
                }
                MediaPlayer.Event.Paused -> PlayerStatus.PAUSED
                MediaPlayer.Event.Buffering -> if (currentState != PlayerStatus.PLAYING) PlayerStatus.BUFFERING else null
                MediaPlayer.Event.EncounteredError -> {
                    Log.e("ChannelsViewModel", "VLC encountered error")
                    // Only trigger retry if we're not already in a retry state
                    // Let the current retry system handle the failure naturally
                    if (currentState != PlayerStatus.RETRYING && currentState != PlayerStatus.RETRY_FAILED) {
                        Log.d("ChannelsViewModel", "VLC error occurred outside retry system, triggering retry")
                        val currentChannel = _uiState.value.currentlyPlaying
                        if (currentChannel != null && !retryManager.isRetrying()) {
                            retryManager.startRetry(viewModelScope) {
                                try {
                                    playChannelInternal(currentChannel)
                                    true
                                } catch (e: Exception) {
                                    Log.e("ChannelsViewModel", "Retry failed: ${e.message}", e)
                                    false
                                }
                            }
                        }
                    } else {
                        Log.d("ChannelsViewModel", "VLC error during retry - letting retry system handle it")
                    }
                    PlayerStatus.ERROR
                }
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> PlayerStatus.IDLE
                else -> null
            }
            if (newStatus != null && newStatus != currentState) {
                _uiState.update { it.copy(playerStatus = newStatus) }
            }
        }

        val vlcOptions = preferenceManager.getVLCOptions()
        libVLC = LibVLC(getApplication(), vlcOptions)
        mediaPlayer = MediaPlayer(libVLC).apply {
            setEventListener(vlcPlayerListener)
        }
    }

    private fun setupRetryManager() {
        retryManager = RetryManager(
            onRetryAttempt = { attempt, maxRetries ->
                _uiState.update { 
                    it.copy(
                        playerStatus = PlayerStatus.RETRYING,
                        retryAttempt = attempt,
                        maxRetryAttempts = maxRetries,
                        retryMessage = "Reintentando... ($attempt/$maxRetries)"
                    ) 
                }
            },
            onRetrySuccess = {
                _uiState.update { 
                    it.copy(
                        playerStatus = PlayerStatus.PLAYING,
                        retryAttempt = 0,
                        retryMessage = null
                    ) 
                }
            },
            onRetryFailed = {
                _uiState.update { 
                    it.copy(
                        playerStatus = PlayerStatus.RETRY_FAILED,
                        retryAttempt = 0,
                        retryMessage = "Error de conexión. Verifica tu red e inténtalo de nuevo."
                    ) 
                }
            }
        )
    }

    open fun onChannelSelected(channel: LiveStream) {
        viewModelScope.launch {
            val allVisibleChannels = (if (_uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                    _uiState.value.categories.flatMap { it.channels }
            val index = allVisibleChannels.indexOfFirst { it.streamId == channel.streamId }

            _uiState.update {
                it.copy(
                    currentlyPlaying = channel,
                    isPlayerVisible = true,
                    screenTitle = channel.name,
                    currentChannelIndex = index,
                    playerStatus = PlayerStatus.LOADING,
                    availableAudioTracks = emptyList(),
                    availableSubtitleTracks = emptyList(),
                    retryAttempt = 0,
                    retryMessage = null
                )
            }

            // Try to play the channel with retry mechanism
            retryManager.startRetry(viewModelScope) {
                try {
                    playChannelInternal(channel)
                    true
                } catch (e: Exception) {
                    Log.e("ChannelsViewModel", "Failed to play channel: ${e.message}", e)
                    false
                }
            }
        }
    }

    private suspend fun playChannelInternal(channel: LiveStream): Boolean {
        return try {
            val streamUrl = buildStreamUrl(channel)
            val vlcOptions = preferenceManager.getVLCOptions()
            val media = Media(libVLC, streamUrl.toUri()).apply {
                vlcOptions.forEach { addOption(it) }
            }

            // Use MediaManager for safe media handling
            mediaManager.setMediaSafely(mediaPlayer, media)
            mediaPlayer.play()
            mediaPlayer.volume = if (_uiState.value.isMuted || _uiState.value.playerStatus == PlayerStatus.PAUSED) 0 else 100
            applyAspectRatio(_uiState.value.currentAspectRatioMode)
            _uiState.update { it.copy(playerStatus = PlayerStatus.BUFFERING) }
            
            // Wait for VLC to actually start playing or fail (max 10 seconds)
            var attempts = 0
            val maxAttempts = 100 // 10 seconds (100ms intervals)
            
            while (attempts < maxAttempts) {
                delay(100) // Check every 100ms
                attempts++
                
                when {
                    mediaPlayer.isPlaying -> {
                        Log.d("ChannelsViewModel", "Stream successfully started playing")
                        return true
                    }
                    _uiState.value.playerStatus == PlayerStatus.ERROR -> {
                        Log.e("ChannelsViewModel", "VLC reported error during playback attempt")
                        return false
                    }
                }
            }
            
            // Timeout - VLC didn't start playing within reasonable time
            Log.e("ChannelsViewModel", "Timeout waiting for stream to start playing")
            _uiState.update { it.copy(playerStatus = PlayerStatus.ERROR) }
            false
            
        } catch (e: Exception) {
            Log.e("ChannelsViewModel", "Error in playChannelInternal", e)
            _uiState.update { it.copy(playerStatus = PlayerStatus.ERROR) }
            throw e
        }
    }

    fun setInitialSystemValues(volume: Int, maxVolume: Int, brightness: Float) {
        _uiState.update {
            it.copy(
                systemVolume = volume,
                maxSystemVolume = maxVolume,
                originalBrightness = brightness,
                screenBrightness = brightness
            )
        }
    }

    fun updateSystemVolume(newVolume: Int) {
        _uiState.update { it.copy(systemVolume = newVolume) }
    }

    fun setScreenBrightness(newBrightness: Float) {
        _uiState.update { it.copy(screenBrightness = newBrightness.coerceIn(0f, 1f)) }
    }

    fun onToggleMute(audioManager: AudioManager) {
        val isMuted = !_uiState.value.isMuted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val adjustDirection = if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustDirection, 0)
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
        }
        _uiState.update { it.copy(isMuted = isMuted) }
    }

    private fun updateTrackInfo() {
        val audioTracks = mediaPlayer.audioTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.audioTrack)
        } ?: emptyList()
        val subtitleTracks = mediaPlayer.spuTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.spuTrack)
        } ?: emptyList()
        _uiState.update {
            it.copy(
                availableAudioTracks = audioTracks,
                availableSubtitleTracks = subtitleTracks
            )
        }
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer.setAudioTrack(trackId)
        toggleAudioMenu(false)
        updateTrackInfo()
    }

    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer.setSpuTrack(trackId)
        toggleSubtitleMenu(false)
        updateTrackInfo()
    }

    fun toggleAudioMenu(show: Boolean) = _uiState.update { it.copy(showAudioMenu = show) }
    fun toggleSubtitleMenu(show: Boolean) = _uiState.update { it.copy(showSubtitleMenu = show) }

    fun onToggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun setInPipMode(isInPip: Boolean) {
        _uiState.update { it.copy(isInPipMode = isInPip) }
    }

    override fun onCleared() {
        super.onCleared()
        if (::retryManager.isInitialized) {
            retryManager.cancelRetry()
        }
        mediaPlayer.stop()
        mediaPlayer.setEventListener(null)
        mediaManager.releaseCurrentMedia(mediaPlayer)
        mediaPlayer.release()
        libVLC.release()
    }

    open fun toggleFavorite(channelId: String) {
        _favoriteChannelIds.update { currentFavorites ->
            if (currentFavorites.contains(channelId)) {
                currentFavorites - channelId
            } else {
                currentFavorites + channelId
            }
        }
    }

    open fun playNextChannel() {
        viewModelScope.launch {
            val allVisibleChannels = (if (_uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                    _uiState.value.categories.flatMap { it.channels }
            val currentIndex = _uiState.value.currentChannelIndex
            if (currentIndex != -1 && currentIndex < allVisibleChannels.size - 1) {
                onChannelSelected(allVisibleChannels[currentIndex + 1])
            }
        }
    }

    open fun playPreviousChannel() {
        viewModelScope.launch {
            val allVisibleChannels = (if (_uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                    _uiState.value.categories.flatMap { it.channels }
            val currentIndex = _uiState.value.currentChannelIndex
            if (currentIndex > 0) {
                onChannelSelected(allVisibleChannels[currentIndex - 1])
            }
        }
    }

    open fun hidePlayer() {
        if (::retryManager.isInitialized) {
            retryManager.cancelRetry()
        }
        mediaManager.stopAndReleaseMedia(mediaPlayer)

        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                isFullScreen = false,
                playerStatus = PlayerStatus.IDLE,
                isInPipMode = false,
                retryAttempt = 0,
                retryMessage = null
            )
        }
    }

    /**
     * Manually retry playback for current channel
     */
    fun retryCurrentChannel() {
        val currentChannel = _uiState.value.currentlyPlaying
        if (currentChannel != null) {
            onChannelSelected(currentChannel)
        }
    }

    fun refreshChannelsManually() {
        viewModelScope.launch {
            Log.d("ChannelsViewModel", "Iniciando refresco manual de canales y EPG...")
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                liveRepository.cacheLiveStreams(currentUser.username, currentUser.password, currentUser.id)
                liveRepository.cacheEpgData(currentUser.username, currentUser.password, currentUser.id)

                loadInitialChannelsAndPreloadEpg()
                syncManager.saveLastSyncTimestamp(currentUser.id, SyncManager.ContentType.LIVE_TV)
                _uiState.update { it.copy(lastUpdatedTimestamp = syncManager.getLastSyncTimestamp(currentUser.id, SyncManager.ContentType.LIVE_TV)) }
            } catch (e: Exception) {
                Log.e("ChannelsViewModel", "Error en refreshChannelsManually(): ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    open fun toggleAspectRatio() {
        val nextMode = when (_uiState.value.currentAspectRatioMode) {
            AspectRatioMode.FIT_SCREEN -> AspectRatioMode.FILL_SCREEN
            AspectRatioMode.FILL_SCREEN -> AspectRatioMode.ASPECT_16_9
            AspectRatioMode.ASPECT_16_9 -> AspectRatioMode.ASPECT_4_3
            AspectRatioMode.ASPECT_4_3 -> AspectRatioMode.FIT_SCREEN
        }
        applyAspectRatio(nextMode)
        preferenceManager.saveAspectRatioMode(nextMode.name)
        _uiState.update { it.copy(currentAspectRatioMode = nextMode) }
    }

    private fun applyAspectRatio(mode: AspectRatioMode) {
        when (mode) {
            AspectRatioMode.FIT_SCREEN -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(0.0f)
            }
            AspectRatioMode.FILL_SCREEN -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(1.0f)
            }
            AspectRatioMode.ASPECT_16_9 -> {
                mediaPlayer.setAspectRatio("16:9")
                mediaPlayer.setScale(0.0f)
            }
            AspectRatioMode.ASPECT_4_3 -> {
                mediaPlayer.setAspectRatio("4:3")
                mediaPlayer.setScale(0.0f)
            }
        }
    }

    open fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterAndSortCategories()
    }

    open fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            val currentOriginals = _originalCategories.value.toMutableList()
            val categoryIndex = currentOriginals.indexOfFirst { it.category.categoryId == categoryId }

            if (categoryIndex != -1) {
                val category = currentOriginals[categoryIndex]
                val isNowExpanding = !category.isExpanded

                if (isNowExpanding) {
                    for (i in currentOriginals.indices) {
                        currentOriginals[i] = currentOriginals[i].copy(isExpanded = false)
                    }
                    _uiState.update { it.copy(isFavoritesCategoryExpanded = false) }
                }

                val updatedCategory = if (isNowExpanding && !category.epgLoaded) {
                    val enrichedChannels = liveRepository.enrichChannelsWithEpg(category.channels, epgCacheMap)
                    category.copy(
                        channels = enrichedChannels,
                        isExpanded = true,
                        epgLoaded = true
                    )
                } else {
                    category.copy(isExpanded = isNowExpanding)
                }
                currentOriginals[categoryIndex] = updatedCategory

                _originalCategories.value = currentOriginals
                filterAndSortCategories()

                _scrollToItemEvent.emit(categoryId)
            }
        }
    }

    private fun loadInitialChannelsAndPreloadEpg() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val categories = liveRepository.getLiveCategories(currentUser.username, currentUser.password, currentUser.id)
                Log.d("ChannelsViewModel", "Categorías cargadas: ${categories.size}")
                categories.forEach { category ->
                    Log.d("ChannelsViewModel", "Categoría: ${category.categoryName} (ID: ${category.categoryId})")
                }
                
                val allChannels = liveRepository.getRawLiveStreams(currentUser.id).first()
                Log.d("ChannelsViewModel", "Canales cargados: ${allChannels.size}")
                
                val channelsByCategory = allChannels.groupBy { it.categoryId }
                Log.d("ChannelsViewModel", "Canales agrupados por categoría: ${channelsByCategory.keys}")
                
                val expandableCategories = categories.map { category ->
                    val channelsInCategory = channelsByCategory[category.categoryId] ?: emptyList()
                    Log.d("ChannelsViewModel", "Categoría ${category.categoryName}: ${channelsInCategory.size} canales")
                    ExpandableCategory(
                        category = category,
                        channels = channelsInCategory
                    )
                }
                _originalCategories.value = expandableCategories
                filterAndSortCategories()
                _uiState.update { it.copy(isLoading = false, totalChannelCount = allChannels.size) }

                epgCacheMap = liveRepository.getAllEpgMapForUser(currentUser.id)

                if (liveRepository.isEpgDataStale(currentUser.id)) {
                    Log.d("ChannelsViewModel", "La EPG está desactualizada. Iniciando actualización automática.")
                    launch {
                        _uiState.update { it.copy(isEpgUpdating = true, epgUpdateMessage = "Actualizando guía de canales...") }
                        liveRepository.cacheEpgData(currentUser.username, currentUser.password, currentUser.id)
                        epgCacheMap = liveRepository.getAllEpgMapForUser(currentUser.id)
                        _uiState.update { it.copy(isEpgUpdating = false, epgUpdateMessage = "¡Guía actualizada!") }
                        delay(3000)
                        _uiState.update { it.copy(epgUpdateMessage = null) }
                    }
                }

            } catch (e: Exception) {
                Log.e("ChannelsViewModel", "Error en loadInitialChannelsAndPreloadEpg(): ${e.message}", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun filterAndSortCategories() {
        val query = _uiState.value.searchQuery.trim()
        val masterList = _originalCategories.value
        val currentCategorySortOrder = _uiState.value.categorySortOrder
        val currentChannelSortOrder = _uiState.value.channelSortOrder

        Log.d("ChannelsViewModel", "Filtrando categorías: ${masterList.size} categorías originales")

        // --- ¡LÓGICA DE CONTROL PARENTAL APLICADA! ---
        // Use ParentalControlManager for filtering
        val categoriesToDisplay = parentalControlManager.filterCategories(masterList) { it.category.categoryId }
        
        Log.d("ChannelsViewModel", "Control parental habilitado: ${parentalControlManager.isParentalControlEnabled()}")
        Log.d("ChannelsViewModel", "Categorías después del filtro parental: ${categoriesToDisplay.size}")
        // --- FIN DE LA LÓGICA ---

        val filteredCategories = categoriesToDisplay.map { originalCategory ->
            val filteredChannels = originalCategory.channels.filter {
                it.name.contains(query, ignoreCase = true)
            }
            originalCategory.copy(channels = filteredChannels)
        }.filter {
            it.category.categoryName.contains(query, ignoreCase = true) || it.channels.isNotEmpty() || query.isBlank()
        }

        val sortedChannelsInCategories = filteredCategories.map { category ->
            val sortedChannels = when (currentChannelSortOrder) {
                SortOrder.AZ -> category.channels.sortedBy { it.name }
                SortOrder.ZA -> category.channels.sortedByDescending { it.name }
                SortOrder.DEFAULT -> category.channels
            }
            category.copy(channels = sortedChannels)
        }

        val finalSortedCategories = when (currentCategorySortOrder) {
            SortOrder.AZ -> sortedChannelsInCategories.sortedBy { it.category.categoryName }
            SortOrder.ZA -> sortedChannelsInCategories.sortedByDescending { it.category.categoryName }
            SortOrder.DEFAULT -> sortedChannelsInCategories
        }

        Log.d("ChannelsViewModel", "Categorías finales mostradas: ${finalSortedCategories.size}")
        finalSortedCategories.forEach { category ->
            Log.d("ChannelsViewModel", "- ${category.category.categoryName}: ${category.channels.size} canales")
        }

        _uiState.update { it.copy(categories = finalSortedCategories) }
    }

    internal suspend fun getFavoriteChannels(): List<LiveStream> {
        val allChannels = _originalCategories.value.flatMap { it.channels }.distinctBy { it.streamId }
        val query = _uiState.value.searchQuery.trim()
        val currentChannelSortOrder = _uiState.value.channelSortOrder

        val favoriteChannels = allChannels.filter {
            it.streamId.toString() in _uiState.value.favoriteChannelIds &&
                    it.name.contains(query, ignoreCase = true)
        }
        return when (currentChannelSortOrder) {
            SortOrder.AZ -> favoriteChannels.sortedBy { it.name }
            SortOrder.ZA -> favoriteChannels.sortedByDescending { it.name }
            SortOrder.DEFAULT -> favoriteChannels
        }
    }

    private fun buildStreamUrl(channel: LiveStream): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }

    open fun onFavoritesCategoryToggled() {
        viewModelScope.launch {
            val isNowExpanding = !_uiState.value.isFavoritesCategoryExpanded

            if (isNowExpanding) {
                val collapsedCategories = _originalCategories.value.map { it.copy(isExpanded = false) }
                _originalCategories.value = collapsedCategories
            }

            _uiState.update { it.copy(isFavoritesCategoryExpanded = isNowExpanding) }

            if(isNowExpanding) {
                val favoriteChannels = getFavoriteChannels()
                val enrichedFavorites = liveRepository.enrichChannelsWithEpg(favoriteChannels, epgCacheMap)

                val originals = _originalCategories.value.toMutableList()
                enrichedFavorites.forEach { enrichedFav ->
                    originals.forEachIndexed { catIndex, cat ->
                        val channelIndex = cat.channels.indexOfFirst { it.streamId == enrichedFav.streamId }
                        if (channelIndex != -1) {
                            val mutableChannels = cat.channels.toMutableList()
                            mutableChannels[channelIndex] = enrichedFav
                            originals[catIndex] = cat.copy(channels = mutableChannels)
                        }
                    }
                }
                _originalCategories.value = originals
                filterAndSortCategories()
            }

            _scrollToItemEvent.emit("favorites")
        }
    }

    fun toggleSortMenu(show: Boolean) {
        _uiState.update { it.copy(showSortMenu = show) }
    }

    fun setCategorySortOrder(sortOrder: SortOrder) {
        preferenceManager.saveSortOrder("category", sortOrder.name)
        _uiState.update { it.copy(categorySortOrder = sortOrder) }
        filterAndSortCategories()
    }

    fun setChannelSortOrder(sortOrder: SortOrder) {
        preferenceManager.saveSortOrder("channel", sortOrder.name)
        _uiState.update { it.copy(channelSortOrder = sortOrder) }
        filterAndSortCategories()
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Nunca"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
        return sdf.format(Date(timestamp))
    }

    /**
     * Updates player settings dynamically when preferences change.
     * This allows immediate application of new settings without restart.
     */
    fun updatePlayerSettings() {
        // Only update if media is currently playing
        if (mediaPlayer.isPlaying) {
            val currentPosition = mediaPlayer.time
            val currentMedia = mediaPlayer.media
            
            // Recreate media with new VLC options
            currentMedia?.let { media ->
                val newOptions = preferenceManager.getVLCOptions()
                val newMedia = Media(libVLC, media.uri).apply {
                    newOptions.forEach { addOption(it) }
                }
                mediaPlayer.media?.release()
                mediaPlayer.media = newMedia
                mediaPlayer.play()
                mediaPlayer.time = currentPosition
            }
        }
    }
}
