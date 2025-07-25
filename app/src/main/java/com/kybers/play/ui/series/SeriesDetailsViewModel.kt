package com.kybers.play.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Season
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.VodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class para el estado de la UI de la pantalla de detalles de una serie.
data class SeriesDetailsUiState(
    val isLoading: Boolean = true,
    val seriesInfo: Series? = null,
    val seasons: List<Season> = emptyList(),
    val episodesBySeason: Map<Int, List<Episode>> = emptyMap(),
    val selectedSeasonNumber: Int = 1,
    val error: String? = null
)

/**
 * ViewModel para la pantalla de Detalles de Series.
 */
class SeriesDetailsViewModel(
    private val vodRepository: VodRepository,
    private val currentUser: User,
    private val seriesId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesDetailsUiState())
    val uiState: StateFlow<SeriesDetailsUiState> = _uiState.asStateFlow()

    init {
        loadSeriesDetails()
    }

    private fun loadSeriesDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Primero, obtenemos el objeto 'Series' base que ya tenemos en la base de datos.
            // Este contiene el seriesId, num, etc.
            val baseSeries = vodRepository.getAllSeries(currentUser.id).first().find { it.seriesId == seriesId }
            if (baseSeries == null) {
                _uiState.update { it.copy(isLoading = false, error = "Serie no encontrada en la base de datos local.") }
                return@launch
            }

            vodRepository.getSeriesDetails(
                user = currentUser.username,
                pass = currentUser.password,
                seriesId = seriesId,
                userId = currentUser.id
            )
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = "Error al cargar detalles: ${e.message}")
                    }
                }
                .collect { seriesInfoResponse ->
                    if (seriesInfoResponse != null) {
                        val episodesMappedByInt = seriesInfoResponse.episodes.mapKeys { it.key.toInt() }

                        // --- ¡CORRECCIÓN! ---
                        // Creamos un objeto 'Series' actualizado, combinando la información base que ya teníamos
                        // con los detalles más completos que vienen de la API en el objeto 'info'.
                        val updatedSeries = baseSeries.copy(
                            plot = seriesInfoResponse.info.plot ?: baseSeries.plot,
                            cast = seriesInfoResponse.info.cast ?: baseSeries.cast,
                            director = seriesInfoResponse.info.director ?: baseSeries.director,
                            genre = seriesInfoResponse.info.genre ?: baseSeries.genre,
                            releaseDate = seriesInfoResponse.info.releaseDate ?: baseSeries.releaseDate,
                            lastModified = seriesInfoResponse.info.lastModified ?: baseSeries.lastModified,
                            rating = seriesInfoResponse.info.rating ?: baseSeries.rating,
                            rating5Based = seriesInfoResponse.info.rating5Based ?: baseSeries.rating5Based,
                            backdropPath = seriesInfoResponse.info.backdropPath ?: baseSeries.backdropPath,
                            youtubeTrailer = seriesInfoResponse.info.youtubeTrailer ?: baseSeries.youtubeTrailer,
                            episodeRunTime = seriesInfoResponse.info.episodeRunTime ?: baseSeries.episodeRunTime
                        )

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                seriesInfo = updatedSeries, // Ahora sí es un objeto 'Series'
                                seasons = seriesInfoResponse.seasons.sortedBy { s -> s.seasonNumber },
                                episodesBySeason = episodesMappedByInt,
                                selectedSeasonNumber = seriesInfoResponse.seasons.minOfOrNull { s -> s.seasonNumber } ?: 1
                            )
                        }
                    } else {
                        // Si la API falla, al menos mostramos la información básica que ya teníamos.
                        _uiState.update {
                            it.copy(isLoading = false, seriesInfo = baseSeries, error = "No se encontraron detalles para esta serie.")
                        }
                    }
                }
        }
    }

    /**
     * Actualiza la temporada seleccionada en el estado de la UI.
     */
    fun selectSeason(seasonNumber: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
    }
}
