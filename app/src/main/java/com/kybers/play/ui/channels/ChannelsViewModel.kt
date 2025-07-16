package com.kybers.play.ui.channels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import androidx.core.net.toUri

// El enum de estado sigue siendo útil
enum class PlayerStatus {
    IDLE, BUFFERING, PLAYING, ERROR
}

// ... (Las data classes como ExpandableCategory, TrackInfo y ChannelsUiState no cambian) ...
data class ExpandableCategory(
    val category: Category,
    val channels: List<LiveStream> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false
)

data class TrackInfo(
    val id: String,
    val language: String,
    val label: String,
    val isSelected: Boolean
)

data class ChannelsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val categories: List<ExpandableCategory> = emptyList(),
    val currentlyPlaying: LiveStream? = null,
    val currentlyPlayingCategoryId: String? = null,
    val isPlayerVisible: Boolean = false,
    val screenTitle: String = "Canales",
    val currentChannelIndex: Int = -1,
    val favoriteChannelIds: Set<String> = emptySet(),
    val isFavoritesCategoryExpanded: Boolean = false,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val hasSubtitles: Boolean = false,
    val isCurrentlyPlayingTvChannel: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playerToastMessage: String? = null,
    val playerStatus: PlayerStatus = PlayerStatus.IDLE
)

open class ChannelsViewModel(
    application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : AndroidViewModel(application) {

    // --- ¡NUEVA LÓGICA DE VLC! ---
    private lateinit var libVLC: LibVLC
    lateinit var mediaPlayer: MediaPlayer
        private set

    private val _uiState = MutableStateFlow(ChannelsUiState())
    open val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())

    // Listener para los eventos del reproductor VLC
    private val vlcPlayerListener = MediaPlayer.EventListener { event ->
        val newStatus = when (event.type) {
            MediaPlayer.Event.Buffering -> PlayerStatus.BUFFERING
            MediaPlayer.Event.Playing -> PlayerStatus.PLAYING
            MediaPlayer.Event.EncounteredError -> PlayerStatus.ERROR
            MediaPlayer.Event.EndReached -> PlayerStatus.IDLE
            else -> null // No actualizamos el estado para otros eventos
        }
        if (newStatus != null) {
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
        libVLC = LibVLC(getApplication())
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.setEventListener(vlcPlayerListener)
    }

    fun retryPlayback() {
        val currentItem = uiState.value.currentlyPlaying ?: return
        onChannelSelected(currentItem)
    }

    open fun onChannelSelected(channel: LiveStream) {
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val index = allVisibleChannels.indexOfFirst { it.streamId == channel.streamId }

        _uiState.update {
            it.copy(
                currentlyPlaying = channel,
                currentlyPlayingCategoryId = channel.categoryId,
                isPlayerVisible = true,
                screenTitle = channel.name,
                currentChannelIndex = index,
                isCurrentlyPlayingTvChannel = (channel.streamType == "live")
            )
        }

        val streamUrl = buildStreamUrl(channel)
        val media = Media(libVLC, streamUrl.toUri())
        // Opciones para reducir la latencia en streams en vivo
        media.addOption(":network-caching=150")
        media.addOption(":clock-jitter=0")
        media.addOption(":clock-synchro=0")

        mediaPlayer.media = media
        mediaPlayer.play()
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.stop()
        mediaPlayer.setEventListener(null)
        mediaPlayer.release()
        libVLC.release()
    }

    // El resto de las funciones (navegación, favoritos, etc.) no cambian su lógica interna,
    // solo cómo interactúan con el nuevo reproductor.
    fun showPlayerToast(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(playerToastMessage = message) }
            delay(2000)
            _uiState.update { it.copy(playerToastMessage = null) }
        }
    }

    open fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            val currentCategories = _originalCategories.value.toMutableList()
            val categoryIndex = currentCategories.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex == -1) return@launch

            val item = currentCategories[categoryIndex]
            val isCurrentlyExpanded = item.isExpanded

            val newCategories = currentCategories.map { it.copy(isExpanded = false) }.toMutableList()

            if (!isCurrentlyExpanded) {
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

    open fun onFavoritesCategoryToggled() {
        viewModelScope.launch {
            val collapsedCategories = _originalCategories.value.map { it.copy(isExpanded = false) }
            _originalCategories.value = collapsedCategories
            _uiState.update { it.copy(isFavoritesCategoryExpanded = !it.isFavoritesCategoryExpanded) }
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
            val nextChannel = allVisibleChannels[currentIndex + 1]
            onChannelSelected(nextChannel)
        }
    }

    open fun playPreviousChannel() {
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val currentIndex = _uiState.value.currentChannelIndex

        if (currentIndex > 0) {
            val previousChannel = allVisibleChannels[currentIndex - 1]
            onChannelSelected(previousChannel)
        }
    }

    open fun hidePlayer() {
        mediaPlayer.stop()
        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                screenTitle = "Canales",
                currentlyPlaying = null,
                currentlyPlayingCategoryId = null,
                currentChannelIndex = -1,
                isCurrentlyPlayingTvChannel = false
            )
        }
    }

    open fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterCategories()
    }

    protected fun filterCategories() {
        val query = _uiState.value.searchQuery
        val filteredList = if (query.isBlank()) {
            _originalCategories.value
        } else {
            _originalCategories.value.mapNotNull { expandableCategory ->
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
        // VLC es muy bueno detectando el tipo, así que podemos volver a .ts si es más común
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }
}
