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
import kotlinx.coroutines.coroutineScope
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
}

data class HomeCarousel(
    val title: String,
    val items: List<HomeContentItem>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val bannerContent: List<Pair<Movie, String?>> = emptyList(),
    val carousels: List<HomeCarousel> = emptyList()
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
                coroutineScope {
                    val bannerJob = async { loadBannerContent() }
                    val carouselsJob = async { loadCarousels() }

                    val bannerContent = bannerJob.await()
                    val carousels = carouselsJob.await()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bannerContent = bannerContent,
                            carousels = carousels
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error al cargar el contenido de la pantalla de inicio", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadCarousels(): List<HomeCarousel> = coroutineScope {
        val carousels = mutableListOf<HomeCarousel>()

        val parentalControlEnabled = preferenceManager.isParentalControlEnabled()
        val blockedCategoryIds = preferenceManager.getBlockedCategories()

        val continueMoviesJob = async { loadContinueMovies(parentalControlEnabled, blockedCategoryIds) }
        val liveNowJob = async { loadLiveNow(parentalControlEnabled, blockedCategoryIds) }
        val recentMoviesJob = async { loadRecentlyAddedMovies(parentalControlEnabled, blockedCategoryIds) }
        val recentSeriesJob = async { loadRecentlyAddedSeries(parentalControlEnabled, blockedCategoryIds) }
        val favMoviesJob = async { loadFavoriteMovies(parentalControlEnabled, blockedCategoryIds) }
        val favSeriesJob = async { loadFavoriteSeries(parentalControlEnabled, blockedCategoryIds) }

        val continueMovies = continueMoviesJob.await()
        val liveNow = liveNowJob.await()
        val recentMovies = recentMoviesJob.await()
        val recentSeries = recentSeriesJob.await()
        val favMovies = favMoviesJob.await()
        val favSeries = favSeriesJob.await()

        if (continueMovies.isNotEmpty()) carousels.add(HomeCarousel("Continuar Viendo Películas", continueMovies))
        if (liveNow.isNotEmpty()) carousels.add(HomeCarousel("En Directo Ahora", liveNow))
        if (recentMovies.isNotEmpty()) carousels.add(HomeCarousel("Películas Recientes", recentMovies))
        if (recentSeries.isNotEmpty()) carousels.add(HomeCarousel("Series Recientes", recentSeries))
        if (favMovies.isNotEmpty()) carousels.add(HomeCarousel("Mis Películas Favoritas", favMovies))
        if (favSeries.isNotEmpty()) carousels.add(HomeCarousel("Mis Series Favoritas", favSeries))

        return@coroutineScope carousels
    }

    private suspend fun loadBannerContent(): List<Pair<Movie, String?>> {
        val parentalControlEnabled = preferenceManager.isParentalControlEnabled()
        val blockedCategoryIds = preferenceManager.getBlockedCategories()
        val localMovies = vodRepository.getAllMovies(currentUser.id).first()
            .filter { if (parentalControlEnabled) !blockedCategoryIds.contains(it.categoryId) else true }

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

    private suspend fun loadContinueMovies(parentalControlEnabled: Boolean, blockedIds: Set<String>): List<HomeContentItem.MovieItem> {
        val limit = preferenceManager.getRecentlyWatchedLimit()
        val moviePositions = preferenceManager.getAllPlaybackPositions()
        return if (moviePositions.isNotEmpty()) {
            val allMovies = vodRepository.getAllMovies(currentUser.id).first()
            moviePositions
                .filter { (_, position) -> position > 10000 }
                .mapNotNull { (id, _) -> allMovies.find { it.streamId.toString() == id } }
                .filter { if (parentalControlEnabled) !blockedIds.contains(it.categoryId) else true }
                .take(limit)
                .map { HomeContentItem.MovieItem(it) }
        } else emptyList()
    }

    private suspend fun loadLiveNow(parentalControlEnabled: Boolean, blockedIds: Set<String>): List<HomeContentItem.LiveChannelItem> {
        val channels = liveRepository.getRawLiveStreams(currentUser.id).first()
            .filter { if (parentalControlEnabled) !blockedIds.contains(it.categoryId) else true }
        if (channels.isEmpty()) return emptyList()

        val epgMap = liveRepository.getAllEpgMapForUser(currentUser.id)
        val enrichedChannels = liveRepository.enrichChannelsWithEpg(channels, epgMap)

        return enrichedChannels
            .filter { it.currentEpgEvent != null }
            .shuffled()
            .take(10)
            .map { HomeContentItem.LiveChannelItem(it) }
    }

    private suspend fun loadRecentlyAddedMovies(parentalControlEnabled: Boolean, blockedIds: Set<String>): List<HomeContentItem.MovieItem> {
        return vodRepository.getAllMovies(currentUser.id).first()
            .filter { if (parentalControlEnabled) !blockedIds.contains(it.categoryId) else true }
            .sortedByDescending { it.added }
            .take(10)
            .map { HomeContentItem.MovieItem(it) }
    }

    private suspend fun loadRecentlyAddedSeries(parentalControlEnabled: Boolean, blockedIds: Set<String>): List<HomeContentItem.SeriesItem> {
        return vodRepository.getAllSeries(currentUser.id).first()
            .filter { if (parentalControlEnabled) !blockedIds.contains(it.categoryId) else true }
            .sortedByDescending { it.lastModified }
            .take(10)
            .map { HomeContentItem.SeriesItem(it) }
    }

    private suspend fun loadFavoriteMovies(parentalControlEnabled: Boolean, blockedIds: Set<String>): List<HomeContentItem.MovieItem> {
        val favMovieIds = preferenceManager.getFavoriteMovieIds()
        return if (favMovieIds.isNotEmpty()) {
            vodRepository.getAllMovies(currentUser.id).first()
                .filter { favMovieIds.contains(it.streamId.toString()) }
                .filter { if (parentalControlEnabled) !blockedIds.contains(it.categoryId) else true }
                .map { HomeContentItem.MovieItem(it) }
        } else emptyList()
    }

    private suspend fun loadFavoriteSeries(parentalControlEnabled: Boolean, blockedIds: Set<String>): List<HomeContentItem.SeriesItem> {
        val favSeriesIds = preferenceManager.getFavoriteSeriesIds()
        return if (favSeriesIds.isNotEmpty()) {
            vodRepository.getAllSeries(currentUser.id).first()
                .filter { favSeriesIds.contains(it.seriesId.toString()) }
                .filter { if (parentalControlEnabled) !blockedIds.contains(it.categoryId) else true }
                .map { HomeContentItem.SeriesItem(it) }
        } else emptyList()
    }
}
