package com.kybers.play.ui.movies

import android.app.Application
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.BuildConfig
import com.kybers.play.data.local.model.User
import com.kybers.play.data.model.MovieWithDetails
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.model.FilmographyItem
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbCollectionDetails
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.di.CurrentUser
import com.kybers.play.di.RepositoryFactory
import com.kybers.play.di.TmdbApiService
import com.kybers.play.ui.player.AspectRatioMode
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.TrackInfo
import com.kybers.play.player.MediaManager
import com.kybers.play.player.RetryManager
import com.kybers.play.core.player.PlayerCoordinator
import com.kybers.play.core.player.MediaSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class MovieDetailsUiState(
    val isLoadingDetails: Boolean = true,
    val movie: Movie? = null,
    val title: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val cast: List<TMDbCastMember> = emptyList(),
    val isFavorite: Boolean = false,
    val playbackPosition: Long = 0L,
    val collection: TMDbCollectionDetails? = null,
    val availableCollectionMovies: List<Movie> = emptyList(),
    val unavailableCollectionMovies: List<TMDbMovieResult> = emptyList(),
    val availableRecommendedMovies: List<Movie> = emptyList(),
    val availableSimilarMovies: List<Movie> = emptyList(),
    val showActorMoviesDialog: Boolean = false,
    val selectedActorName: String = "",
    val selectedActorBio: String? = null,
    val availableFilmography: List<EnrichedActorMovie> = emptyList(),
    val unavailableFilmography: List<FilmographyItem> = emptyList(),
    val isActorMoviesLoading: Boolean = false,
    val showUnavailableDetailsDialog: Boolean = false,
    val unavailableItemDetails: UnavailableItemDetails? = null,
    val isUnavailableItemLoading: Boolean = false,
    val isPlayerVisible: Boolean = false,
    val playerStatus: PlayerStatus = PlayerStatus.IDLE,
    val isFullScreen: Boolean = false,
    val isMuted: Boolean = false,
    val systemVolume: Int = 0,
    val maxSystemVolume: Int = 1,
    val screenBrightness: Float = 0.5f,
    val originalBrightness: Float = -1f,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val showAudioMenu: Boolean = false,
    val showSubtitleMenu: Boolean = false,
    val currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT_SCREEN,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isInPipMode: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetryAttempts: Int = 3,
    val retryMessage: String? = null
)

data class EnrichedActorMovie(
    val movie: Movie,
    val details: MovieWithDetails
)

data class UnavailableItemDetails(
    val title: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val rating: Double?,
    val certification: String?,
    val overview: String?
)

class MovieDetailsViewModel @AssistedInject constructor(
    @Assisted private val application: Application,
    private val repositoryFactory: RepositoryFactory,
    private val detailsRepository: DetailsRepository,
    @TmdbApiService private val externalApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    @CurrentUser private val currentUser: User,
    @Assisted private val movieId: Int,
    val mediaManager: MediaManager,
    val playerCoordinator: PlayerCoordinator
) : AndroidViewModel(application) {

    private val vodRepository: VodRepository by lazy {
        repositoryFactory.createVodRepository(currentUser.url)
    }

    @AssistedFactory
    interface Factory {
        fun create(application: Application, movieId: Int): MovieDetailsViewModel
    }

    private val _uiState = MutableStateFlow(MovieDetailsUiState())
    val uiState: StateFlow<MovieDetailsUiState> = _uiState.asStateFlow()
    private val _navigationEvent = MutableSharedFlow<Int>()
    val navigationEvent: SharedFlow<Int> = _navigationEvent.asSharedFlow()

    private var retryManager: RetryManager? = null
    private var engineObservationJob: Job? = null

    private var lastSaveTimeMillis: Long = 0L
    private val saveIntervalMillis: Long = 15000
    private var softwareFallbackTried = false

    init {
        loadInitialData()
        setupRetryManager()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true) }

            val movie = vodRepository.getAllMovies(currentUser.id).firstOrNull()?.find { it.streamId == movieId }

            if (movie != null) {
                val favoriteIds = preferenceManager.getFavoriteMovieIds()
                val savedPosition = preferenceManager.getPlaybackPosition(movieId.toString())
                _uiState.update {
                    it.copy(
                        movie = movie,
                        title = movie.name,
                        isFavorite = favoriteIds.contains(movie.streamId.toString()),
                        playbackPosition = savedPosition
                    )
                }
                fetchEnrichedDetails(movie)
            } else {
                _uiState.update { it.copy(isLoadingDetails = false) }
            }
        }
    }

    private fun fetchEnrichedDetails(movie: Movie) {
        viewModelScope.launch(Dispatchers.IO) {
            val detailsWrapper = detailsRepository.getMovieDetails(movie)
            val allLocalMovies = vodRepository.getAllMovies(currentUser.id).first()

            _uiState.update {
                it.copy(
                    isLoadingDetails = false,
                    posterUrl = detailsWrapper.details?.posterUrl ?: movie.streamIcon,
                    backdropUrl = detailsWrapper.details?.backdropUrl,
                    releaseYear = detailsWrapper.details?.releaseYear,
                    rating = detailsWrapper.details?.rating,
                    plot = detailsWrapper.details?.plot,
                    cast = detailsWrapper.getCastList()
                )
            }

            val collectionId = detailsWrapper.getCollectionId()
            var collectionMovieTmdbIds = emptySet<String>()

            if (collectionId != null) {
                val collectionDetails = fetchCollection(collectionId)
                if (collectionDetails != null) {
                    val localTmdbIds = allLocalMovies.mapNotNull { it.tmdbId }.toSet()

                    val sortedParts = collectionDetails.parts.sortedBy { it.releaseDate }
                    val (available, unavailable) = sortedParts.partition {
                        localTmdbIds.contains(it.id.toString())
                    }

                    val availableMovies = available
                        .mapNotNull { tmdbMovie -> allLocalMovies.find { it.tmdbId == tmdbMovie.id.toString() } }

                    collectionMovieTmdbIds = availableMovies.mapNotNull { it.tmdbId }.toSet()

                    _uiState.update {
                        it.copy(
                            collection = collectionDetails,
                            availableCollectionMovies = availableMovies,
                            unavailableCollectionMovies = unavailable
                        )
                    }
                }
            }

            processRecommendationsAndSimilar(detailsWrapper, allLocalMovies, collectionMovieTmdbIds)
        }
    }

    private suspend fun fetchCollection(collectionId: Int): TMDbCollectionDetails? {
        return try {
            val response = externalApiService.getCollectionDetails(collectionId, BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("MovieDetailsVM", "Error al buscar detalles de la colección", e)
            null
        }
    }

    private fun processRecommendationsAndSimilar(
        detailsWrapper: MovieWithDetails,
        allLocalMovies: List<Movie>,
        excludeIds: Set<String>
    ) {
        val localTmdbIds = allLocalMovies.mapNotNull { it.tmdbId }.toSet()

        val recommended = detailsWrapper.getRecommendationList()
            .filter { !excludeIds.contains(it.id.toString()) && localTmdbIds.contains(it.id.toString()) }
            .mapNotNull { tmdbMovie -> allLocalMovies.find { it.tmdbId == tmdbMovie.id.toString() } }
            .distinctBy { it.streamId }

        val similar = detailsWrapper.getSimilarList()
            .filter { !excludeIds.contains(it.id.toString()) && localTmdbIds.contains(it.id.toString()) }
            .mapNotNull { tmdbMovie -> allLocalMovies.find { it.tmdbId == tmdbMovie.id.toString() } }
            .distinctBy { it.streamId }

        _uiState.update {
            it.copy(
                availableRecommendedMovies = recommended,
                availableSimilarMovies = similar
            )
        }
    }

    fun onActorSelected(actor: TMDbCastMember) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isActorMoviesLoading = true,
                    showActorMoviesDialog = true,
                    selectedActorName = actor.name
                )
            }
            val allMovies = vodRepository.getAllMovies(currentUser.id).first()
            val allSeries = vodRepository.getAllSeries(currentUser.id).first()
            val filmography = detailsRepository.getActorFilmography(actor.id, allMovies, allSeries)

            val availableTmdbIds = filmography.availableItems.map { it.id }.toSet()
            val availableLocalMovies = allMovies.filter { availableTmdbIds.contains(it.tmdbId?.toIntOrNull()) }

            val enrichedMovies = availableLocalMovies.map { movie ->
                EnrichedActorMovie(movie, detailsRepository.getMovieDetails(movie))
            }

            _uiState.update {
                it.copy(
                    isActorMoviesLoading = false,
                    selectedActorBio = filmography.biography,
                    availableFilmography = enrichedMovies,
                    unavailableFilmography = filmography.unavailableItems
                )
            }
        }
    }

    fun onUnavailableItemSelected(item: FilmographyItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isUnavailableItemLoading = true, showUnavailableDetailsDialog = true) }

            val details: UnavailableItemDetails? = when (item.mediaType) {
                "movie" -> {
                    val movieDetails = detailsRepository.getTMDbDetails(item.id)
                    movieDetails?.let {
                        UnavailableItemDetails(
                            title = it.title,
                            posterUrl = it.getFullPosterUrl(),
                            backdropUrl = it.getFullBackdropUrl(),
                            releaseYear = it.releaseDate?.substringBefore("-"),
                            rating = it.voteAverage,
                            certification = detailsRepository.findMovieCertification(it),
                            overview = it.overview
                        )
                    }
                }
                "tv" -> {
                    val tvDetails = detailsRepository.getTMDbTvDetails(item.id)
                    tvDetails?.let {
                        UnavailableItemDetails(
                            title = it.name,
                            posterUrl = it.getFullPosterUrl(),
                            backdropUrl = it.getFullBackdropUrl(),
                            releaseYear = it.firstAirDate?.substringBefore("-"),
                            rating = it.voteAverage,
                            certification = detailsRepository.findTvCertification(it),
                            overview = it.overview
                        )
                    }
                }
                else -> null
            }

            _uiState.update {
                it.copy(
                    isUnavailableItemLoading = false,
                    unavailableItemDetails = details
                )
            }
        }
    }

    fun onDismissActorMoviesDialog() {
        _uiState.update {
            it.copy(
                showActorMoviesDialog = false,
                selectedActorName = "",
                selectedActorBio = null,
                availableFilmography = emptyList(),
                unavailableFilmography = emptyList()
            )
        }
    }

    fun onDismissUnavailableDetailsDialog() {
        _uiState.update {
            it.copy(
                showUnavailableDetailsDialog = false,
                unavailableItemDetails = null
            )
        }
    }

    fun onRecommendationSelected(movie: Movie) {
        viewModelScope.launch { _navigationEvent.emit(movie.streamId) }
    }

    private fun setupRetryManager() {
        // Retry logic is now handled by PlayerCoordinator
        // This is kept for backward compatibility but will be simplified
        retryManager = RetryManager(
            onRetryAttempt = { attempt, maxRetries ->
                _uiState.update { 
                    it.copy(
                        playerStatus = PlayerStatus.RETRYING,
                        retryAttempt = attempt,
                        maxRetryAttempts = maxRetries,
                        retryMessage = "Reintentando... ($attempt/$maxRetries)"
                    ) 
                }
            },
            onRetrySuccess = {
                _uiState.update { 
                    it.copy(
                        playerStatus = PlayerStatus.PLAYING,
                        retryAttempt = 0,
                        retryMessage = null
                    ) 
                }
            },
            onRetryFailed = {
                _uiState.update {
                    it.copy(
                        playerStatus = PlayerStatus.RETRY_FAILED,
                        retryAttempt = 0,
                        retryMessage = "Error de conexión. Verifica tu red e inténtalo de nuevo."
                    )
                }
            }
        )
    }

    private fun saveCurrentProgress() {
        // Progress saving is now handled by the PlayerCoordinator
        // For backward compatibility, we'll keep this but it won't be used
        val currentMovieId = _uiState.value.movie?.streamId?.toString() ?: return
        // The actual progress saving is handled by the player engine now
    }

    fun startPlayback(continueFromLastPosition: Boolean) {
        viewModelScope.launch {
            softwareFallbackTried = false
            _uiState.update {
                it.copy(
                    playerStatus = PlayerStatus.LOADING,
                    retryAttempt = 0,
                    retryMessage = null
                )
            }
            try {
                val movie = _uiState.value.movie ?: throw IllegalStateException("No movie selected")
                val streamUrl = buildStreamUrl(movie)
                val startPosition = if (continueFromLastPosition && _uiState.value.playbackPosition > 0) {
                    _uiState.value.playbackPosition
                } else 0L
                val mediaSpec = MediaSpec(
                    url = streamUrl,
                    title = movie.name,
                    forceSoftwareDecoding = false
                )
                playerCoordinator.play(mediaSpec)
                _uiState.update { it.copy(isPlayerVisible = true, playerStatus = PlayerStatus.BUFFERING) }
                observeCurrentEngine()
            } catch (e: Exception) {
                Log.e("MovieDetailsViewModel", "Failed to start playback: ${e.message}", e)
                _uiState.update { it.copy(playerStatus = PlayerStatus.ERROR) }
            }
        }
    }

    // --- ¡CORRECCIÓN DE FUGA DE MEMORIA! ---
    fun hidePlayer() {
        viewModelScope.launch {
            retryManager?.cancelRetry()
            playerCoordinator.stopAll()
            engineObservationJob?.cancel()
            engineObservationJob = null
            _uiState.update {
                it.copy(
                    isPlayerVisible = false, isFullScreen = false, playerStatus = PlayerStatus.IDLE,
                    currentPosition = 0L, duration = 0L, isInPipMode = false,
                    playbackPosition = preferenceManager.getPlaybackPosition(movieId.toString()),
                    retryAttempt = 0, retryMessage = null
                )
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            try {
                val engine = playerCoordinator.getCurrentEngine()
                if (engine != null) {
                    when (engine.state.value) {
                        com.kybers.play.core.player.PlayerState.PLAYING -> {
                            engine.pause()
                            _uiState.update { it.copy(playerStatus = PlayerStatus.PAUSED) }
                        }
                        com.kybers.play.core.player.PlayerState.READY,
                        com.kybers.play.core.player.PlayerState.PAUSED -> {
                            engine.play()
                            _uiState.update { it.copy(playerStatus = PlayerStatus.PLAYING) }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.e("MovieDetailsViewModel", "togglePlayPause error: ${e.message}", e)
            }
        }
    }

    private fun observeCurrentEngine() {
        engineObservationJob?.cancel()
        val engine = playerCoordinator.getCurrentEngine() ?: return
        engineObservationJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                engine.state,
                engine.position,
                engine.duration
            ) { state, position, duration ->
                Triple(state, position, duration)
            }.collect { (state, position, duration) ->
                val mappedStatus = when (state) {
                    com.kybers.play.core.player.PlayerState.PREPARING,
                    com.kybers.play.core.player.PlayerState.BUFFERING -> PlayerStatus.BUFFERING
                    com.kybers.play.core.player.PlayerState.PLAYING -> PlayerStatus.PLAYING
                    com.kybers.play.core.player.PlayerState.PAUSED -> PlayerStatus.PAUSED
                    com.kybers.play.core.player.PlayerState.READY -> PlayerStatus.PAUSED
                    com.kybers.play.core.player.PlayerState.ENDED -> PlayerStatus.PAUSED
                    com.kybers.play.core.player.PlayerState.ERROR -> PlayerStatus.ERROR
                    com.kybers.play.core.player.PlayerState.IDLE -> PlayerStatus.IDLE
                }
                _uiState.update {
                    it.copy(
                        playerStatus = mappedStatus,
                        currentPosition = position,
                        duration = duration
                    )
                }
            }
        }
    }

    fun seekForward() {
        viewModelScope.launch {
            try {
                val engine = playerCoordinator.getCurrentEngine() ?: return@launch
                val newPos = (engine.position.value + 10_000).coerceAtMost(engine.duration.value)
                engine.seekTo(newPos)
            } catch (e: Exception) {
                Log.e("MovieDetailsViewModel", "seekForward error: ${e.message}", e)
            }
        }
    }

    fun seekBackward() {
        viewModelScope.launch {
            try {
                val engine = playerCoordinator.getCurrentEngine() ?: return@launch
                val newPos = (engine.position.value - 10_000).coerceAtLeast(0)
                engine.seekTo(newPos)
            } catch (e: Exception) {
                Log.e("MovieDetailsViewModel", "seekBackward error: ${e.message}", e)
            }
        }
    }

    fun setInitialSystemValues(volume: Int, maxVolume: Int, brightness: Float) {
        _uiState.update {
            it.copy(
                systemVolume = volume,
                maxSystemVolume = maxVolume,
                originalBrightness = brightness,
                screenBrightness = if (brightness >= 0) brightness else 0.5f
            )
        }
    }

    fun setInPipMode(inPip: Boolean) {
        _uiState.update { it.copy(isInPipMode = inPip) }
    }
    fun onToggleMute(audioManager: AudioManager) {
        val newMuteState = !_uiState.value.isMuted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val direction = if (newMuteState) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, newMuteState)
        }
        _uiState.update { it.copy(isMuted = newMuteState) }
    }
    fun setSystemVolume(volume: Int, audioManager: AudioManager) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        _uiState.update { it.copy(systemVolume = volume, isMuted = volume == 0) }
    }
    fun setScreenBrightness(brightness: Float) {
        _uiState.update { it.copy(screenBrightness = brightness.coerceIn(0f, 1f)) }
    }
    fun onToggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }
    fun seekTo(position: Long) {
        viewModelScope.launch {
            try {
                playerCoordinator.getCurrentEngine()?.seekTo(position)
            } catch (e: Exception) {
                Log.e("MovieDetailsViewModel", "seekTo error: ${e.message}", e)
            }
        }
    }
    fun toggleAspectRatio() {
        val nextMode = when (_uiState.value.currentAspectRatioMode) {
            AspectRatioMode.FIT_SCREEN -> AspectRatioMode.FILL_SCREEN
            AspectRatioMode.FILL_SCREEN -> AspectRatioMode.ASPECT_16_9
            AspectRatioMode.ASPECT_16_9 -> AspectRatioMode.ASPECT_4_3
            AspectRatioMode.ASPECT_4_3 -> AspectRatioMode.FIT_SCREEN
        }
        applyAspectRatio(nextMode)
        _uiState.update { it.copy(currentAspectRatioMode = nextMode) }
    }
    private fun applyAspectRatio(mode: AspectRatioMode) {
        // Aspect ratio is now handled by the PlayerEngine
        // The specific implementation will depend on whether it's VLC or Media3
    }
    private fun updateTrackInfo() {
        // Track info is now handled by the PlayerCoordinator
        // For now, we'll just clear the track info
        _uiState.update { 
            it.copy(
                availableAudioTracks = emptyList(),
                availableSubtitleTracks = emptyList()
            ) 
        }
    }
    fun toggleAudioMenu(show: Boolean) = _uiState.update { it.copy(showAudioMenu = show) }
    fun toggleSubtitleMenu(show: Boolean) = _uiState.update { it.copy(showSubtitleMenu = show) }
    fun selectAudioTrack(trackId: Int) { 
        // Audio track selection is now handled by the PlayerCoordinator
        updateTrackInfo() 
    }
    fun selectSubtitleTrack(trackId: Int) { 
        // Subtitle track selection is now handled by the PlayerCoordinator
        updateTrackInfo() 
    }
    private fun buildStreamUrl(movie: Movie): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}movie/${currentUser.username}/${currentUser.password}/${movie.streamId}.${movie.containerExtension}"
    }
    override fun onCleared() {
        super.onCleared()
        retryManager?.cancelRetry()
        // Player cleanup is now handled by PlayerCoordinator
    }
    fun toggleFavorite() {
        val movie = _uiState.value.movie ?: return
        val currentFavorites = preferenceManager.getFavoriteMovieIds().toMutableSet()
        val isCurrentlyFavorite = currentFavorites.contains(movie.streamId.toString())
        if (isCurrentlyFavorite) {
            currentFavorites.remove(movie.streamId.toString())
        } else {
            currentFavorites.add(movie.streamId.toString())
        }
        preferenceManager.saveFavoriteMovieIds(currentFavorites)
        _uiState.update { it.copy(isFavorite = !isCurrentlyFavorite) }
    }

    /**
     * Manually retry playback for current movie
     */
    fun retryPlayback() {
        startPlayback(_uiState.value.playbackPosition > 0)
    }

    /**
     * Updates player settings dynamically when preferences change.
     * This allows immediate application of new settings without restart.
     */
    fun updatePlayerSettings() {
        // Player settings are now handled by the PlayerCoordinator and PlayerEngines
        // This method is kept for backward compatibility but does nothing
    }
}
