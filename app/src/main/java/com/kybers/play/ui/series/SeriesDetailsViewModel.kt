package com.kybers.play.ui.series

import android.app.Application
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Season
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbTvResult
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.player.AspectRatioMode
import com.kybers.play.ui.player.PlayerStatus
import com.kybers.play.ui.player.TrackInfo
import com.kybers.play.player.MediaManager
import com.kybers.play.player.RetryManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import java.util.concurrent.TimeUnit

data class SeriesDetailsUiState(
    val isLoading: Boolean = true,
    val isLoadingImages: Boolean = false,
    val seriesInfo: Series? = null,
    val seasons: List<Season> = emptyList(),
    val episodesBySeason: Map<Int, List<Episode>> = emptyMap(),
    val selectedSeasonNumber: Int = 1,
    val error: String? = null,
    val title: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val firstAirYear: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val cast: List<TMDbCastMember> = emptyList(),
    val availableRecommendations: List<Series> = emptyList(),
    val availableSimilarSeries: List<Series> = emptyList(),
    val certification: String? = null,
    val selectedTabIndex: Int = 0,
    val currentlyPlayingEpisode: Episode? = null,
    val isPlayerVisible: Boolean = false,
    val playerStatus: PlayerStatus = PlayerStatus.IDLE,
    val isFullScreen: Boolean = false,
    val isMuted: Boolean = false,
    val isFavorite: Boolean = false,
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
    val playbackStates: Map<String, Pair<Long, Long>> = emptyMap(),
    val retryAttempt: Int = 0,
    val maxRetryAttempts: Int = 3,
    val retryMessage: String? = null
)

class SeriesDetailsViewModel(
    application: Application,
    private val vodRepository: VodRepository,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val seriesId: Int
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SeriesDetailsUiState())
    val uiState: StateFlow<SeriesDetailsUiState> = _uiState.asStateFlow()

    private val libVLC: LibVLC = LibVLC(application)
    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)

    private val mediaManager = MediaManager()
    private lateinit var retryManager: RetryManager

    private var lastSaveTimeMillis: Long = 0L
    private val saveIntervalMillis: Long = 15000
    private var softwareFallbackTried = false

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    init {
        loadSeriesDetails()
        setupMediaPlayer()
        setupRetryManager()
    }

    private fun loadSeriesDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val baseSeries = vodRepository.getAllSeries(currentUser.id).first().find { it.seriesId == seriesId }
            if (baseSeries == null) {
                _uiState.update { it.copy(isLoading = false, error = "Serie no encontrada.") }
                return@launch
            }

            val enrichedDetails = detailsRepository.getSeriesDetails(baseSeries)
            val castList = parseCastJson(enrichedDetails?.castJson)
            val recommendationsList = parseRecommendationsJson(enrichedDetails?.recommendationsJson)

            _uiState.update {
                it.copy(
                    seriesInfo = baseSeries,
                    title = baseSeries.name,
                    plot = enrichedDetails?.plot ?: baseSeries.plot,
                    posterUrl = enrichedDetails?.posterUrl ?: baseSeries.cover,
                    backdropUrl = enrichedDetails?.backdropUrl ?: baseSeries.backdropPath?.firstOrNull(),
                    firstAirYear = enrichedDetails?.firstAirYear ?: baseSeries.releaseDate?.substringBefore("-"),
                    rating = enrichedDetails?.rating ?: (baseSeries.rating5Based.toDouble() * 2),
                    certification = enrichedDetails?.certification,
                    cast = castList
                )
            }

            findAvailableRecommendations(recommendationsList)
            loadFavoriteStatus()
            loadEpisodes()
        }
    }

    private fun loadEpisodes() {
        viewModelScope.launch {
            vodRepository.getSeriesDetails(
                user = currentUser.username,
                pass = currentUser.password,
                seriesId = seriesId,
                userId = currentUser.id
            ).catch { e ->
                _uiState.update { it.copy(isLoading = false, error = "Error al cargar episodios: ${e.message}") }
            }.collect { seriesInfoResponse ->
                if (seriesInfoResponse != null) {
                    // Mostrar la UI inmediatamente con los datos básicos
                    val basicEpisodesMap = seriesInfoResponse.episodes.mapKeys { it.key.toInt() }
                    
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            seasons = seriesInfoResponse.seasons.sortedBy { season -> season.seasonNumber },
                            episodesBySeason = basicEpisodesMap,
                            selectedSeasonNumber = seriesInfoResponse.seasons.minOfOrNull { season -> season.seasonNumber } ?: 1,
                            isLoadingImages = true
                        )
                    }
                    
                    loadPlaybackStatesForSeason(_uiState.value.selectedSeasonNumber)
                    
                    // Cargar imágenes de TMDb en segundo plano de forma progresiva
                    loadEpisodeImagesProgressively(basicEpisodesMap)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "No se encontraron episodios para esta serie.") }
                }
            }
        }
    }

    private fun loadEpisodeImagesProgressively(episodesMap: Map<Int, List<Episode>>) {
        viewModelScope.launch {
            val tmdbId = _uiState.value.seriesInfo?.tmdbId?.toIntOrNull()
            if (tmdbId == null) {
                _uiState.update { it.copy(isLoadingImages = false) }
                return@launch
            }
            
            val selectedSeason = _uiState.value.selectedSeasonNumber
            val sortedSeasons = episodesMap.keys.sortedWith { a, b ->
                when {
                    a == selectedSeason -> -1
                    b == selectedSeason -> 1
                    else -> a.compareTo(b)
                }
            }

            var last429Time = 0L
            var retryAfterMs = 0L

            for (seasonNumber in sortedSeasons) {
                val episodes = episodesMap[seasonNumber] ?: continue
                val enrichedEpisodes = episodes.map { episode ->
                    if (episode.imageUrl.isNullOrBlank()) {
                        var success = false
                        var attempts = 0
                        while (!success && attempts < 3) {
                            try {
                                val response = detailsRepository.getEpisodeImageFromTMDbWithResponse(
                                    tvId = tmdbId,
                                    seasonNumber = episode.season,
                                    episodeNumber = episode.episodeNum,
                                    episodeId = episode.id
                                )
                                if (response.isSuccessful) {
                                    val tmdbImage = response.body()
                                    if (tmdbImage != null) {
                                        episode.imageUrl = tmdbImage
                                    }
                                    success = true
                                } else if (response.code() == 429) {
                                    // Límite de peticiones superado
                                    val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 1L
                                    retryAfterMs = retryAfter * 1000L
                                    last429Time = System.currentTimeMillis()
                                    Log.w("SeriesDetailsViewModel", "TMDb rate limit reached. Waiting $retryAfterMs ms.")
                                    delay(retryAfterMs)
                                } else {
                                    Log.w("SeriesDetailsViewModel", "TMDb error: ${response.code()} - ${response.message()}")
                                    success = true // No reintentar otros errores
                                }
                            } catch (e: Exception) {
                                Log.w("SeriesDetailsViewModel", "Failed to load image for episode ${episode.id}: ${e.message}")
                                attempts++
                                delay(200)
                            }
                        }
                    }
                    episode
                }
                _uiState.update { currentState ->
                    val updatedEpisodesMap = currentState.episodesBySeason.toMutableMap()
                    updatedEpisodesMap[seasonNumber] = enrichedEpisodes
                    currentState.copy(episodesBySeason = updatedEpisodesMap)
                }
                delay(30)
            }
            _uiState.update { it.copy(isLoadingImages = false) }
        }
    }

    private suspend fun findAvailableRecommendations(recommendations: List<TMDbTvResult>) {
        val allLocalSeries = vodRepository.getAllSeries(currentUser.id).first()
        val availableRecs = allLocalSeries.filter { localSeries ->
            recommendations.any { recommendation -> recommendation.id.toString() == localSeries.tmdbId }
        }
        _uiState.update { it.copy(availableRecommendations = availableRecs.distinctBy { series -> series.seriesId }) }
    }

    private fun parseCastJson(json: String?): List<TMDbCastMember> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, TMDbCastMember::class.java)
            val adapter = moshi.adapter<List<TMDbCastMember>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun parseRecommendationsJson(json: String?): List<TMDbTvResult> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, TMDbTvResult::class.java)
            val adapter = moshi.adapter<List<TMDbTvResult>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
        loadPlaybackStatesForSeason(seasonNumber)
    }

    private fun loadPlaybackStatesForSeason(seasonNumber: Int) {
        val allStates = preferenceManager.getAllEpisodePlaybackStates()
        _uiState.update { it.copy(playbackStates = allStates) }
    }

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }

    private fun setupMediaPlayer() {
        mediaPlayer.setEventListener { event ->
            val currentState = _uiState.value.playerStatus
            val newStatus = when (event.type) {
                MediaPlayer.Event.Playing -> PlayerStatus.PLAYING
                MediaPlayer.Event.Paused -> PlayerStatus.PAUSED
                MediaPlayer.Event.Buffering -> if (currentState != PlayerStatus.PLAYING) PlayerStatus.BUFFERING else null
                MediaPlayer.Event.EncounteredError -> {
                    Log.e("SeriesDetailsViewModel", "VLC encountered error")
                    val currentEpisode = _uiState.value.currentlyPlayingEpisode
                    if (!softwareFallbackTried && currentEpisode != null) {
                        softwareFallbackTried = true
                        retryManager.startRetry(viewModelScope) {
                            try {
                                startEpisodePlaybackInternal(currentEpisode, resumeFromSaved = false, forceSoftwareDecoding = true)
                            } catch (e: Exception) {
                                Log.e("SeriesDetailsViewModel", "Retry with software decoding failed: ${e.message}", e)
                                false
                            }
                        }
                        null
                    } else {
                        // Only trigger retry if we're not already in a retry state
                        if (currentState != PlayerStatus.RETRYING && currentState != PlayerStatus.RETRY_FAILED) {
                            Log.d("SeriesDetailsViewModel", "VLC error occurred outside retry system, triggering retry")
                            if (currentEpisode != null && !retryManager.isRetrying()) {
                                retryManager.startRetry(viewModelScope) {
                                    try {
                                        startEpisodePlaybackInternal(currentEpisode, false)
                                    } catch (e: Exception) {
                                        Log.e("SeriesDetailsViewModel", "Retry failed: ${e.message}", e)
                                        false
                                    }
                                }
                            }
                        } else {
                            Log.d("SeriesDetailsViewModel", "VLC error during retry - letting retry system handle it")
                        }
                        PlayerStatus.ERROR
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    saveCurrentEpisodeProgress(markAsFinished = true)
                    playNextEpisode()
                    null
                }
                MediaPlayer.Event.Stopped -> PlayerStatus.IDLE
                MediaPlayer.Event.TimeChanged -> {
                    _uiState.update { it.copy(currentPosition = event.timeChanged, duration = mediaPlayer.length) }
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSaveTimeMillis > saveIntervalMillis) {
                        saveCurrentEpisodeProgress()
                        lastSaveTimeMillis = currentTime
                    }
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

    private fun setupRetryManager() {
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
                mediaManager.stopAndReleaseMedia(mediaPlayer)
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

    fun playEpisode(episode: Episode) {
        val previousEpisode = _uiState.value.currentlyPlayingEpisode
        if (mediaPlayer.isPlaying && previousEpisode != null && previousEpisode.id != episode.id) {
            saveCurrentEpisodeProgress()
        }

        softwareFallbackTried = false
        _uiState.update {
            it.copy(
                currentlyPlayingEpisode = episode,
                isPlayerVisible = true,
                playerStatus = PlayerStatus.LOADING,
                retryAttempt = 0,
                retryMessage = null
            )
        }

        // Try to play the episode with retry mechanism
        retryManager.startRetry(viewModelScope) {
            try {
                startEpisodePlaybackInternal(episode, true)
            } catch (e: Exception) {
                Log.e("SeriesDetailsViewModel", "Failed to play episode: ${e.message}", e)
                false
            }
        }
    }

    private suspend fun startEpisodePlaybackInternal(episode: Episode, resumeFromSaved: Boolean, forceSoftwareDecoding: Boolean = false): Boolean {
        return try {
            val streamUrl = buildStreamUrl(episode)
            val vlcOptions = preferenceManager.getVLCOptions().apply {
                if (forceSoftwareDecoding) {
                    removeAll { it.startsWith("--avcodec-hw") }
                    add("--avcodec-hw=none")
                }
            }
            val newMedia = Media(libVLC, streamUrl.toUri()).apply {
                vlcOptions.forEach { addOption(it) }
            }

            // Use MediaManager for safe media handling
            mediaManager.setMediaSafely(mediaPlayer, newMedia)
            mediaPlayer.play()

            // --- ¡LÓGICA DE REANUDACIÓN CORREGIDA! ---
            if (resumeFromSaved) {
                val (position, duration) = preferenceManager.getEpisodePlaybackState(episode.id)
                val isFinished = duration > 0 && (duration - position) < 5000

                if (position > 0 && !isFinished) {
                    mediaPlayer.time = position
                }
            }

            _uiState.update {
                it.copy(
                    isPlayerVisible = true,
                    playerStatus = PlayerStatus.BUFFERING,
                    currentlyPlayingEpisode = episode
                )
            }
            
            // Wait for VLC to actually start playing or fail (max 10 seconds)
            var attempts = 0
            val maxAttempts = 100 // 10 seconds (100ms intervals)
            
            while (attempts < maxAttempts) {
                delay(100) // Check every 100ms
                attempts++
                
                when {
                    mediaPlayer.isPlaying -> {
                        Log.d("SeriesDetailsViewModel", "Episode successfully started playing")
                        return true
                    }
                    _uiState.value.playerStatus == PlayerStatus.ERROR -> {
                        Log.e("SeriesDetailsViewModel", "VLC reported error during playback attempt")
                        return false
                    }
                }
            }
            
            // Timeout - VLC didn't start playing within reasonable time
            Log.e("SeriesDetailsViewModel", "Timeout waiting for episode to start playing")
            _uiState.update { it.copy(playerStatus = PlayerStatus.ERROR) }
            false
            
        } catch (e: Exception) {
            Log.e("SeriesDetailsViewModel", "Error in startEpisodePlaybackInternal", e)
            _uiState.update { it.copy(playerStatus = PlayerStatus.ERROR) }
            throw e
        }
    }

    // --- ¡FUNCIÓN DE GUARDADO CORREGIDA! ---
    private fun saveCurrentEpisodeProgress(markAsFinished: Boolean = false) {
        val episode = _uiState.value.currentlyPlayingEpisode ?: return
        val duration = mediaPlayer.length
        if (duration <= 0 && !markAsFinished) return

        val position = mediaPlayer.time
        val isConsideredFinished = markAsFinished || (duration > 0 && (duration - position) < 5000)
        val finalPosition = if (isConsideredFinished) duration else position

        // Guardamos la posición del episodio y de la serie para la pantalla principal
        preferenceManager.saveEpisodePlaybackState(episode.id, finalPosition, duration)

        val allEpisodes = _uiState.value.episodesBySeason.values.flatten()
        val currentIndex = allEpisodes.indexOf(episode)
        val isLastEpisode = currentIndex == allEpisodes.lastIndex
        val seriesProgress = if (isLastEpisode && isConsideredFinished) 0L else finalPosition
        preferenceManager.savePlaybackPosition(seriesId.toString(), seriesProgress)

        // Actualizamos el estado de la UI para que la barra de progreso se actualice al instante.
        _uiState.update {
            it.copy(playbackStates = it.playbackStates + (episode.id to (finalPosition to duration)))
        }
    }

    fun playNextEpisode() {
        saveCurrentEpisodeProgress(markAsFinished = true)
        val currentEpisode = _uiState.value.currentlyPlayingEpisode ?: return
        val currentSeasonEpisodes = _uiState.value.episodesBySeason[_uiState.value.selectedSeasonNumber] ?: return
        val currentIndex = currentSeasonEpisodes.indexOf(currentEpisode)

        if (currentIndex != -1 && currentIndex < currentSeasonEpisodes.size - 1) {
            val nextEpisode = currentSeasonEpisodes[currentIndex + 1]
            playEpisode(nextEpisode)
        } else {
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
        retryManager.cancelRetry()
        saveCurrentEpisodeProgress()
        mediaManager.stopAndReleaseMedia(mediaPlayer)
        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                isFullScreen = false,
                playerStatus = PlayerStatus.IDLE,
                currentPosition = 0L,
                duration = 0L,
                isInPipMode = false,
                currentlyPlayingEpisode = null,
                retryAttempt = 0,
                retryMessage = null
            )
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
    }

    fun seekTo(position: Long) {
        mediaPlayer.time = position
    }

    /**
     * Manually retry playback for current episode
     */
    fun retryCurrentEpisode() {
        val currentEpisode = _uiState.value.currentlyPlayingEpisode
        if (currentEpisode != null) {
            playEpisode(currentEpisode)
        }
    }

    /**
     * Find the last watched episode (the one with the highest progress that's not completed)
     */
    fun getLastWatchedEpisode(): Episode? {
        val playbackStates = _uiState.value.playbackStates
        val allEpisodes = _uiState.value.episodesBySeason.values.flatten()
        
        return allEpisodes
            .filter { episode ->
                val state = playbackStates[episode.id]
                val progress = if (state != null && state.second > 0) {
                    state.first.toFloat() / state.second.toFloat()
                } else 0f
                // Consider episodes with progress > 5% and < 90% as "in progress"
                progress > 0.05f && progress < 0.90f
            }
            .maxByOrNull { episode ->
                // Return the episode with the most recent progress (highest position)
                playbackStates[episode.id]?.first ?: 0L
            }
    }
    
    /**
     * Obtiene el episodio adecuado para continuar.
     * - Si hay un episodio en progreso, lo devuelve.
     * - Si todos los episodios anteriores están completos, devuelve el siguiente sin ver.
     * - En caso contrario, el primer episodio de la serie.
     */
    fun getContinueWatchingEpisode(): Episode? {
        val playbackStates = _uiState.value.playbackStates
        val episodes = _uiState.value.episodesBySeason.values.flatten()
            .sortedWith(compareBy<Episode>({ it.season }, { it.episodeNum }))

        // Episodio en progreso
        episodes.firstOrNull { episode ->
            val state = playbackStates[episode.id]
            val progress = if (state != null && state.second > 0) {
                state.first.toFloat() / state.second.toFloat()
            } else 0f
            progress > 0.05f && progress < 0.90f
        }?.let { return it }

        // Siguiente después del último completado
        val lastCompletedIndex = episodes.indexOfLast { episode ->
            val state = playbackStates[episode.id]
            val progress = if (state != null && state.second > 0) {
                state.first.toFloat() / state.second.toFloat()
            } else 0f
            progress >= 0.90f
        }
        if (lastCompletedIndex != -1 && lastCompletedIndex < episodes.lastIndex) {
            return episodes[lastCompletedIndex + 1]
        }

        // Default al primer episodio
        return episodes.firstOrNull()
    }
    
    /**
     * Continue watching from the appropriate episode
     */
    fun continueWatching() {
        getContinueWatchingEpisode()?.let { episode ->
            playEpisode(episode)
        }
    }

    private fun buildStreamUrl(episode: Episode): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}series/${currentUser.username}/${currentUser.password}/${episode.id}.${episode.containerExtension}"
    }

    override fun onCleared() {
        super.onCleared()
        retryManager.cancelRetry()
        hidePlayer()
        mediaPlayer.release()
        libVLC.release()
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
    fun selectAudioTrack(trackId: Int) {
        mediaPlayer.setAudioTrack(trackId)
        updateTrackInfo()
    }
    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer.setSpuTrack(trackId)
        updateTrackInfo()
    }

    /**
     * Updates player settings dynamically when preferences change.
     * This allows immediate application of new settings without restart.
     */
    fun updatePlayerSettings() {
        // Only update if media is currently playing
        if (mediaPlayer.isPlaying) {
            val currentPosition = mediaPlayer.time
            val currentMedia = mediaPlayer.media
            
            // Recreate media with new VLC options
            currentMedia?.let { media ->
                val newOptions = preferenceManager.getVLCOptions()
                val newMedia = Media(libVLC, media.uri).apply {
                    newOptions.forEach { addOption(it) }
                }
                mediaManager.setMediaSafely(mediaPlayer, newMedia)
                mediaPlayer.play()
                mediaPlayer.time = currentPosition
            }
        }
    }

    fun toggleFavorite() {
        val currentSeries = _uiState.value.seriesInfo ?: return
        val isFavorite = !_uiState.value.isFavorite
        
        viewModelScope.launch {
            if (isFavorite) {
                preferenceManager.addFavoriteSeries(currentSeries.seriesId)
            } else {
                preferenceManager.removeFavoriteSeries(currentSeries.seriesId)
            }
            _uiState.update { it.copy(isFavorite = isFavorite) }
        }
    }

    private fun loadFavoriteStatus() {
        viewModelScope.launch {
            val seriesId = _uiState.value.seriesInfo?.seriesId ?: return@launch
            val isFavorite = preferenceManager.isSerisFavorite(seriesId)
            _uiState.update { it.copy(isFavorite = isFavorite) }
        }
    }
}
