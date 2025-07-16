package com.kybers.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            // ¡CORREGIDO! Ahora observamos los datos del caché de forma reactiva.
            combine(
                contentRepository.getAllMovies(),
                contentRepository.getAllSeries()
            ) { movies, series ->
                HomeUiState(
                    isLoading = false,
                    recommendedMovies = movies,
                    recommendedSeries = series
                )
            }.catch { throwable ->
                // Manejar errores si es necesario
                _uiState.value = HomeUiState(isLoading = false)
            }.collect {
                _uiState.value = it
            }
        }
    }
}
