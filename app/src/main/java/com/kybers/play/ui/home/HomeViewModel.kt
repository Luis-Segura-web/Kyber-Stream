package com.kybers.play.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.VodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// El estado de la UI no necesita cambios.
data class HomeUiState(
    val isLoading: Boolean = true,
    val recommendedMovies: List<Movie> = emptyList(),
    val recommendedSeries: List<Series> = emptyList()
)

/**
 * --- ¡VIEWMODEL CORREGIDO! ---
 * ViewModel para la pantalla de Inicio.
 * Se ha añadido la importación que faltaba para 'Log'.
 *
 * @property vodRepository El repositorio especializado en contenido bajo demanda.
 * @property currentUser El perfil del usuario actual.
 */
class HomeViewModel(
    private val vodRepository: VodRepository,
    private val currentUser: User
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadInitialContent()
    }

    private fun loadInitialContent() {
        viewModelScope.launch {
            combine(
                vodRepository.getAllMovies(currentUser.id),
                vodRepository.getAllSeries(currentUser.id)
            ) { movies, series ->
                HomeUiState(
                    isLoading = false,
                    recommendedMovies = movies.take(10),
                    recommendedSeries = series.take(10)
                )
            }.catch { throwable ->
                Log.e("HomeViewModel", "Error al cargar contenido inicial", throwable)
                _uiState.value = HomeUiState(isLoading = false)
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
}
