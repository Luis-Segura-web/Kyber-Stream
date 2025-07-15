package com.kybers.play.ui.channels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Representa una categoría junto con su lista de canales y si está expandida.
 */
data class ExpandableCategory(
    val category: Category,
    val channels: List<LiveStream> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * El estado completo de la UI para la nueva pantalla de Canales.
 */
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
    val isCurrentlyPlayingTvChannel: Boolean = false // ¡NUEVO! Indica si el canal actual es de TV
)

/**
 * ViewModel para la nueva pantalla de Canales con reproductor integrado.
 * Usamos AndroidViewModel para poder gestionar la instancia de ExoPlayer.
 */
open class ChannelsViewModel(
    application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : AndroidViewModel(application) {

    open val player: Player = ExoPlayer.Builder(application)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build()

    protected val _uiState = MutableStateFlow(ChannelsUiState())
    open val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        loadInitialCategories()
        viewModelScope.launch {
            _favoriteChannelIds.collect { favorites ->
                _uiState.update { it.copy(favoriteChannelIds = favorites) }
                filterCategories()
            }
        }
    }

    /**
     * Loads initial live channel categories.
     */
    private fun loadInitialCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val categories = contentRepository.getLiveCategories(currentUser.username, currentUser.password)
            val expandableCategories = categories.map { ExpandableCategory(category = it) }
            _originalCategories.value = expandableCategories
            _uiState.update { it.copy(isLoading = false, categories = expandableCategories) }
        }
    }

    /**
     * Called when the user taps a category to expand or collapse it.
     * Ensures only one category is expanded at a time.
     */
    open fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFavoritesCategoryExpanded = false) }

            val currentCategories = _originalCategories.value.toMutableList()
            val categoryIndex = currentCategories.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex == -1) return@launch

            val item = currentCategories[categoryIndex]

            if (item.isExpanded) {
                currentCategories[categoryIndex] = item.copy(isExpanded = false)
            } else {
                val newCategories = currentCategories.map { cat ->
                    if (cat.category.categoryId == categoryId) {
                        cat.copy(isExpanded = true)
                    } else {
                        cat.copy(isExpanded = false)
                    }
                }.toMutableList()
                _originalCategories.value = newCategories

                if (item.channels.isEmpty()) {
                    newCategories[categoryIndex] = newCategories[categoryIndex].copy(isLoading = true)
                    _originalCategories.value = newCategories
                    filterCategories()

                    val categoryIdAsInt = categoryId.toIntOrNull() ?: return@launch
                    val channels = contentRepository.getLiveStreams(currentUser.username, currentUser.password, categoryIdAsInt)
                    newCategories[categoryIndex] = newCategories[categoryIndex].copy(channels = channels, isLoading = false)
                }
                _originalCategories.value = newCategories
            }
            filterCategories()
        }
    }

    /**
     * Called when the user taps the Favorites category.
     * Ensures only the Favorites category (or none) is expanded.
     */
    open fun onFavoritesCategoryToggled() {
        viewModelScope.launch {
            val collapsedCategories = _originalCategories.value.map { it.copy(isExpanded = false) }
            _originalCategories.value = collapsedCategories

            _uiState.update { it.copy(isFavoritesCategoryExpanded = !it.isFavoritesCategoryExpanded) }
            filterCategories()
        }
    }

    /**
     * Called when the user selects a channel to play.
     * Updates the index of the currently playing channel.
     */
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
                isCurrentlyPlayingTvChannel = (channel.streamType == "live") // ¡NUEVO! Determina si es un canal de TV
            )
        }
        val streamUrl = buildStreamUrl(channel)
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Toggles the favorite status of a channel.
     * For now, this is in-memory. In a later step, we'll integrate Room for persistence.
     */
    open fun toggleFavorite(channelId: String) {
        _favoriteChannelIds.update { currentFavorites ->
            if (currentFavorites.contains(channelId)) {
                currentFavorites - channelId
            } else {
                currentFavorites + channelId
            }
        }
    }

    /**
     * Plays the next channel in the visible list.
     */
    open fun playNextChannel() {
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val currentIndex = _uiState.value.currentChannelIndex

        if (currentIndex != -1 && currentIndex < allVisibleChannels.size - 1) {
            val nextChannel = allVisibleChannels[currentIndex + 1]
            onChannelSelected(nextChannel)
        } else if (currentIndex == allVisibleChannels.size - 1) {
            // Optional: If it's the last, you could loop back to the first.
        }
    }

    /**
     * Plays the previous channel in the visible list.
     */
    open fun playPreviousChannel() {
        val allVisibleChannels = (if (uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                _uiState.value.categories.flatMap { it.channels }
        val currentIndex = _uiState.value.currentChannelIndex

        if (currentIndex > 0) {
            val previousChannel = allVisibleChannels[currentIndex - 1]
            onChannelSelected(previousChannel)
        } else if (currentIndex == 0) {
            // Optional: If it's the first, you could loop to the last.
        }
    }

    /**
     * Hides the player and resets the screen title.
     */
    open fun hidePlayer() {
        player.stop()
        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                screenTitle = "Canales",
                currentlyPlaying = null,
                currentChannelIndex = -1,
                isCurrentlyPlayingTvChannel = false // Resetea también este estado
            )
        }
    }

    /**
     * Called when the search query text changes.
     */
    open fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterCategories()
    }

    /**
     * Filters categories and channels based on the search query,
     * and now also manages the favorite channels list based on its expanded state.
     */
    protected fun filterCategories() {
        val query = _uiState.value.searchQuery
        val filteredList = if (query.isBlank()) {
            _originalCategories.value.map { it.copy(isExpanded = it.isExpanded) }
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

    /**
     * Helper function to get the list of favorite channels.
     */
    protected fun getFavoriteChannels(): List<LiveStream> {
        return _originalCategories.value
            .flatMap { it.channels }
            .filter { it.streamId.toString() in _uiState.value.favoriteChannelIds }
    }


    /**
     * Builds the full stream URL for ExoPlayer.
     */
    private fun buildStreamUrl(channel: LiveStream): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }

    /**
     * Called when the ViewModel is about to be destroyed.
     * Very important! Release player resources to prevent memory leaks.
     */
    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
