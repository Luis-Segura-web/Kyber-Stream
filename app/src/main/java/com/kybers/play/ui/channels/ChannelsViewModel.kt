package com.kybers.play.ui.channels

import android.app.Application
import android.media.AudioManager
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Enum para representar el estado actual del reproductor.
enum class PlayerStatus {
    IDLE, BUFFERING, PLAYING, ERROR, PAUSED
}

// Enum para las opciones de ordenamiento.
enum class SortOrder {
    DEFAULT, // Orden original del servicio (por ID o el que venga por defecto de la API)
    AZ,      // Alfabético A-Z
    ZA       // Alfabético Z-A
}

// Enum para los modos de relación de aspecto.
enum class AspectRatioMode {
    FIT_SCREEN,   // Ajustar a la pantalla (por defecto de VLC, escala 0.0f)
    FILL_SCREEN,  // Llenar la pantalla (puede recortar, escala 1.0f si no hay ratio específico)
    ASPECT_16_9,  // Relación de aspecto 16:9
    ASPECT_4_3    // Relación de aspecto 4:3
}

// Data class para representar una categoría de canales que puede expandirse o contraerse.
data class ExpandableCategory(
    val category: Category,
    val channels: List<LiveStream> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false
)

// Data class para la información de las pistas de audio, subtítulos o video.
data class TrackInfo(
    val id: Int,
    val name: String,
    val isSelected: Boolean
)

// Data class que representa el estado completo de la UI de la pantalla de canales.
data class ChannelsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val categories: List<ExpandableCategory> = emptyList(),
    val currentlyPlaying: LiveStream? = null,
    val isPlayerVisible: Boolean = false,
    val screenTitle: String = "Canales",
    val currentChannelIndex: Int = -1,
    val favoriteChannelIds: Set<String> = emptySet(),
    val isFavoritesCategoryExpanded: Boolean = false,
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
    val categorySortOrder: SortOrder = SortOrder.DEFAULT,
    val channelSortOrder: SortOrder = SortOrder.DEFAULT,
    val showSortMenu: Boolean = false,
    val currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT_SCREEN,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isInPipMode: Boolean = false,
    val lastUpdatedTimestamp: Long = 0L,
    val isRefreshing: Boolean = false
)

open class ChannelsViewModel(
    application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager
) : AndroidViewModel(application) {

    private lateinit var libVLC: LibVLC
    lateinit var mediaPlayer: MediaPlayer
        private set

    private val _uiState = MutableStateFlow(ChannelsUiState())
    open val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _originalCategories = MutableStateFlow<List<ExpandableCategory>>(emptyList())
    private val _favoriteChannelIds = MutableStateFlow<Set<String>>(emptySet())

    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()

    private val vlcOptions = arrayListOf(
        "--network-caching=3000",
        "--file-caching=3000",
        "--clock-jitter=0",
        "--clock-synchro=0",
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--sout-ts-dts-delay=0",
        "--no-ts-trust-pcr"
    )

    private val vlcPlayerListener = MediaPlayer.EventListener { event ->
        val currentState = _uiState.value.playerStatus
        val newStatus = when (event.type) {
            MediaPlayer.Event.Playing -> {
                updateTrackInfo()
                PlayerStatus.PLAYING
            }
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
        if (newStatus != null && newStatus != currentState) {
            _uiState.update { it.copy(playerStatus = newStatus) }
        }
    }

    init {
        setupVLC()
        // Las preferencias de ordenación y aspecto son globales, no por usuario.
        val savedCategorySortOrder = preferenceManager.getSortOrder("category").toSortOrder()
        val savedChannelSortOrder = preferenceManager.getSortOrder("channel").toSortOrder()
        val savedAspectRatioMode = preferenceManager.getAspectRatioMode().toAspectRatioMode()
        // La última sincronización SÍ es por usuario.
        val lastSyncTime = syncManager.getLastSyncTimestamp(currentUser.id)

        _uiState.update {
            it.copy(
                categorySortOrder = savedCategorySortOrder,
                channelSortOrder = savedChannelSortOrder,
                currentAspectRatioMode = savedAspectRatioMode,
                lastUpdatedTimestamp = lastSyncTime
            )
        }
        loadInitialCategories()
        viewModelScope.launch {
            _favoriteChannelIds.collect { favorites ->
                _uiState.update { it.copy(favoriteChannelIds = favorites) }
                filterAndSortCategories()
            }
        }
    }

    private fun setupVLC() {
        libVLC = LibVLC(getApplication(), vlcOptions)
        mediaPlayer = MediaPlayer(libVLC).apply {
            setEventListener(vlcPlayerListener)
        }
    }

    open fun onChannelSelected(channel: LiveStream) {
        viewModelScope.launch {
            // Asegurarse de que 'channel' es accesible aquí.
            // Si el error persiste, la estructura de la función onChannelSelected podría estar dañada.
            val allVisibleChannels = (if (_uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                    _uiState.value.categories.flatMap { it.channels }
            val index = allVisibleChannels.indexOfFirst { it.streamId == channel.streamId }

            _uiState.update {
                it.copy(
                    currentlyPlaying = channel,
                    isPlayerVisible = true,
                    screenTitle = channel.name,
                    currentChannelIndex = index,
                    playerStatus = PlayerStatus.BUFFERING,
                    availableAudioTracks = emptyList(),
                    availableSubtitleTracks = emptyList(),
                    availableVideoTracks = emptyList(),
                    currentPosition = 0L,
                    duration = 0L
                )
            }

            val streamUrl = buildStreamUrl(channel)

            val media = Media(libVLC, streamUrl.toUri()).apply {
                vlcOptions.forEach { addOption(it) }
            }

            mediaPlayer.media = media
            mediaPlayer.play()
            mediaPlayer.volume = if (_uiState.value.isMuted || _uiState.value.playerStatus == PlayerStatus.PAUSED) 0 else 100

            applyAspectRatio(_uiState.value.currentAspectRatioMode)
        }
    }

    fun seekTo(position: Long) {
        mediaPlayer.time = position
        _uiState.update { it.copy(currentPosition = position) }
    }

    // --- System Controls State Management ---

    fun setInitialSystemValues(volume: Int, maxVolume: Int, brightness: Float) {
        _uiState.update {
            it.copy(
                systemVolume = volume,
                maxSystemVolume = maxVolume,
                originalBrightness = brightness,
                screenBrightness = brightness
            )
        }
    }

    fun updateSystemVolume(newVolume: Int) {
        _uiState.update { it.copy(systemVolume = newVolume) }
    }

    fun setScreenBrightness(newBrightness: Float) {
        _uiState.update { it.copy(screenBrightness = newBrightness.coerceIn(0f, 1f)) }
    }

    fun onToggleMute(audioManager: AudioManager) {
        val isMuted = !_uiState.value.isMuted
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
        _uiState.update { it.copy(isMuted = isMuted) }
    }

    // --- Track Selection ---

    private fun updateTrackInfo() {
        val audioTracks = mediaPlayer.audioTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.audioTrack)
        } ?: emptyList()

        val subtitleTracks = mediaPlayer.spuTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.spuTrack)
        } ?: emptyList()

        val videoTracks = mediaPlayer.videoTracks?.map {
            TrackInfo(it.id, it.name, it.id == mediaPlayer.videoTrack)
        } ?: emptyList()

        _uiState.update {
            it.copy(
                availableAudioTracks = audioTracks,
                availableSubtitleTracks = subtitleTracks,
                availableVideoTracks = videoTracks
            )
        }
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer.setAudioTrack(trackId)
        toggleAudioMenu(false)
        updateTrackInfo()
    }

    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer.setSpuTrack(trackId)
        toggleSubtitleMenu(false)
        updateTrackInfo()
    }

    fun selectVideoTrack(trackId: Int) {
        mediaPlayer.setVideoTrack(trackId)
        toggleVideoMenu(false)
        updateTrackInfo()
    }

    fun toggleAudioMenu(show: Boolean) = _uiState.update { it.copy(showAudioMenu = show) }
    fun toggleSubtitleMenu(show: Boolean) = _uiState.update { it.copy(showSubtitleMenu = show) }
    fun toggleVideoMenu(show: Boolean) = _uiState.update { it.copy(showVideoMenu = show) }

    // --- Playback and Navigation ---

    fun retryPlayback() {
        uiState.value.currentlyPlaying?.let { onChannelSelected(it) }
    }

    fun onToggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun setInPipMode(isInPip: Boolean) {
        _uiState.update { it.copy(isInPipMode = isInPip) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.stop()
        mediaPlayer.setEventListener(null)
        mediaPlayer.release()
        libVLC.release()
    }

    open fun toggleFavorite(channelId: String) {
        _favoriteChannelIds.update { currentFavorites ->
            if (currentFavorites.contains(channelId)) {
                currentFavorites - channelId
            } else {
                currentFavorites + channelId
            }
        }
    }

    open fun playNextChannel() {
        viewModelScope.launch {
            val allVisibleChannels = (if (_uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                    _uiState.value.categories.flatMap { it.channels }
            val currentIndex = _uiState.value.currentChannelIndex
            if (currentIndex != -1 && currentIndex < allVisibleChannels.size - 1) {
                onChannelSelected(allVisibleChannels[currentIndex + 1])
            }
        }
    }

    open fun playPreviousChannel() {
        viewModelScope.launch {
            val allVisibleChannels = (if (_uiState.value.isFavoritesCategoryExpanded) getFavoriteChannels() else emptyList()) +
                    _uiState.value.categories.flatMap { it.channels }
            val currentIndex = _uiState.value.currentChannelIndex
            if (currentIndex > 0) {
                onChannelSelected(allVisibleChannels[currentIndex - 1])
            }
        }
    }

    open fun hidePlayer() {
        mediaPlayer.stop()
        _uiState.update {
            it.copy(
                isPlayerVisible = false,
                isFullScreen = false,
                playerStatus = PlayerStatus.IDLE,
                currentPosition = 0L,
                duration = 0L,
                isInPipMode = false
            )
        }
    }

    /**
     * Función para actualizar manualmente los canales.
     * Muestra un indicador de carga, realiza la sincronización de datos
     * y actualiza la marca de tiempo de la última actualización.
     * Los datos siempre se cargarán desde el servidor, luego se guardarán en caché y finalmente
     * se mostrarán desde esa caché actualizada.
     */
    fun refreshChannelsManually() {
        viewModelScope.launch {
            Log.d("ChannelsViewModel", "Iniciando refreshChannelsManually()...")
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                withContext(Dispatchers.IO) {
                    Log.d("ChannelsViewModel", "Caché de LiveStreams para userId: ${currentUser.id}")
                    contentRepository.cacheLiveStreams(currentUser.username, currentUser.password, currentUser.id)
                }

                val categories = contentRepository.getLiveCategories(currentUser.username, currentUser.password)
                val allChannels = contentRepository.getAllLiveStreams(currentUser.id).first()
                Log.d("ChannelsViewModel", "Canales obtenidos de la DB para userId ${currentUser.id} después del refresh: ${allChannels.size}")


                val channelsByCategory = allChannels.groupBy { it.categoryId }

                val expandableCategories = categories.map { category ->
                    ExpandableCategory(
                        category = category,
                        channels = channelsByCategory[category.categoryId] ?: emptyList(),
                        isExpanded = false,
                        isLoading = false
                    )
                }
                _originalCategories.value = expandableCategories
                Log.d("ChannelsViewModel", "Categorías originales actualizadas después del refresh: ${_originalCategories.value.size}")
                filterAndSortCategories()

                syncManager.saveLastSyncTimestamp(currentUser.id)
                _uiState.update { it.copy(lastUpdatedTimestamp = syncManager.getLastSyncTimestamp(currentUser.id)) }
                Log.d("ChannelsViewModel", "Sincronización manual completada. Última actualización: ${formatTimestamp(syncManager.getLastSyncTimestamp(currentUser.id))}")

            } catch (e: Exception) {
                Log.e("ChannelsViewModel", "Error en refreshChannelsManually(): ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // --- Aspect Ratio Control ---
    open fun toggleAspectRatio() {
        val nextMode = when (_uiState.value.currentAspectRatioMode) {
            AspectRatioMode.FIT_SCREEN -> AspectRatioMode.FILL_SCREEN
            AspectRatioMode.FILL_SCREEN -> AspectRatioMode.ASPECT_16_9
            AspectRatioMode.ASPECT_16_9 -> AspectRatioMode.ASPECT_4_3
            AspectRatioMode.ASPECT_4_3 -> AspectRatioMode.FIT_SCREEN
        }
        applyAspectRatio(nextMode)
        preferenceManager.saveAspectRatioMode(nextMode.name)
        _uiState.update { it.copy(currentAspectRatioMode = nextMode) }
    }

    private fun applyAspectRatio(mode: AspectRatioMode) {
        when (mode) {
            AspectRatioMode.FIT_SCREEN -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(0.0f)
            }
            AspectRatioMode.FILL_SCREEN -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(1.0f)
            }
            AspectRatioMode.ASPECT_16_9 -> {
                mediaPlayer.setAspectRatio("16:9")
                mediaPlayer.setScale(0.0f)
            }
            AspectRatioMode.ASPECT_4_3 -> {
                mediaPlayer.setAspectRatio("4:3")
                mediaPlayer.setScale(0.0f)
            }
        }
    }

    // --- Category and List Management ---

    open fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterAndSortCategories()
    }

    open fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            Log.d("ChannelsViewModel", "onCategoryToggled() para categoryId: $categoryId, userId: ${currentUser.id}")
            val currentOriginals = _originalCategories.value
            val categoryIndex = currentOriginals.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex == -1) {
                Log.w("ChannelsViewModel", "Categoría no encontrada: $categoryId")
                return@launch
            }

            val clickedCategory = currentOriginals[categoryIndex]
            val isCurrentlyExpanded = clickedCategory.isExpanded

            val newOriginals = currentOriginals.toMutableList()

            for (i in newOriginals.indices) {
                if (newOriginals[i].category.categoryId != categoryId) {
                    newOriginals[i] = newOriginals[i].copy(isExpanded = false)
                }
            }
            _uiState.update { it.copy(isFavoritesCategoryExpanded = false) }

            newOriginals[categoryIndex] = clickedCategory.copy(isExpanded = !isCurrentlyExpanded)
            _originalCategories.value = newOriginals
            Log.d("ChannelsViewModel", "Estado de expansión de categorías actualizado.")

            filterAndSortCategories()
            _scrollToItemEvent.emit(categoryId)
        }
    }


    private fun loadInitialCategories() {
        viewModelScope.launch {
            Log.d("ChannelsViewModel", "Iniciando loadInitialCategories() para userId: ${currentUser.id}")
            _uiState.update { it.copy(isLoading = true) }
            try {
                val categories = contentRepository.getLiveCategories(currentUser.username, currentUser.password)
                val allChannels = contentRepository.getAllLiveStreams(currentUser.id).first()
                Log.d("ChannelsViewModel", "Categorías obtenidas: ${categories.size}, Todos los canales de DB para userId ${currentUser.id}: ${allChannels.size}")

                val channelsByCategory = allChannels.groupBy { it.categoryId }

                val expandableCategories = categories.map { category ->
                    ExpandableCategory(
                        category = category,
                        channels = channelsByCategory[category.categoryId] ?: emptyList(),
                        isExpanded = false,
                        isLoading = false
                    )
                }
                _originalCategories.value = expandableCategories
                _uiState.update { it.copy(isLoading = false, categories = expandableCategories) }
                filterAndSortCategories()
                Log.d("ChannelsViewModel", "Carga inicial de categorías y canales completada.")
            } catch (e: Exception) {
                Log.e("ChannelsViewModel", "Error en loadInitialCategories(): ${e.message}", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun filterAndSortCategories() {
        val query = _uiState.value.searchQuery.trim()
        val masterList = _originalCategories.value
        val currentCategorySortOrder = _uiState.value.categorySortOrder
        val currentChannelSortOrder = _uiState.value.channelSortOrder

        val filteredCategories = masterList.map { originalCategory ->
            val filteredChannels = originalCategory.channels.filter {
                it.name.contains(query, ignoreCase = true)
            }
            originalCategory.copy(channels = filteredChannels)
        }.filter {
            it.category.categoryName.contains(query, ignoreCase = true) || it.channels.isNotEmpty() || query.isBlank()
        }

        val sortedChannelsInCategories = filteredCategories.map { category ->
            val sortedChannels = when (currentChannelSortOrder) {
                SortOrder.AZ -> category.channels.sortedBy { it.name }
                SortOrder.ZA -> category.channels.sortedByDescending { it.name }
                SortOrder.DEFAULT -> category.channels
            }
            category.copy(channels = sortedChannels)
        }

        val finalSortedCategories = when (currentCategorySortOrder) {
            SortOrder.AZ -> sortedChannelsInCategories.sortedBy { it.category.categoryName }
            SortOrder.ZA -> sortedChannelsInCategories.sortedByDescending { it.category.categoryName }
            SortOrder.DEFAULT -> sortedChannelsInCategories
        }

        val categoriesWithCorrectExpansion = finalSortedCategories.map { category ->
            val shouldBeExpanded = if (query.isNotBlank()) {
                category.category.categoryName.contains(query, ignoreCase = true) || category.channels.isNotEmpty()
            } else {
                masterList.find { it.category.categoryId == category.category.categoryId }?.isExpanded ?: false
            }
            category.copy(isExpanded = shouldBeExpanded)
        }

        _uiState.update { it.copy(categories = categoriesWithCorrectExpansion) }
        Log.d("ChannelsViewModel", "filterAndSortCategories() ejecutado. Categorías UI: ${_uiState.value.categories.size}")
    }

    internal suspend fun getFavoriteChannels(): List<LiveStream> {
        val allChannels = contentRepository.getAllLiveStreams(currentUser.id).first().distinctBy { it.streamId }
        val query = _uiState.value.searchQuery.trim()
        val currentChannelSortOrder = _uiState.value.channelSortOrder

        val favoriteChannels = allChannels.filter {
            it.streamId.toString() in _uiState.value.favoriteChannelIds &&
                    it.name.contains(query, ignoreCase = true)
        }
        Log.d("ChannelsViewModel", "Canales favoritos obtenidos: ${favoriteChannels.size}")
        return when (currentChannelSortOrder) {
            SortOrder.AZ -> favoriteChannels.sortedBy { it.name }
            SortOrder.ZA -> favoriteChannels.sortedByDescending { it.name }
            SortOrder.DEFAULT -> favoriteChannels
        }
    }

    private fun buildStreamUrl(channel: LiveStream): String {
        val baseUrl = if (currentUser.url.endsWith("/")) currentUser.url else "${currentUser.url}/"
        return "${baseUrl}live/${currentUser.username}/${currentUser.password}/${channel.streamId}.ts"
    }

    open fun onFavoritesCategoryToggled() {
        viewModelScope.launch {
            Log.d("ChannelsViewModel", "onFavoritesCategoryToggled()")
            val isCurrentlyExpanded = _uiState.value.isFavoritesCategoryExpanded

            val collapsedCategories = _originalCategories.value.map { it.copy(isExpanded = false) }
            _originalCategories.value = collapsedCategories

            _uiState.update { it.copy(isFavoritesCategoryExpanded = !isCurrentlyExpanded) }

            filterAndSortCategories()
            _scrollToItemEvent.emit("favorites")
        }
    }

    // --- Funciones para el menú de ordenación ---

    fun toggleSortMenu(show: Boolean) {
        _uiState.update { it.copy(showSortMenu = show) }
    }

    fun setCategorySortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            preferenceManager.saveSortOrder("category", sortOrder.name)
            _uiState.update { it.copy(categorySortOrder = sortOrder) }
            filterAndSortCategories()
        }
    }

    fun setChannelSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            preferenceManager.saveSortOrder("channel", sortOrder.name)
            _uiState.update { it.copy(channelSortOrder = sortOrder) }
            filterAndSortCategories()
        }
    }

    // Función auxiliar para formatear la marca de tiempo a una cadena legible
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Nunca"
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// Extensión para convertir String a SortOrder
fun String.toSortOrder(): SortOrder {
    return try {
        SortOrder.valueOf(this)
    } catch (e: IllegalArgumentException) {
        SortOrder.DEFAULT
    }
}

// Extensión para convertir String a AspectRatioMode
fun String.toAspectRatioMode(): AspectRatioMode {
    return try {
        AspectRatioMode.valueOf(this)
    } catch (e: IllegalArgumentException) {
        AspectRatioMode.FIT_SCREEN
    }
}
