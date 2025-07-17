package com.kybers.play.ui.channels

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

enum class PlayerStatus {
    IDLE, BUFFERING, PLAYING, ERROR
}

data class ExpandableCategory(
    val category: Category,
    val channels: List<LiveStream> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false
)

data class TrackInfo(
    val id: Int,
    val name: String,
    val isSelected: Boolean
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
    val volume: Int = 100,
    val brightness: Float = 0.5f,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val availableVideoTracks: List<TrackInfo> = emptyList(),
    val showAudioMenu: Boolean = false,
    val showSubtitleMenu: Boolean = false,
    val showVideoMenu: Boolean = false
)

open class ChannelsViewModel(
    application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : AndroidViewModel(application) {

    private lateinit var libVLC: LibVLC
    lateinit var mediaPlayer: MediaPlayer
        private set

    private val _uiState = MutableStateFlow(ChannelsUiState())
    open val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())

    // ¡NUEVO! Lista de opciones para estabilizar la reproducción.
    private val vlcOptions = arrayListOf(
        // Caché de red y 'live' (en ms)
        "--network-caching=50000",
        "--live-caching=50000",
        // Caché de archivo y de multiplexado (en ms)
        "--file-caching=50000",
        "--sout-mux-caching=50000",
        // Usar libavcodec y prioridad para evitar retrasos
        "--codec=avcodec",
        "--avcodec-fast",
        // Sincronización de reloj maestro y cero jitter
        "--clock-jitter=0",
        "--clock-synchro=master",
        // Evitar desechar o saltar fotogramas tardíos
        "--no-drop-late-frames",
        "--no-skip-frames"
    )

    private val vlcPlayerListener = MediaPlayer.EventListener { event ->
        val currentState = _uiState.value.playerStatus
        val newStatus = when (event.type) {
            MediaPlayer.Event.Playing -> {
                updateTrackInfo()
                PlayerStatus.PLAYING
            }
            MediaPlayer.Event.Buffering -> if (currentState != PlayerStatus.PLAYING) PlayerStatus.BUFFERING else null
            MediaPlayer.Event.EncounteredError -> PlayerStatus.ERROR
            MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> PlayerStatus.IDLE
            else -> null
        }
        if (newStatus != null && newStatus != currentState) {
            _uiState.update { it.copy(playerStatus = newStatus) }
        }
    }

    init {
        setupVLC()
        loadInitialCategories()
        viewModelScope.launch {
            _favoriteChannelIds.collect { favorites ->
                _uiState.update { it.copy(favoriteChannelIds = favorites) }
                filterCategories()
            }
        }
    }

    private fun setupVLC() {
        // ¡MODIFICADO! Inicializamos LibVLC con las nuevas opciones.
        libVLC = LibVLC(getApplication(), vlcOptions)
        mediaPlayer = MediaPlayer(libVLC).apply {
            setEventListener(vlcPlayerListener)
        }
    }

    open fun onChannelSelected(channel: LiveStream) {
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val index = allVisibleChannels.indexOfFirst { it.streamId == channel.streamId }

        _uiState.update {
            it.copy(
                currentlyPlaying = channel,
                isPlayerVisible = true,
                screenTitle = channel.name,
                currentChannelIndex = index,
                playerStatus = PlayerStatus.BUFFERING,
                availableAudioTracks = emptyList(),
                availableSubtitleTracks = emptyList(),
                availableVideoTracks = emptyList()
            )
        }

        val streamUrl = buildStreamUrl(channel)

        val media = Media(libVLC, streamUrl.toUri()).apply {
            // ¡MODIFICADO! Aplicamos las opciones a cada media para máxima compatibilidad.
            vlcOptions.forEach { addOption(it) }
        }

        mediaPlayer.media = media
        mediaPlayer.play()
        mediaPlayer.volume = if (_uiState.value.isMuted) 0 else _uiState.value.volume
    }

    private fun updateTrackInfo() {
        val audioTracks = mediaPlayer.audioTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.audioTrack)
        } ?: emptyList()

        val subtitleTracks = mediaPlayer.spuTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.spuTrack)
        } ?: emptyList()

        val videoTracks = mediaPlayer.videoTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.videoTrack)
        } ?: emptyList()

        _uiState.update {
            it.copy(
                availableAudioTracks = audioTracks,
                availableSubtitleTracks = subtitleTracks,
                availableVideoTracks = videoTracks
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

    fun selectVideoTrack(trackId: Int) {
        mediaPlayer.setVideoTrack(trackId)
        toggleVideoMenu(false)
        updateTrackInfo()
    }

    fun toggleAudioMenu(show: Boolean) = _uiState.update { it.copy(showAudioMenu = show) }
    fun toggleSubtitleMenu(show: Boolean) = _uiState.update { it.copy(showSubtitleMenu = show) }
    fun toggleVideoMenu(show: Boolean) = _uiState.update { it.copy(showVideoMenu = show) }

    fun retryPlayback() {
        uiState.value.currentlyPlaying?.let { onChannelSelected(it) }
    }

    fun onToggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun toggleMute() {
        val newMutedState = !_uiState.value.isMuted
        mediaPlayer.volume = if (newMutedState) 0 else _uiState.value.volume
        _uiState.update { it.copy(isMuted = newMutedState) }
    }

    fun setVolume(newVolume: Int) {
        val finalVolume = newVolume.coerceIn(0, 100)
        mediaPlayer.volume = finalVolume
        _uiState.update { it.copy(volume = finalVolume, isMuted = finalVolume == 0) }
    }

    fun setBrightness(newBrightness: Float) {
        _uiState.update { it.copy(brightness = newBrightness.coerceIn(0f, 1f)) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.stop()
        mediaPlayer.setEventListener(null)
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
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val currentIndex = _uiState.value.currentChannelIndex
        if (currentIndex != -1 && currentIndex < allVisibleChannels.size - 1) {
            onChannelSelected(allVisibleChannels[currentIndex + 1])
        }
    }

    open fun playPreviousChannel() {
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val currentIndex = _uiState.value.currentChannelIndex
        if (currentIndex > 0) {
            onChannelSelected(allVisibleChannels[currentIndex - 1])
        }
    }

    open fun hidePlayer() {
        mediaPlayer.stop()
        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                isFullScreen = false,
                playerStatus = PlayerStatus.IDLE
            )
        }
    }

    open fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterCategories()
    }

    open fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            val currentCategories = _originalCategories.value.toMutableList()
            val categoryIndex = currentCategories.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex == -1) return@launch

            val item = currentCategories[categoryIndex]
            val isCurrentlyExpanded = item.isExpanded

            val newCategories = _originalCategories.value.map {
                it.copy(isExpanded = false, isLoading = false)
            }.toMutableList()

            if (!isCurrentlyExpanded) {
                newCategories[categoryIndex] = newCategories[categoryIndex].copy(isLoading = true)
                filterCategories(newCategories) // Update UI to show loading

                val channelsFromCache = contentRepository.getLiveStreamsByCategory(categoryId).first()
                newCategories[categoryIndex] = newCategories[categoryIndex].copy(
                    isExpanded = true,
                    channels = channelsFromCache,
                    isLoading = false
                )
            }

            _originalCategories.value = newCategories
            _uiState.update { it.copy(isFavoritesCategoryExpanded = false) }
            filterCategories()
        }
    }

    private fun loadInitialCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val categories = contentRepository.getLiveCategories(currentUser.username, currentUser.password)
            val expandableCategories = categories.map { ExpandableCategory(category = it) }
            _originalCategories.value = expandableCategories
            _uiState.update { it.copy(isLoading = false, categories = expandableCategories) }
        }
    }

    private fun filterCategories(categoriesToFilter: List<ExpandableCategory> = _originalCategories.value) {
        val query = _uiState.value.searchQuery
        val filteredList = if (query.isBlank()) {
            categoriesToFilter
        } else {
            categoriesToFilter.mapNotNull { expandableCategory ->
                val filteredChannels = expandableCategory.channels.filter {
                    it.name.contains(query, ignoreCase = true)
                }
                if (filteredChannels.isNotEmpty() || expandableCategory.category.categoryName.contains(query, ignoreCase = true)) {
                    expandableCategory.copy(
                        channels = filteredChannels,
                        isExpanded = filteredChannels.isNotEmpty()
                    )
                } else {
                    null
                }
            }
        }
        _uiState.update { it.copy(categories = filteredList) }
    }

    internal fun getFavoriteChannels(): List<LiveStream> {
        val allChannels = _originalCategories.value.flatMap { it.channels }.distinctBy { it.streamId }
        return allChannels.filter { it.streamId.toString() in _uiState.value.favoriteChannelIds }
    }

    private fun buildStreamUrl(channel: LiveStream): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }

    open fun onFavoritesCategoryToggled() {
        viewModelScope.launch {
            val collapsedCategories = _originalCategories.value.map { it.copy(isExpanded = false) }
            _originalCategories.value = collapsedCategories
            _uiState.update { it.copy(isFavoritesCategoryExpanded = !it.isFavoritesCategoryExpanded) }
            filterCategories()
        }
    }
}
