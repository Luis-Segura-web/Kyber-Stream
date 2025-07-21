package com.kybers.play.ui.details

import android.app.Application
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.ui.player.AspectRatioMode
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.TrackInfo
import com.kybers.play.ui.player.toAspectRatioMode
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

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

    // Estado del diálogo del actor
    val showActorMoviesDialog: Boolean = false,
    val selectedActorName: String = "",
    val selectedActorBio: String? = null,
    val availableActorMovies: List<Movie> = emptyList(),
    val unavailableActorMovies: List<TMDbMovieResult> = emptyList(),
    val isActorMoviesLoading: Boolean = false,

    // Estado del reproductor
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
    val availableVideoTracks: List<TrackInfo> = emptyList(),
    val showAudioMenu: Boolean = false,
    val showSubtitleMenu: Boolean = false,
    val showVideoMenu: Boolean = false,
    val currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT_SCREEN,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isInPipMode: Boolean = false
)

class MovieDetailsViewModel(
    application: Application,
    private val contentRepository: ContentRepository,
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

    init {
        loadInitialData()
        setupMediaPlayer()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true) }
            val movie = contentRepository.getAllMovies(currentUser.id).firstOrNull()
                ?.find { it.streamId == movieId }

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
            val detailsWrapper = contentRepository.getMovieDetails(movie)

            // Actualizamos la UI con la información principal primero
            _uiState.update {
                it.copy(
                    isLoadingDetails = false,
                    title = detailsWrapper.details?.releaseYear?.let { year ->
                        "${movie.name} ($year)"
                    } ?: movie.name,
                    posterUrl = detailsWrapper.details?.posterUrl ?: movie.streamIcon,
                    backdropUrl = detailsWrapper.details?.backdropUrl,
                    releaseYear = detailsWrapper.details?.releaseYear,
                    rating = detailsWrapper.details?.rating,
                    plot = detailsWrapper.details?.plot,
                    cast = detailsWrapper.getCastList()
                )
            }

            // Lanzamos una nueva coroutina para las recomendaciones, para no bloquear
            launch {
                val allLocalMovies = contentRepository.getAllMovies(currentUser.id).first()
                val availableRecs = contentRepository.findMoviesByTMDbResults(
                    detailsWrapper.getRecommendationList(),
                    allLocalMovies
                )
                _uiState.update {
                    it.copy(availableRecommendations = availableRecs.distinctBy { it.streamId })
                }
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

            val allMovies = contentRepository.getAllMovies(currentUser.id).first()
            val filmography = contentRepository.getActorFilmography(actor.id, allMovies)

            // ¡NUEVO! Ordenamos las películas disponibles por año descendente.
            val sortedAvailableMovies = filmography.availableMovies.sortedByDescending { movie ->
                contentRepository.cleanMovieTitle(movie.name).year?.toIntOrNull() ?: 0
            }

            _uiState.update {
                it.copy(
                    isActorMoviesLoading = false,
                    selectedActorBio = filmography.biography,
                    availableActorMovies = sortedAvailableMovies, // Usamos la lista ordenada
                    unavailableActorMovies = filmography.unavailableMovies
                )
            }
        }
    }

    fun onRecommendationSelected(movie: Movie) {
        viewModelScope.launch {
            _navigationEvent.emit(movie.streamId)
        }
    }

    fun onDismissActorMoviesDialog() {
        _uiState.update {
            it.copy(
                showActorMoviesDialog = false,
                selectedActorName = "",
                selectedActorBio = null,
                availableActorMovies = emptyList(),
                unavailableActorMovies = emptyList()
            )
        }
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
        val videoTracks = mediaPlayer.videoTracks?.map { TrackInfo(it.id, it.name, it.id == mediaPlayer.videoTrack) } ?: emptyList()
        _uiState.update { it.copy(availableAudioTracks = audioTracks, availableSubtitleTracks = subtitleTracks, availableVideoTracks = videoTracks) }
    }
    fun toggleAudioMenu(show: Boolean) = _uiState.update { it.copy(showAudioMenu = show) }
    fun toggleSubtitleMenu(show: Boolean) = _uiState.update { it.copy(showSubtitleMenu = show) }
    fun toggleVideoMenu(show: Boolean) = _uiState.update { it.copy(showVideoMenu = show) }
    fun selectAudioTrack(trackId: Int) { mediaPlayer.setAudioTrack(trackId); updateTrackInfo() }
    fun selectSubtitleTrack(trackId: Int) { mediaPlayer.setSpuTrack(trackId); updateTrackInfo() }
    fun selectVideoTrack(trackId: Int) { mediaPlayer.setVideoTrack(trackId); updateTrackInfo() }
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
