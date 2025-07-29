package com.kybers.play.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.UserInfo
import com.kybers.play.data.repository.BaseContentRepository
import com.kybers.play.ui.theme.ThemeManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Data class que representa el estado completo de la UI de la pantalla de Ajustes.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val userInfo: UserInfo? = null,
    val lastSyncTimestamp: Long = 0,
    val syncFrequency: Int = 12,
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
    object ShowHistoryClearedMessage : SettingsEvent()
    object ShowPinSetSuccess : SettingsEvent()
    object ShowPinChangeSuccess : SettingsEvent()
    object ShowPinChangeError : SettingsEvent()
    object ShowRecommendationsApplied : SettingsEvent()
}

/**
 * ViewModel para la pantalla de Ajustes.
 */
class SettingsViewModel(
    private val context: Context,
    private val contentRepository: BaseContentRepository,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager,
    private val currentUser: User,
    private val themeManager: ThemeManager? = null
) : ViewModel() {

    private val dynamicSettingsManager = DynamicSettingsManager(context, preferenceManager)

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
        _uiState.update {
            it.copy(
                lastSyncTimestamp = syncManager.getLastSyncTimestamp(currentUser.id),
                syncFrequency = preferenceManager.getSyncFrequency(),
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
            val userInfoJob = async { contentRepository.getUserInfo(currentUser.username, currentUser.password) }
            val liveCategoriesJob = async { contentRepository.getLiveCategories(currentUser.username, currentUser.password, currentUser.id) }
            val movieCategoriesJob = async { contentRepository.getMovieCategories(currentUser.username, currentUser.password, currentUser.id) }
            val seriesCategoriesJob = async { contentRepository.getSeriesCategories(currentUser.username, currentUser.password, currentUser.id) }

            val userInfo = userInfoJob.await()
            val liveCategories = liveCategoriesJob.await().sortedBy { it.categoryName }
            val movieCategories = movieCategoriesJob.await().sortedBy { it.categoryName }
            val seriesCategories = seriesCategoriesJob.await().sortedBy { it.categoryName }

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
    }

    fun onStreamFormatChanged(format: String) {
        preferenceManager.saveStreamFormat(format)
        _uiState.update { it.copy(streamFormat = format) }
    }

    fun onHwAccelerationChanged(enabled: Boolean) {
        preferenceManager.saveHwAcceleration(enabled)
        _uiState.update { it.copy(hwAccelerationEnabled = enabled) }
    }

    fun onNetworkBufferChanged(bufferSize: String) {
        preferenceManager.saveNetworkBuffer(bufferSize)
        _uiState.update { it.copy(networkBuffer = bufferSize) }
    }

    fun onAppThemeChanged(theme: String) {
        preferenceManager.saveAppTheme(theme)
        _uiState.update { it.copy(appTheme = theme) }
        // Immediately apply theme change through ThemeManager if available
        // This ensures instant theme switching without app restart
        themeManager?.updateThemeFromString(theme)
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
        preferenceManager.saveBlockedCategories(blockedIds)
        _uiState.update { it.copy(blockedCategories = blockedIds) }
    }

    fun verifyPin(pin: String): Boolean {
        return preferenceManager.getParentalPin() == pin
    }

    // --- MÉTODOS DE ACCIÓN ---

    fun onForceSyncClicked() {
        viewModelScope.launch {
            syncManager.saveLastSyncTimestamp(currentUser.id)
            _events.emit(SettingsEvent.ShowSyncForcedMessage)
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
