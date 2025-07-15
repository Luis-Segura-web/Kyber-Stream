package com.kybers.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Define el estado de la UI para la pantalla de Inicio.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val recommendedMovies: List<Movie> = emptyList(),
    val recommendedSeries: List<Series> = emptyList()
    // Aquí podríamos añadir listas para favoritos, etc.
)

/**
 * ViewModel para la pantalla de Inicio.
 */
class HomeViewModel(
    private val contentRepository: ContentRepository,
    private val currentUser: User // El usuario activo
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Al iniciar el ViewModel, cargamos todos los datos necesarios.
        loadInitialContent()
    }

    private fun loadInitialContent() {
        viewModelScope.launch {
            // Ponemos el estado en "cargando".
            _uiState.value = HomeUiState(isLoading = true)

            // Hacemos las llamadas a la API en paralelo para más eficiencia.
            val movies = contentRepository.getMovies(currentUser.username, currentUser.password)
            val series = contentRepository.getSeries(currentUser.username, currentUser.password)

            // Actualizamos el estado con los datos cargados.
            _uiState.value = HomeUiState(
                isLoading = false,
                recommendedMovies = movies,
                recommendedSeries = series
            )
        }
    }
}
