package com.kybers.play.ui.series

import android.app.Application
import android.media.AudioManager
import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Season
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.player.AspectRatioMode
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

// --- ¡NUEVO ESTADO AÑADIDO! ---
// Data class para el estado de la UI de la pantalla de detalles de una serie.
data class SeriesDetailsUiState(
    val isLoading: Boolean = true,
    val seriesInfo: Series? = null,
    val seasons: List<Season> = emptyList(),
    val episodesBySeason: Map<Int, List<Episode>> = emptyMap(),
    val selectedSeasonNumber: Int = 1,
    val error: String? = null,
    // --- ESTADOS DEL REPRODUCTOR ---
    val currentlyPlayingEpisode: Episode? = null,
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

/**
 * --- ¡VIEWMODEL COMPLETAMENTE RENOVADO! ---
 * ViewModel para la pantalla de Detalles de Series.
 * Ahora gestiona tanto los detalles de la serie como el estado completo del reproductor de video.
 */
class SeriesDetailsViewModel(
    application: Application,
    private val vodRepository: VodRepository,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val seriesId: Int
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SeriesDetailsUiState())
    val uiState: StateFlow<SeriesDetailsUiState> = _uiState.asStateFlow()

    private val libVLC: LibVLC = LibVLC(application)
    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)
    private val vlcOptions = arrayListOf("--network-caching=3000", "--file-caching=3000")

    init {
        loadSeriesDetails()
        setupMediaPlayer()
    }

    private fun loadSeriesDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val baseSeries = vodRepository.getAllSeries(currentUser.id).first().find { it.seriesId == seriesId }
            if (baseSeries == null) {
                _uiState.update { it.copy(isLoading = false, error = "Serie no encontrada.") }
                return@launch
            }

            vodRepository.getSeriesDetails(
                user = currentUser.username,
                pass = currentUser.password,
                seriesId = seriesId,
                userId = currentUser.id
            ).catch { e ->
                _uiState.update { it.copy(isLoading = false, error = "Error al cargar detalles: ${e.message}") }
            }.collect { seriesInfoResponse ->
                if (seriesInfoResponse != null) {
                    val episodesMappedByInt = seriesInfoResponse.episodes.mapKeys { it.key.toInt() }
                    val updatedSeries = baseSeries.copy(
                        plot = seriesInfoResponse.info.plot ?: baseSeries.plot,
                        cast = seriesInfoResponse.info.cast ?: baseSeries.cast,
                        director = seriesInfoResponse.info.director ?: baseSeries.director,
                        genre = seriesInfoResponse.info.genre ?: baseSeries.genre,
                        releaseDate = seriesInfoResponse.info.releaseDate ?: baseSeries.releaseDate,
                        rating5Based = seriesInfoResponse.info.rating5Based ?: baseSeries.rating5Based,
                        backdropPath = seriesInfoResponse.info.backdropPath ?: baseSeries.backdropPath,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            seriesInfo = updatedSeries,
                            seasons = seriesInfoResponse.seasons.sortedBy { s -> s.seasonNumber },
                            episodesBySeason = episodesMappedByInt,
                            selectedSeasonNumber = seriesInfoResponse.seasons.minOfOrNull { s -> s.seasonNumber } ?: 1
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, seriesInfo = baseSeries, error = "No se encontraron detalles para esta serie.") }
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
    }

    // --- LÓGICA DEL REPRODUCTOR ---

    private fun setupMediaPlayer() {
        mediaPlayer.setEventListener { event ->
            val currentState = _uiState.value.playerStatus
            val newStatus = when (event.type) {
                MediaPlayer.Event.Playing -> PlayerStatus.PLAYING
                MediaPlayer.Event.Paused -> PlayerStatus.PAUSED
                MediaPlayer.Event.Buffering -> if (currentState != PlayerStatus.PLAYING) PlayerStatus.BUFFERING else null
                MediaPlayer.Event.EncounteredError -> PlayerStatus.ERROR
                MediaPlayer.Event.EndReached -> {
                    playNextEpisode() // ¡Reproducción automática del siguiente episodio!
                    PlayerStatus.IDLE
                }
                MediaPlayer.Event.Stopped -> PlayerStatus.IDLE
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

    fun playEpisode(episode: Episode, continueFromLastPosition: Boolean = false) {
        val streamUrl = buildStreamUrl(episode)
        val newMedia = Media(libVLC, streamUrl.toUri()).apply {
            vlcOptions.forEach { addOption(it) }
        }
        mediaPlayer.media?.release()
        mediaPlayer.media = newMedia
        mediaPlayer.play()

        val position = if (continueFromLastPosition) {
            preferenceManager.getPlaybackPosition(episode.id)
        } else {
            0L
        }
        if (position > 0) {
            mediaPlayer.time = position
        }

        _uiState.update {
            it.copy(
                isPlayerVisible = true,
                playerStatus = PlayerStatus.BUFFERING,
                currentlyPlayingEpisode = episode
            )
        }
    }

    fun playNextEpisode() {
        val currentEpisode = _uiState.value.currentlyPlayingEpisode ?: return
        val currentSeasonEpisodes = _uiState.value.episodesBySeason[_uiState.value.selectedSeasonNumber] ?: return
        val currentIndex = currentSeasonEpisodes.indexOf(currentEpisode)

        if (currentIndex != -1 && currentIndex < currentSeasonEpisodes.size - 1) {
            val nextEpisode = currentSeasonEpisodes[currentIndex + 1]
            playEpisode(nextEpisode)
        } else {
            // Opcional: pasar a la siguiente temporada o simplemente parar.
            hidePlayer()
        }
    }

    fun playPreviousEpisode() {
        val currentEpisode = _uiState.value.currentlyPlayingEpisode ?: return
        val currentSeasonEpisodes = _uiState.value.episodesBySeason[_uiState.value.selectedSeasonNumber] ?: return
        val currentIndex = currentSeasonEpisodes.indexOf(currentEpisode)

        if (currentIndex > 0) {
            val previousEpisode = currentSeasonEpisodes[currentIndex - 1]
            playEpisode(previousEpisode)
        }
    }

    fun hidePlayer() {
        val episodeId = _uiState.value.currentlyPlayingEpisode?.id
        if (mediaPlayer.isPlaying && episodeId != null) {
            preferenceManager.savePlaybackPosition(episodeId, mediaPlayer.time)
        }
        mediaPlayer.stop()
        mediaPlayer.media?.release()
        mediaPlayer.media = null
        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                isFullScreen = false,
                playerStatus = PlayerStatus.IDLE,
                currentPosition = 0L,
                duration = 0L,
                isInPipMode = false,
                currentlyPlayingEpisode = null
            )
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
    }

    fun seekTo(position: Long) {
        mediaPlayer.time = position
    }

    private fun buildStreamUrl(episode: Episode): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}series/${currentUser.username}/${currentUser.password}/${episode.id}.${episode.containerExtension}"
    }

    override fun onCleared() {
        super.onCleared()
        hidePlayer() // Asegura que se guarde la posición al salir
        mediaPlayer.release()
        libVLC.release()
    }

    // --- MÉTODOS DE CONTROL DE UI DEL REPRODUCTOR (similares a MovieDetailsViewModel) ---

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

    fun setInPipMode(inPip: Boolean) {
        _uiState.update { it.copy(isInPipMode = inPip) }
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
}
