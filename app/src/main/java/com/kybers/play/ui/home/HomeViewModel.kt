package com.kybers.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.Flow // Asegúrate de que Flow esté importado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect // Aunque collectAsState se usa en Compose, es bueno tenerlo si se usa directamente.
import kotlinx.coroutines.launch // <--- ¡IMPORTACIÓN CRÍTICA PARA 'launch'!

data class HomeUiState(
    val isLoading: Boolean = true,
    val recommendedMovies: List<Movie> = emptyList(),
    val recommendedSeries: List<Series> = emptyList()
)

class HomeViewModel(
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadInitialContent()
    }

    private fun loadInitialContent() {
        viewModelScope.launch { // 'launch' ahora debería ser reconocido
            // Ahora observamos los datos del caché de forma reactiva, filtrados por el ID del usuario actual.
            combine(
                contentRepository.getAllMovies(currentUser.id),
                contentRepository.getAllSeries(currentUser.id)
            ) { movies, series ->
                HomeUiState(
                    isLoading = false,
                    recommendedMovies = movies,
                    recommendedSeries = series
                )
            }.catch { throwable ->
                // Manejar errores si es necesario
                _uiState.value = HomeUiState(isLoading = false)
            }.collect { // 'collect' ahora debería ser reconocido
                _uiState.value = it
            }
        }
    }
}
