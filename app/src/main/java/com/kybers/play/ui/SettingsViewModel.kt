package com.kybers.play.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kybers.play.core.datastore.SettingsDataStore
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.UserInfo
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.di.CurrentUser
import com.kybers.play.di.RepositoryFactory
import com.kybers.play.settings.Settings
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.settings.DynamicSettingsManager
import com.kybers.play.ui.theme.ThemeManager
import com.kybers.play.ui.theme.ThemeConfig
import com.kybers.play.ui.theme.ThemeColor
import com.kybers.play.ui.theme.ThemeMode
import com.kybers.play.work.CacheWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Data class que representa el estado completo de la UI de la pantalla de Ajustes.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val userInfo: UserInfo? = null,
    val lastSyncTimestamp: Long = 0,
    val syncFrequency: Int = 12,
    val playerPreference: String = "AUTO",
    val streamFormat: String = "AUTOMATIC",
    val hwAccelerationEnabled: Boolean = true,
    val networkBuffer: String = "MEDIUM",
    val appTheme: String = "SYSTEM",
    val recentlyWatchedLimit: Int = 10,
    val parentalControlEnabled: Boolean = false,
    val hasParentalPin: Boolean = false,
    val liveCategories: List<Category> = emptyList(),
    val movieCategories: List<Category> = emptyList(),
    val seriesCategories: List<Category> = emptyList(),
    val blockedCategories: Set<String> = emptySet(),
    // New dynamic settings fields
    val adaptiveRecommendations: Map<String, Any> = emptyMap(),
    val showRecommendations: Boolean = false
)

/**
 * Eventos de una sola vez que el ViewModel puede enviar a la UI.
 */
sealed class SettingsEvent {
    object NavigateToLogin : SettingsEvent()
    object ShowSyncForcedMessage : SettingsEvent()
    object ShowSyncCompletedMessage : SettingsEvent()
    data class ShowSyncErrorMessage(val message: String) : SettingsEvent()
    object ShowHistoryClearedMessage : SettingsEvent()
    object ShowPinSetSuccess : SettingsEvent()
    object ShowPinChangeSuccess : SettingsEvent()
    object ShowPinChangeError : SettingsEvent()
    object ShowRecommendationsApplied : SettingsEvent()
    object PlayerSettingsChanged : SettingsEvent()
}

/**
 * ViewModel para la pantalla de Ajustes.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repositoryFactory: RepositoryFactory,
    private val preferenceManager: PreferenceManager,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    @CurrentUser private val currentUser: User,
    private val parentalControlManager: ParentalControlManager,
    private val themeManager: ThemeManager,
    private val dynamicSettingsManager: DynamicSettingsManager
) : ViewModel() {

    private val liveRepository: LiveRepository by lazy {
        repositoryFactory.createLiveRepository(currentUser.url)
    }
    
    private val vodRepository: VodRepository by lazy {
        repositoryFactory.createVodRepository(currentUser.url)
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadInitialSettings()
        loadUserInfoAndCategories()
        loadAdaptiveRecommendations()
    }

    private fun loadInitialSettings() {
        val playerPreference = preferenceManager.getPlayerPreference()
        Log.d("SettingsViewModel", "Loading initial player preference: $playerPreference")
        
        _uiState.update {
            it.copy(
                lastSyncTimestamp = syncManager.getOldestSyncTimestamp(currentUser.id),
                syncFrequency = preferenceManager.getSyncFrequency(),
                playerPreference = playerPreference,
                streamFormat = preferenceManager.getStreamFormat(),
                hwAccelerationEnabled = preferenceManager.getHwAcceleration(),
                networkBuffer = preferenceManager.getNetworkBuffer(),
                appTheme = preferenceManager.getAppTheme(),
                recentlyWatchedLimit = preferenceManager.getRecentlyWatchedLimit(),
                parentalControlEnabled = preferenceManager.isParentalControlEnabled(),
                hasParentalPin = !preferenceManager.getParentalPin().isNullOrBlank(),
                blockedCategories = preferenceManager.getBlockedCategories()
            )
        }
    }

    private fun loadAdaptiveRecommendations() {
        viewModelScope.launch {
            val recommendations = dynamicSettingsManager.getAdaptiveRecommendations()
            _uiState.update { 
                it.copy(
                    adaptiveRecommendations = recommendations,
                    showRecommendations = hasAvailableRecommendations(recommendations)
                ) 
            }
        }
    }
    
    private fun hasAvailableRecommendations(recommendations: Map<String, Any>): Boolean {
        val currentBuffer = _uiState.value.networkBuffer
        val currentHwAccel = _uiState.value.hwAccelerationEnabled
        val currentSync = _uiState.value.syncFrequency
        
        return recommendations["bufferSize"] as String != currentBuffer ||
               recommendations["hardwareAcceleration"] as Boolean != currentHwAccel ||
               recommendations["syncFrequency"] as Int != currentSync
    }

    private fun loadUserInfoAndCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userInfoJob = async { vodRepository.getUserInfo(currentUser.username, currentUser.password) }
            val liveCategoriesJob = async { liveRepository.getLiveCategories(currentUser.username, currentUser.password, currentUser.id) }
            val movieCategoriesJob = async { vodRepository.getMovieCategories(currentUser.username, currentUser.password, currentUser.id) }
            val seriesCategoriesJob = async { vodRepository.getSeriesCategories(currentUser.username, currentUser.password, currentUser.id) }

            val userInfo = userInfoJob.await()
            val liveCategories = liveCategoriesJob.await().sortedBy { it.categoryName }
            val movieCategories = movieCategoriesJob.await().sortedBy { it.categoryName }
            val seriesCategories = seriesCategoriesJob.await().sortedBy { it.categoryName }

            android.util.Log.d("SettingsViewModel", "Categorías cargadas para control parental:")
            android.util.Log.d("SettingsViewModel", "- Live: ${liveCategories.size} categorías")
            android.util.Log.d("SettingsViewModel", "- Movies: ${movieCategories.size} categorías")
            android.util.Log.d("SettingsViewModel", "- Series: ${seriesCategories.size} categorías")

            _uiState.update {
                it.copy(
                    isLoading = false,
                    userInfo = userInfo,
                    liveCategories = liveCategories,
                    movieCategories = movieCategories,
                    seriesCategories = seriesCategories
                )
            }
        }
    }

    // --- MÉTODOS DE GESTIÓN DE AJUSTES DINÁMICOS ---
    
    fun applyRecommendedSettings() {
        viewModelScope.launch {
            val recommendations = _uiState.value.adaptiveRecommendations
            
            // Apply buffer size recommendation
            if (recommendations.containsKey("bufferSize")) {
                val recommendedBuffer = recommendations["bufferSize"] as String
                onNetworkBufferChanged(recommendedBuffer)
            }
            
            // Apply hardware acceleration recommendation
            if (recommendations.containsKey("hardwareAcceleration")) {
                val recommendedHwAccel = recommendations["hardwareAcceleration"] as Boolean
                onHwAccelerationChanged(recommendedHwAccel)
            }
            
            // Apply sync frequency recommendation
            if (recommendations.containsKey("syncFrequency")) {
                val recommendedSync = recommendations["syncFrequency"] as Int
                onSyncFrequencyChanged(recommendedSync)
            }
            
            // Update UI to hide recommendations
            _uiState.update { it.copy(showRecommendations = false) }
            _events.emit(SettingsEvent.ShowRecommendationsApplied)
        }
    }
    
    fun dismissRecommendations() {
        _uiState.update { it.copy(showRecommendations = false) }
    }
    
    fun refreshRecommendations() {
        loadAdaptiveRecommendations()
    }

    // --- MÉTODOS DE GESTIÓN DE AJUSTES ---

    fun onSyncFrequencyChanged(frequencyHours: Int) {
        preferenceManager.saveSyncFrequency(frequencyHours)
        _uiState.update { it.copy(syncFrequency = frequencyHours) }
        
        // Reprogramar worker con nueva frecuencia
        rescheduleCacheWorker(frequencyHours)
    }

    fun onPlayerPreferenceChanged(preference: String) {
        Log.d("SettingsViewModel", "Player preference changed to: $preference")
        
        // Update old preference system for backward compatibility
        preferenceManager.savePlayerPreference(preference)
        Log.d("SettingsViewModel", "Saved to PreferenceManager: $preference")
        
        // Also update new settings datastore - make this blocking to avoid race conditions
        viewModelScope.launch {
            val playerPref = when (preference) {
                "MEDIA3" -> Settings.PlayerPref.MEDIA3
                "VLC" -> Settings.PlayerPref.VLC
                else -> Settings.PlayerPref.AUTO
            }
            Log.d("SettingsViewModel", "Updating SettingsDataStore with enum: $playerPref")
            
            try {
                settingsDataStore.updatePlayerPreferences(
                    playerPref = playerPref,
                    stopOnBackground = preferenceManager.getStopOnBackground(),
                    enableAutoFallback = preferenceManager.getAutoFallbackEnabled()
                )
                
                // Verify the save worked
                val savedSettings = settingsDataStore.settings.first()
                Log.d("SettingsViewModel", "Verified SettingsDataStore value: ${savedSettings.playerPref}")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to update or verify SettingsDataStore", e)
            }
        }
        
        _uiState.update { it.copy(playerPreference = preference) }
        notifyPlayerSettingsChanged()
    }

    private fun rescheduleCacheWorker(frequencyHours: Int) {
        val workManager = WorkManager.getInstance(context)
        
        // Cancelar worker actual
        workManager.cancelUniqueWork("CacheSyncWorker")
        
        if (frequencyHours > 0) {
            // Programar nuevo worker con frecuencia del usuario
            val syncRequest = PeriodicWorkRequestBuilder<CacheWorker>(
                frequencyHours.toLong(), TimeUnit.HOURS
            ).setInitialDelay(10, TimeUnit.MINUTES).build()
            
            workManager.enqueueUniquePeriodicWork(
                "CacheSyncWorker",
                ExistingPeriodicWorkPolicy.REPLACE,
                syncRequest
            )
            Log.d("SettingsViewModel", "Worker reprogramado para ejecutarse cada $frequencyHours horas")
        } else {
            Log.d("SettingsViewModel", "Worker cancelado - sincronización solo manual")
        }
    }

    fun onStreamFormatChanged(format: String) {
        preferenceManager.saveStreamFormat(format)
        _uiState.update { it.copy(streamFormat = format) }
        notifyPlayerSettingsChanged()
    }

    fun onHwAccelerationChanged(enabled: Boolean) {
        preferenceManager.saveHwAcceleration(enabled)
        _uiState.update { it.copy(hwAccelerationEnabled = enabled) }
        notifyPlayerSettingsChanged()
    }

    fun onNetworkBufferChanged(bufferSize: String) {
        preferenceManager.saveNetworkBuffer(bufferSize)
        _uiState.update { it.copy(networkBuffer = bufferSize) }
        notifyPlayerSettingsChanged()
    }

    private fun notifyPlayerSettingsChanged() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.PlayerSettingsChanged)
        }
    }

    fun onAppThemeChanged(theme: String) {
        preferenceManager.saveAppTheme(theme)
        _uiState.update { it.copy(appTheme = theme) }
        // Immediately apply theme change through ThemeManager if available
        // This ensures instant theme switching without app restart
        themeManager?.refreshThemeFromPreferences()
    }
    
    /**
     * Maneja cambios en la configuración del tema usando el nuevo sistema
     */
    fun onThemeConfigChanged(config: ThemeConfig) {
        // Guardar en nuevo formato
        preferenceManager.saveThemeColor(config.color.name)
        preferenceManager.saveThemeMode(config.mode.name)
        
        // Mantener compatibilidad con el sistema anterior
        val legacyThemeString = when {
            config.mode == ThemeMode.SYSTEM -> "SYSTEM"
            config.mode == ThemeMode.LIGHT && config.color == ThemeColor.BLUE -> "LIGHT"
            config.mode == ThemeMode.DARK && config.color == ThemeColor.BLUE -> "DARK"
            else -> config.color.name
        }
        
        preferenceManager.saveAppTheme(legacyThemeString)
        _uiState.update { it.copy(appTheme = legacyThemeString) }
        
        // Aplicar inmediatamente a través del ThemeManager
        themeManager?.setThemeConfig(config)
    }

    fun onRecentlyWatchedLimitChanged(limit: Int) {
        preferenceManager.saveRecentlyWatchedLimit(limit)
        _uiState.update { it.copy(recentlyWatchedLimit = limit) }
    }

    fun onClearHistoryClicked() {
        viewModelScope.launch {
            preferenceManager.clearAllPlaybackHistory()
            _events.emit(SettingsEvent.ShowHistoryClearedMessage)
        }
    }

    // --- MÉTODOS DE CONTROL PARENTAL ---

    fun onParentalControlEnabledChanged(enabled: Boolean) {
        // Si se está desactivando, también borramos el PIN por seguridad.
        if (!enabled) {
            preferenceManager.saveParentalPin("")
        }
        preferenceManager.saveParentalControlEnabled(enabled)
        _uiState.update { it.copy(parentalControlEnabled = enabled, hasParentalPin = !preferenceManager.getParentalPin().isNullOrBlank()) }
    }

    fun setInitialPin(pin: String) {
        viewModelScope.launch {
            preferenceManager.saveParentalPin(pin)
            // Al establecer el PIN, activamos el control parental automáticamente.
            preferenceManager.saveParentalControlEnabled(true)
            _uiState.update { it.copy(hasParentalPin = true, parentalControlEnabled = true) }
            _events.emit(SettingsEvent.ShowPinSetSuccess)
        }
    }

    fun changePin(oldPin: String, newPin: String) {
        viewModelScope.launch {
            if (verifyPin(oldPin)) {
                preferenceManager.saveParentalPin(newPin)
                _events.emit(SettingsEvent.ShowPinChangeSuccess)
            } else {
                _events.emit(SettingsEvent.ShowPinChangeError)
            }
        }
    }

    fun onBlockedCategoriesChanged(blockedIds: Set<String>) {
        parentalControlManager.updateBlockedCategories(blockedIds)
        _uiState.update { it.copy(blockedCategories = blockedIds) }
    }

    fun verifyPin(pin: String): Boolean {
        return preferenceManager.getParentalPin() == pin
    }

    // --- MÉTODOS DE ACCIÓN ---

    fun onForceSyncClicked() {
        viewModelScope.launch {
            try {
                // Actually trigger immediate synchronization instead of just resetting timestamp
                _uiState.update { it.copy(isLoading = true) }
                
                // Force sync all content types immediately
                val moviesJob = async { 
                    vodRepository.cacheMovies(currentUser.username, currentUser.password, currentUser.id)
                    syncManager.saveLastSyncTimestamp(currentUser.id, SyncManager.ContentType.MOVIES)
                }
                val seriesJob = async { 
                    vodRepository.cacheSeries(currentUser.username, currentUser.password, currentUser.id)
                    syncManager.saveLastSyncTimestamp(currentUser.id, SyncManager.ContentType.SERIES)
                }
                val liveJob = async { 
                    liveRepository.cacheLiveStreams(currentUser.username, currentUser.password, currentUser.id)
                    syncManager.saveLastSyncTimestamp(currentUser.id, SyncManager.ContentType.LIVE_TV)
                }
                val epgJob = async {
                    if (syncManager.isEpgSyncNeeded(currentUser.id)) {
                        liveRepository.cacheEpgData(currentUser.username, currentUser.password, currentUser.id)
                        syncManager.saveEpgLastSyncTimestamp(currentUser.id)
                    }
                }
                
                awaitAll(moviesJob, seriesJob, liveJob, epgJob)
                
                // Update general timestamp for backward compatibility
                syncManager.saveLastSyncTimestamp(currentUser.id)
                
                // Update UI with new timestamp
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        lastSyncTimestamp = syncManager.getOldestSyncTimestamp(currentUser.id)
                    ) 
                }
                
                _events.emit(SettingsEvent.ShowSyncCompletedMessage)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(SettingsEvent.ShowSyncErrorMessage(e.message ?: "Error desconocido"))
            }
        }
    }

    fun onChangeProfileClicked() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.NavigateToLogin)
        }
    }

    // --- FUNCIONES DE FORMATO ---

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Nunca"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp))
    }

    fun formatUnixTimestamp(timestamp: String?): String {
        if (timestamp == null) return "No disponible"
        return try {
            val date = Date(timestamp.toLong() * 1000)
            val sdf = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"))
            sdf.format(date)
        } catch (e: Exception) {
            "Fecha inválida"
        }
    }
}
