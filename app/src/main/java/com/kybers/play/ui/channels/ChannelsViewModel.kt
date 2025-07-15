package com.kybers.play.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la UI para la pantalla de Canales.
 */
data class ChannelsUiState(
    val isLoadingCategories: Boolean = true,
    val isLoadingChannels: Boolean = false,
    val categories: List<Category> = emptyList(),
    val channels: List<LiveStream> = emptyList(),
    val selectedCategoryId: String? = null
)

/**
 * ViewModel para la pantalla de Canales.
 */
class ChannelsViewModel(
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true) }
            val categories = contentRepository.getLiveCategories(currentUser.username, currentUser.password)
            _uiState.update { it.copy(isLoadingCategories = false, categories = categories) }

            // Si hay categorías, seleccionamos la primera por defecto.
            categories.firstOrNull()?.let {
                onCategorySelected(it)
            }
        }
    }

    fun onCategorySelected(category: Category) {
        // Si la categoría ya está seleccionada, no hacemos nada.
        if (_uiState.value.selectedCategoryId == category.categoryId) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedCategoryId = category.categoryId,
                    isLoadingChannels = true,
                    channels = emptyList() // Limpiamos la lista anterior
                )
            }
            val categoryIdAsInt = category.categoryId.toIntOrNull() ?: return@launch
            val channels = contentRepository.getLiveStreams(currentUser.username, currentUser.password, categoryIdAsInt)
            _uiState.update { it.copy(isLoadingChannels = false, channels = channels) }
        }
    }

    /**
     * AÑADIDO: Construye la URL de reproducción para un canal en vivo.
     * El formato estándar de Xtream Codes es: {scheme}://{host}:{port}/live/{user}/{pass}/{stream_id}.ts
     * @param channel El canal para el cual construir la URL.
     * @return La URL completa del stream.
     */
    fun buildStreamUrl(channel: LiveStream): String {
        // Nos aseguramos de que la URL base del servidor termine con una barra para evitar dobles barras.
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }
}
