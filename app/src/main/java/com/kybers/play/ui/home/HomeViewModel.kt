package com.kybers.play.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.BuildConfig
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class HomeContentItem {
    data class MovieItem(val movie: Movie) : HomeContentItem()
    data class SeriesItem(val series: Series) : HomeContentItem()
    data class LiveChannelItem(val channel: LiveStream) : HomeContentItem()
    data class ContinueWatchingItem(
        val item: Any,
        val progress: Float,
        val streamId: Int
    ) : HomeContentItem()
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val bannerContent: List<Pair<Movie, String?>> = emptyList(),
    val continueWatchingItems: List<HomeContentItem.ContinueWatchingItem> = emptyList(),
    val liveNowItems: List<HomeContentItem.LiveChannelItem> = emptyList(),
    val recentlyAddedMovies: List<HomeContentItem.MovieItem> = emptyList(),
    val recentlyAddedSeries: List<HomeContentItem.SeriesItem> = emptyList(),
    val favoriteItems: List<HomeContentItem> = emptyList()
)

class HomeViewModel(
    private val vodRepository: VodRepository,
    private val liveRepository: LiveRepository,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAllHomeContent()
    }

    private fun loadAllHomeContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, userName = currentUser.profileName) }

            try {
                val bannerJob = async { loadBannerContent() }
                val continueWatchingJob = async { loadContinueWatching() }
                val liveNowJob = async { loadLiveNow() }
                val recentMoviesJob = async { loadRecentlyAddedMovies() }
                val recentSeriesJob = async { loadRecentlyAddedSeries() }
                val favoritesJob = async { loadFavorites() }

                val results = awaitAll(
                    bannerJob, continueWatchingJob, liveNowJob, recentMoviesJob, recentSeriesJob, favoritesJob
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bannerContent = results[0] as List<Pair<Movie, String?>>,
                        continueWatchingItems = results[1] as List<HomeContentItem.ContinueWatchingItem>,
                        liveNowItems = results[2] as List<HomeContentItem.LiveChannelItem>,
                        recentlyAddedMovies = results[3] as List<HomeContentItem.MovieItem>,
                        recentlyAddedSeries = results[4] as List<HomeContentItem.SeriesItem>,
                        favoriteItems = results[5] as List<HomeContentItem>
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error al cargar el contenido de la pantalla de inicio", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadBannerContent(): List<Pair<Movie, String?>> {
        val localMovies = vodRepository.getAllMovies(currentUser.id).first()
        val localTmdbIds = localMovies.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
        if (localTmdbIds.isEmpty()) return emptyList()

        try {
            val response = externalApiService.getPopularMoviesTMDb(apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) {
                val popularTmdbMovies = response.body()?.results ?: emptyList()
                val availablePopular = popularTmdbMovies
                    .filter { popularTmdbMovie -> localTmdbIds.contains(popularTmdbMovie.id) }
                    .mapNotNull { popularTmdbMovie ->
                        localMovies.find { it.tmdbId == popularTmdbMovie.id.toString() }
                    }
                    .take(5)

                return availablePopular.map { movie ->
                    val details = detailsRepository.getMovieDetails(movie)
                    movie to details.details?.backdropUrl
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "No se pudieron cargar las películas para el banner", e)
        }
        return emptyList()
    }

    private suspend fun loadContinueWatching(): List<HomeContentItem.ContinueWatchingItem> {
        val moviePositions = preferenceManager.getAllPlaybackPositions()
        val resumeMovies = if (moviePositions.isNotEmpty()) {
            val allMovies = vodRepository.getAllMovies(currentUser.id).first()
            moviePositions.mapNotNull { (id, position) ->
                val movie = allMovies.find { it.streamId.toString() == id }
                if (movie != null && position > 10000) {
                    HomeContentItem.ContinueWatchingItem(movie, 0.5f, movie.streamId)
                } else null
            }
        } else emptyList()

        return resumeMovies.take(10)
    }

    private suspend fun loadLiveNow(): List<HomeContentItem.LiveChannelItem> {
        val channels = liveRepository.getRawLiveStreams(currentUser.id).first()
        if (channels.isEmpty()) return emptyList()

        val epgMap = liveRepository.getAllEpgMapForUser(currentUser.id)
        val enrichedChannels = liveRepository.enrichChannelsWithEpg(channels, epgMap)

        return enrichedChannels
            .filter { it.currentEpgEvent != null }
            .shuffled()
            .take(10)
            .map { HomeContentItem.LiveChannelItem(it) }
    }

    private suspend fun loadRecentlyAddedMovies(): List<HomeContentItem.MovieItem> {
        return vodRepository.getAllMovies(currentUser.id).first()
            .sortedByDescending { it.added }
            .take(10)
            .map { HomeContentItem.MovieItem(it) }
    }

    private suspend fun loadRecentlyAddedSeries(): List<HomeContentItem.SeriesItem> {
        return vodRepository.getAllSeries(currentUser.id).first()
            .sortedByDescending { it.lastModified }
            .take(10)
            .map { HomeContentItem.SeriesItem(it) }
    }

    private suspend fun loadFavorites(): List<HomeContentItem> {
        val favMovieIds = preferenceManager.getFavoriteMovieIds()
        val favSeriesIds = preferenceManager.getFavoriteSeriesIds()

        val favMovies = if (favMovieIds.isNotEmpty()) {
            vodRepository.getAllMovies(currentUser.id).first()
                .filter { favMovieIds.contains(it.streamId.toString()) }
                .map { HomeContentItem.MovieItem(it) }
        } else emptyList()

        val favSeries = if (favSeriesIds.isNotEmpty()) {
            vodRepository.getAllSeries(currentUser.id).first()
                .filter { favSeriesIds.contains(it.seriesId.toString()) }
                .map { HomeContentItem.SeriesItem(it) }
        } else emptyList()

        return (favMovies + favSeries).shuffled().take(10)
    }
}
