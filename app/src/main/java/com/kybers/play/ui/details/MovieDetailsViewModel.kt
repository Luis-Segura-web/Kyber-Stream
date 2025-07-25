package com.kybers.play.ui.details

import android.app.Application
import android.media.AudioManager
import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.local.model.User
import com.kybers.play.data.model.MovieWithDetails
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.model.FilmographyItem
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.repository.ActorFilmography
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.player.AspectRatioMode
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

// Los data classes de estado de la UI no necesitan cambios.
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
    val availableRecommendations: List<Movie> = emptyList(),
    val isFavorite: Boolean = false,
    val playbackPosition: Long = 0L,
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
    val isInPipMode: Boolean = false
)

class MovieDetailsViewModel(
    application: Application,
    private val vodRepository: VodRepository,
    private val detailsRepository: DetailsRepository,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val movieId: Int
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MovieDetailsUiState())
    val uiState: StateFlow<MovieDetailsUiState> = _uiState.asStateFlow()
    private val _navigationEvent = MutableSharedFlow<Int>()
    val navigationEvent: SharedFlow<Int> = _navigationEvent.asSharedFlow()
    private val libVLC: LibVLC = LibVLC(application)
    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)
    private val vlcOptions = arrayListOf("--network-caching=3000", "--file-caching=3000")

    // --- ¡NUEVO! ---
    // Mapa para guardar en memoria todos los detalles de películas que ya tenemos en la BD local.
    private var cachedDetailsMap: Map<Int, MovieDetailsCache> = emptyMap()


    init {
        loadInitialData()
        setupMediaPlayer()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true) }

            // --- ¡CAMBIO! ---
            // Precargamos todos los detalles cacheados al mismo tiempo que la película actual.
            val movieJob = async { vodRepository.getAllMovies(currentUser.id).firstOrNull()?.find { it.streamId == movieId } }
            val cacheJob = async { cachedDetailsMap = detailsRepository.getAllCachedMovieDetailsMap() }

            val movie = movieJob.await()
            cacheJob.await() // Nos aseguramos de que el mapa esté listo.

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
            val latinCharsRegex = "^[\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\s]*$".toRegex()
            val filteredCast = detailsWrapper.getCastList().filter { latinCharsRegex.matches(it.name) }
            _uiState.update {
                it.copy(
                    isLoadingDetails = false,
                    title = movie.name,
                    posterUrl = detailsWrapper.details?.posterUrl ?: movie.streamIcon,
                    backdropUrl = detailsWrapper.details?.backdropUrl,
                    releaseYear = detailsWrapper.details?.releaseYear,
                    rating = detailsWrapper.details?.rating,
                    plot = detailsWrapper.details?.plot,
                    cast = filteredCast
                )
            }
            launch {
                val allLocalMovies = vodRepository.getAllMovies(currentUser.id).first()
                val tmdbRecs = detailsWrapper.getRecommendationList()
                val availableRecs = allLocalMovies.filter { localMovie ->
                    tmdbRecs.any { it.id.toString() == localMovie.tmdbId }
                }
                _uiState.update { it.copy(availableRecommendations = availableRecs.distinctBy { it.streamId }) }
            }
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


            // --- ¡GRAN OPTIMIZACIÓN AQUÍ! ---
            // Ya no hacemos llamadas de red en bucle. Usamos el mapa que cargamos al inicio.
            val enrichedMovies = availableLocalMovies.map { movie ->
                val details = cachedDetailsMap[movie.streamId]
                EnrichedActorMovie(movie, MovieWithDetails(movie, details))
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
    private fun setupMediaPlayer() {
        mediaPlayer.setEventListener { event ->
            val currentState = _uiState.value.playerStatus
            val newStatus = when (event.type) {
                MediaPlayer.Event.Playing -> PlayerStatus.PLAYING
                MediaPlayer.Event.Paused -> PlayerStatus.PAUSED
                MediaPlayer.Event.Buffering -> if (currentState != PlayerStatus.PLAYING) PlayerStatus.BUFFERING else null
                MediaPlayer.Event.EncounteredError -> PlayerStatus.ERROR
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> PlayerStatus.IDLE
                MediaPlayer.Event.TimeChanged -> {
                    _uiState.update { it.copy(currentPosition = event.timeChanged, duration = mediaPlayer.length) }
                    null
                }
                else -> null
            }
            newStatus?.let { status ->
                if (status != currentState) {
                    _uiState.update { it.copy(playerStatus = status) }
                    if (status == PlayerStatus.PLAYING) updateTrackInfo()
                }
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
    fun startPlayback(continueFromLastPosition: Boolean) {
        val movie = _uiState.value.movie ?: return
        val streamUrl = buildStreamUrl(movie)
        val newMedia = Media(libVLC, streamUrl.toUri()).apply {
            vlcOptions.forEach { addOption(it) }
        }
        mediaPlayer.media?.release()
        mediaPlayer.media = newMedia
        mediaPlayer.play()
        if (continueFromLastPosition && _uiState.value.playbackPosition > 0) {
            mediaPlayer.time = _uiState.value.playbackPosition
        }
        _uiState.update { it.copy(isPlayerVisible = true, playerStatus = PlayerStatus.BUFFERING) }
    }
    fun hidePlayer() {
        if (mediaPlayer.isPlaying) {
            preferenceManager.savePlaybackPosition(movieId.toString(), mediaPlayer.time)
            mediaPlayer.stop()
        }
        mediaPlayer.media?.release()
        mediaPlayer.media = null
        _uiState.update {
            it.copy(
                isPlayerVisible = false, isFullScreen = false, playerStatus = PlayerStatus.IDLE,
                currentPosition = 0L, duration = 0L, isInPipMode = false,
                playbackPosition = preferenceManager.getPlaybackPosition(movieId.toString())
            )
        }
    }
    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
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
        mediaPlayer.time = position
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
        when (mode) {
            AspectRatioMode.FIT_SCREEN -> { mediaPlayer.setAspectRatio(null); mediaPlayer.setScale(0.0f) }
            AspectRatioMode.FILL_SCREEN -> { mediaPlayer.setAspectRatio(null); mediaPlayer.setScale(1.0f) }
            AspectRatioMode.ASPECT_16_9 -> { mediaPlayer.setAspectRatio("16:9"); mediaPlayer.setScale(0.0f) }
            AspectRatioMode.ASPECT_4_3 -> { mediaPlayer.setAspectRatio("4:3"); mediaPlayer.setScale(0.0f) }
        }
    }
    private fun updateTrackInfo() {
        val audioTracks = mediaPlayer.audioTracks?.map { TrackInfo(it.id, it.name, it.id == mediaPlayer.audioTrack) } ?: emptyList()
        val subtitleTracks = mediaPlayer.spuTracks?.map { TrackInfo(it.id, it.name, it.id == mediaPlayer.spuTrack) } ?: emptyList()
        _uiState.update { it.copy(availableAudioTracks = audioTracks, availableSubtitleTracks = subtitleTracks) }
    }
    fun toggleAudioMenu(show: Boolean) = _uiState.update { it.copy(showAudioMenu = show) }
    fun toggleSubtitleMenu(show: Boolean) = _uiState.update { it.copy(showSubtitleMenu = show) }
    fun selectAudioTrack(trackId: Int) { mediaPlayer.setAudioTrack(trackId); updateTrackInfo() }
    fun selectSubtitleTrack(trackId: Int) { mediaPlayer.setSpuTrack(trackId); updateTrackInfo() }
    private fun buildStreamUrl(movie: Movie): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}movie/${currentUser.username}/${currentUser.password}/${movie.streamId}.${movie.containerExtension}"
    }
    override fun onCleared() {
        super.onCleared()
        if (mediaPlayer.isPlaying) {
            preferenceManager.savePlaybackPosition(movieId.toString(), mediaPlayer.time)
        }
        mediaPlayer.stop()
        mediaPlayer.media?.release()
        mediaPlayer.release()
        libVLC.release()
    }
}