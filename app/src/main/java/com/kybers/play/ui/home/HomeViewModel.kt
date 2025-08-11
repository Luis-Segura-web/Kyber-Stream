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
import com.kybers.play.di.RepositoryFactory
import com.kybers.play.di.TmdbApiService
import com.kybers.play.di.UserSession
import com.kybers.play.ui.components.ParentalControlManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repositoryFactory: RepositoryFactory,
    private val detailsRepository: DetailsRepository,
    @TmdbApiService private val externalApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    private val userSession: UserSession,
    private val parentalControlManager: ParentalControlManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val currentUser: User?
        get() = userSession.getCurrentUser()

    private fun getVodRepository(): VodRepository? {
        return currentUser?.let { repositoryFactory.createVodRepository(it.url) }
    }

    private fun getLiveRepository(): LiveRepository? {
        return currentUser?.let { repositoryFactory.createLiveRepository(it.url) }
    }

    init {
        // Only load content if we have a current user
        currentUser?.let {
            loadAllHomeContent()
            
            // React to parental control changes
            viewModelScope.launch {
                parentalControlManager.blockedCategoriesState.collect { _ ->
                    // Reload content when blocked categories change
                    loadAllHomeContent()
                }
            }
        }
    }

    private fun loadAllHomeContent() {
        viewModelScope.launch {
            val user = currentUser ?: return@launch
            _uiState.update { it.copy(isLoading = true, userName = user.profileName) }

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

        // Load all carousels in parallel
        val favoritesJob = async { loadMixedFavorites() }
        val continueWatchingJob = async { loadMixedContinueWatching() }
        val recentContentJob = async { loadMixedRecentContent() }
        val trendingMoviesJob = async { loadTrendingMoviesWeek() }
        val trendingSeriesJob = async { loadTrendingSeriesWeek() }

        val favorites = favoritesJob.await()
        val continueWatching = continueWatchingJob.await()
        val recentContent = recentContentJob.await()
        val trendingMovies = trendingMoviesJob.await()
        val trendingSeries = trendingSeriesJob.await()

        // Add carousels in the specified order
        if (favorites.isNotEmpty()) carousels.add(HomeCarousel("Mis Favoritas", favorites))
        if (continueWatching.isNotEmpty()) carousels.add(HomeCarousel("Continuar Viendo", continueWatching))
        if (recentContent.isNotEmpty()) carousels.add(HomeCarousel("Películas y series Recientes", recentContent))
        if (trendingMovies.isNotEmpty()) carousels.add(HomeCarousel("Películas más vistas de la semana", trendingMovies))
        if (trendingSeries.isNotEmpty()) carousels.add(HomeCarousel("Series más vistas de la semana", trendingSeries))

        return@coroutineScope carousels
    }

    private suspend fun loadMixedFavorites(): List<HomeContentItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val favMovieIds = preferenceManager.getFavoriteMovieIds()
        val favSeriesIds = preferenceManager.getFavoriteSeriesIds()
        
        val favorites = mutableListOf<HomeContentItem>()
        
        if (favMovieIds.isNotEmpty()) {
            val allMovies = vodRepo.getAllMovies(user.id).first()
                .filter { favMovieIds.contains(it.streamId.toString()) }
            val filteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }
            favorites.addAll(filteredMovies.map { HomeContentItem.MovieItem(it) })
        }
        
        if (favSeriesIds.isNotEmpty()) {
            val allSeries = vodRepo.getAllSeries(user.id).first()
                .filter { favSeriesIds.contains(it.seriesId.toString()) }
            val filteredSeries = parentalControlManager.filterContentByCategory(allSeries) { it.categoryId }
            favorites.addAll(filteredSeries.map { HomeContentItem.SeriesItem(it) })
        }
        
        return favorites.shuffled() // Mix movies and series randomly
    }

    private suspend fun loadMixedContinueWatching(): List<HomeContentItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val limit = preferenceManager.getRecentlyWatchedLimit()
        val moviePositions = preferenceManager.getAllPlaybackPositions()
        val continueWatching = mutableListOf<HomeContentItem>()
        
        if (moviePositions.isNotEmpty()) {
            val allMovies = vodRepo.getAllMovies(user.id).first()
            val filteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }
            
            moviePositions
                .filter { (_, position) -> position > 10000 }
                .mapNotNull { (id, _) -> filteredMovies.find { it.streamId.toString() == id } }
                .take(limit)
                .forEach { continueWatching.add(HomeContentItem.MovieItem(it)) }
        }
        
        // TODO: Add series continue watching logic when series playback positions are available
        
        return continueWatching
    }

    private suspend fun loadMixedRecentContent(): List<HomeContentItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val recentContent = mutableListOf<HomeContentItem>()
        
        // Get recent movies
        val allMovies = vodRepo.getAllMovies(user.id).first()
        val filteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }
        val recentMovies = filteredMovies
            .sortedByDescending { it.added }
            .take(5)
            .map { HomeContentItem.MovieItem(it) }
        
        // Get recent series
        val allSeries = vodRepo.getAllSeries(user.id).first()
        val filteredSeries = parentalControlManager.filterContentByCategory(allSeries) { it.categoryId }
        val recentSeries = filteredSeries
            .sortedByDescending { it.lastModified }
            .take(5)
            .map { HomeContentItem.SeriesItem(it) }
        
        // Combine and sort by date (most recent first)
        recentContent.addAll(recentMovies)
        recentContent.addAll(recentSeries)
        
        // Sort mixed content by recency
        return recentContent.sortedByDescending { item ->
            when (item) {
                is HomeContentItem.MovieItem -> item.movie.added.toLongOrNull() ?: 0L
                is HomeContentItem.SeriesItem -> item.series.lastModified?.toLongOrNull() ?: 0L
                else -> 0L
            }
        }.take(10)
    }

    private suspend fun loadTrendingMoviesWeek(): List<HomeContentItem.MovieItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val localMovies = vodRepo.getAllMovies(user.id).first()
        val filteredMovies = parentalControlManager.filterContentByCategory(localMovies) { it.categoryId }
        val localTmdbIds = filteredMovies.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
        
        if (localTmdbIds.isEmpty()) return emptyList()
        
        try {
            val response = externalApiService.getTrendingMoviesWeek(apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) {
                val trendingTmdbMovies = response.body()?.results ?: emptyList()
                return trendingTmdbMovies
                    .filter { trendingMovie -> localTmdbIds.contains(trendingMovie.id) }
                    .mapNotNull { trendingMovie ->
                        filteredMovies.find { it.tmdbId == trendingMovie.id.toString() }
                    }
                    .take(10)
                    .map { HomeContentItem.MovieItem(it) }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "No se pudieron cargar las películas trending de la semana", e)
        }
        return emptyList()
    }

    private suspend fun loadTrendingSeriesWeek(): List<HomeContentItem.SeriesItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val localSeries = vodRepo.getAllSeries(user.id).first()
        val filteredSeries = parentalControlManager.filterContentByCategory(localSeries) { it.categoryId }
        val localTmdbIds = filteredSeries.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
        
        if (localTmdbIds.isEmpty()) return emptyList()
        
        try {
            val response = externalApiService.getTrendingSeriesWeek(apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) {
                val trendingTmdbSeries = response.body()?.results ?: emptyList()
                return trendingTmdbSeries
                    .filter { trendingSeries -> localTmdbIds.contains(trendingSeries.id) }
                    .mapNotNull { trendingSeries ->
                        filteredSeries.find { it.tmdbId == trendingSeries.id.toString() }
                    }
                    .take(10)
                    .map { HomeContentItem.SeriesItem(it) }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "No se pudieron cargar las series trending de la semana", e)
        }
        return emptyList()
    }

    private suspend fun loadBannerContent(): List<Pair<Movie, String?>> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val localMovies = vodRepo.getAllMovies(user.id).first()
        
        // Apply parental control filtering using ParentalControlManager
        val filteredMovies = parentalControlManager.filterContentByCategory(localMovies) { it.categoryId }

        val localTmdbIds = filteredMovies.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
        if (localTmdbIds.isEmpty()) return emptyList()

        try {
            val response = externalApiService.getPopularMoviesTMDb(apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) {
                val popularTmdbMovies = response.body()?.results ?: emptyList()
                val availablePopular = popularTmdbMovies
                    .filter { popularTmdbMovie -> localTmdbIds.contains(popularTmdbMovie.id) }
                    .mapNotNull { popularTmdbMovie ->
                        filteredMovies.find { it.tmdbId == popularTmdbMovie.id.toString() }
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

    private suspend fun loadContinueMovies(): List<HomeContentItem.MovieItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val limit = preferenceManager.getRecentlyWatchedLimit()
        val moviePositions = preferenceManager.getAllPlaybackPositions()
        return if (moviePositions.isNotEmpty()) {
            val allMovies = vodRepo.getAllMovies(user.id).first()
            
            // Apply parental control filtering using ParentalControlManager
            val filteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }
            
            moviePositions
                .filter { (_, position) -> position > 10000 }
                .mapNotNull { (id, _) -> filteredMovies.find { it.streamId.toString() == id } }
                .take(limit)
                .map { HomeContentItem.MovieItem(it) }
        } else emptyList()
    }

    private suspend fun loadLiveNow(): List<HomeContentItem.LiveChannelItem> {
        val user = currentUser ?: return emptyList()
        val liveRepo = getLiveRepository() ?: return emptyList()
        val channels = liveRepo.getRawLiveStreams(user.id).first()
        
        // Apply parental control filtering using ParentalControlManager
        val filteredChannels = parentalControlManager.filterContentByCategory(channels) { it.categoryId }
        
        if (filteredChannels.isEmpty()) return emptyList()

        val epgMap = liveRepo.getAllEpgMapForUser(user.id)
        val enrichedChannels = liveRepo.enrichChannelsWithEpg(filteredChannels, epgMap)

        return enrichedChannels
            .filter { it.currentEpgEvent != null }
            .shuffled()
            .take(10)
            .map { HomeContentItem.LiveChannelItem(it) }
    }

    private suspend fun loadRecentlyAddedMovies(): List<HomeContentItem.MovieItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val allMovies = vodRepo.getAllMovies(user.id).first()
        
        // Apply parental control filtering using ParentalControlManager
        val filteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }
        
        return filteredMovies
            .sortedByDescending { it.added }
            .take(10)
            .map { HomeContentItem.MovieItem(it) }
    }

    private suspend fun loadRecentlyAddedSeries(): List<HomeContentItem.SeriesItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val allSeries = vodRepo.getAllSeries(user.id).first()
        
        // Apply parental control filtering using ParentalControlManager
        val filteredSeries = parentalControlManager.filterContentByCategory(allSeries) { it.categoryId }
        
        return filteredSeries
            .sortedByDescending { it.lastModified }
            .take(10)
            .map { HomeContentItem.SeriesItem(it) }
    }

    private suspend fun loadFavoriteMovies(): List<HomeContentItem.MovieItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val favMovieIds = preferenceManager.getFavoriteMovieIds()
        return if (favMovieIds.isNotEmpty()) {
            val allMovies = vodRepo.getAllMovies(user.id).first()
                .filter { favMovieIds.contains(it.streamId.toString()) }
            
            // Apply parental control filtering using ParentalControlManager
            val filteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }
            
            filteredMovies.map { HomeContentItem.MovieItem(it) }
        } else emptyList()
    }

    private suspend fun loadFavoriteSeries(): List<HomeContentItem.SeriesItem> {
        val user = currentUser ?: return emptyList()
        val vodRepo = getVodRepository() ?: return emptyList()
        val favSeriesIds = preferenceManager.getFavoriteSeriesIds()
        return if (favSeriesIds.isNotEmpty()) {
            val allSeries = vodRepo.getAllSeries(user.id).first()
                .filter { favSeriesIds.contains(it.seriesId.toString()) }
            
            // Apply parental control filtering using ParentalControlManager
            val filteredSeries = parentalControlManager.filterContentByCategory(allSeries) { it.categoryId }
            
            filteredSeries.map { HomeContentItem.SeriesItem(it) }
        } else emptyList()
    }
}
