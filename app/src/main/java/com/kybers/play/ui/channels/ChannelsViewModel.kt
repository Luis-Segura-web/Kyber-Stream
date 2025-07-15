package com.kybers.play.ui.channels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    val currentlyPlaying: LiveStream? = null
)

/**
 * ViewModel para la nueva pantalla de Canales con reproductor integrado.
 * Usamos AndroidViewModel para poder gestionar la instancia de ExoPlayer.
 */
class ChannelsViewModel(
    application: Application, // Necesario para ExoPlayer
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : AndroidViewModel(application) {

    // La instancia del reproductor que controlará este ViewModel.
    val player: Player = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    // Un StateFlow privado para mantener la lista original sin filtrar.
    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())

    init {
        loadInitialCategories()
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

    /**
     * Se llama cuando el usuario toca una categoría para expandirla o contraerla.
     */
    fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            val currentCategories = _originalCategories.value.toMutableList()
            val categoryIndex = currentCategories.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex == -1) return@launch

            val item = currentCategories[categoryIndex]

            // Si ya está expandida, la contraemos.
            if (item.isExpanded) {
                currentCategories[categoryIndex] = item.copy(isExpanded = false)
                _originalCategories.value = currentCategories
                filterCategories() // Aplicamos el filtro actual
                return@launch
            }

            // Si no está expandida y no tiene canales, los cargamos.
            if (item.channels.isEmpty()) {
                currentCategories[categoryIndex] = item.copy(isLoading = true)
                _originalCategories.value = currentCategories
                filterCategories()

                val categoryIdAsInt = categoryId.toIntOrNull() ?: return@launch
                val channels = contentRepository.getLiveStreams(currentUser.username, currentUser.password, categoryIdAsInt)
                currentCategories[categoryIndex] = item.copy(channels = channels, isLoading = false, isExpanded = true)
            } else {
                // Si ya tiene canales, simplemente la expandimos.
                currentCategories[categoryIndex] = item.copy(isExpanded = true)
            }

            _originalCategories.value = currentCategories
            filterCategories()
        }
    }

    /**
     * Se llama cuando el usuario selecciona un canal para reproducir.
     */
    fun onChannelSelected(channel: LiveStream) {
        _uiState.update { it.copy(currentlyPlaying = channel) }
        val streamUrl = buildStreamUrl(channel)
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Se llama cuando el texto del buscador cambia.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterCategories()
    }

    private fun filterCategories() {
        val query = _uiState.value.searchQuery
        val filteredList = if (query.isBlank()) {
            _originalCategories.value
        } else {
            _originalCategories.value.mapNotNull { expandableCategory ->
                val filteredChannels = expandableCategory.channels.filter {
                    it.name.contains(query, ignoreCase = true)
                }
                if (filteredChannels.isNotEmpty()) {
                    expandableCategory.copy(channels = filteredChannels, isExpanded = true)
                } else {
                    null
                }
            }
        }
        _uiState.update { it.copy(categories = filteredList) }
    }

    private fun buildStreamUrl(channel: LiveStream): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }

    override fun onCleared() {
        super.onCleared()
        player.release() // ¡Muy importante! Liberamos el reproductor.
    }
}
