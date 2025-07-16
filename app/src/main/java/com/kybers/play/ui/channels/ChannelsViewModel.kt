package com.kybers.play.ui.channels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    // ¡NUEVO! Para mostrar mensajes temporales en el reproductor
    val playerToastMessage: String? = null
)

open class ChannelsViewModel(
    application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : AndroidViewModel(application) {

    open val player: Player = ExoPlayer.Builder(application).build()

    protected val _uiState = MutableStateFlow(ChannelsUiState())
    open val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            updateTrackInfo(tracks)
        }
    }

    init {
        player.addListener(playerListener)
        loadInitialCategories()
        viewModelScope.launch {
            _favoriteChannelIds.collect { favorites ->
                _uiState.update { it.copy(favoriteChannelIds = favorites) }
                filterCategories()
            }
        }
    }

    // ¡NUEVO! Función para mostrar un mensaje y ocultarlo después de un tiempo
    fun showPlayerToast(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(playerToastMessage = message) }
            delay(2000) // Muestra el mensaje por 2 segundos
            _uiState.update { it.copy(playerToastMessage = null) }
        }
    }

    private fun updateTrackInfo(tracks: Tracks) {
        val audioTracks = tracks.groups.mapIndexedNotNull { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                (0 until group.length).map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    TrackInfo(
                        id = format.id ?: "$groupIndex:$trackIndex",
                        language = format.language ?: "Desconocido",
                        label = format.label ?: "Pista ${trackIndex + 1}",
                        isSelected = group.isTrackSelected(trackIndex)
                    )
                }
            } else null
        }.flatten()

        val subtitleTracks = tracks.groups.mapIndexedNotNull { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                (0 until group.length).map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    TrackInfo(
                        id = format.id ?: "$groupIndex:$trackIndex",
                        language = format.language ?: "Desconocido",
                        label = format.label ?: "Subtítulo ${trackIndex + 1}",
                        isSelected = group.isTrackSelected(trackIndex)
                    )
                }
            } else null
        }.flatten()

        _uiState.update {
            it.copy(
                availableAudioTracks = audioTracks,
                availableSubtitleTracks = subtitleTracks,
                hasSubtitles = subtitleTracks.isNotEmpty()
            )
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
                newCategories[categoryIndex] = newCategories[categoryIndex].copy(isExpanded = true)

                if (newCategories[categoryIndex].channels.isEmpty()) {
                    newCategories[categoryIndex] = newCategories[categoryIndex].copy(isLoading = true)
                    _originalCategories.value = newCategories
                    filterCategories()

                    val categoryIdAsInt = categoryId.toIntOrNull() ?: return@launch
                    val channels = contentRepository.getLiveStreams(currentUser.username, currentUser.password, categoryIdAsInt)
                    newCategories[categoryIndex] = newCategories[categoryIndex].copy(channels = channels, isLoading = false)
                }
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
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun selectSubtitle(trackId: String) { /* Lógica futura */ }
    fun selectAudioTrack(trackId: String) { /* Lógica futura */ }
    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
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
        player.stop()
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
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }

    override fun onCleared() {
        super.onCleared()
        player.removeListener(playerListener)
        player.release()
    }
}
