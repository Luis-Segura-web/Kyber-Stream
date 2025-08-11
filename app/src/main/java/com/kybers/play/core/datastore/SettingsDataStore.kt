package com.kybers.play.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.kybers.play.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializer para el protocolo buffer de configuraciones
 */
object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Settings {
        try {
            return Settings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw androidx.datastore.core.CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
}

/**
 * Extension para crear el DataStore
 */
private val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer
)

/**
 * Gestor de configuraciones usando DataStore con Protocol Buffers
 * Reemplaza gradualmente las SharedPreferences por un almacenamiento más moderno y type-safe
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) {
    
    private val dataStore = context.settingsDataStore
    
    /**
     * Flow de configuraciones que se actualiza automáticamente
     */
    val settings: Flow<Settings> = dataStore.data
        .catch { exception ->
            // Si hay un error, emitir configuraciones por defecto
            if (exception is IOException) {
                emit(Settings.getDefaultInstance())
            } else {
                throw exception
            }
        }

    /**
     * Actualiza las preferencias del reproductor
     */
    suspend fun updatePlayerPreferences(
        playerPref: Settings.PlayerPref = Settings.PlayerPref.AUTO,
        stopOnBackground: Boolean = true,
        cooldownMs: Int = 2000,
        enableAutoFallback: Boolean = true
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setPlayerPref(playerPref)
                .setStopOnBackground(stopOnBackground)
                .setCooldownMs(cooldownMs)
                .setEnableAutoFallback(enableAutoFallback)
                .build()
        }
    }

    /**
     * Actualiza las configuraciones de tema
     */
    suspend fun updateThemeSettings(
        themeMode: Settings.ThemeMode = Settings.ThemeMode.SYSTEM,
        themeColor: String = ""
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setThemeMode(themeMode)
                .setThemeColor(themeColor)
                .build()
        }
    }

    /**
     * Actualiza las configuraciones de reproductor técnico
     */
    suspend fun updatePlayerTechnicalSettings(
        hwAcceleration: Boolean = true,
        networkBuffer: Settings.NetworkBuffer = Settings.NetworkBuffer.MEDIUM,
        streamFormat: String = "AUTOMATIC"
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setHwAcceleration(hwAcceleration)
                .setNetworkBuffer(networkBuffer)
                .setStreamFormat(streamFormat)
                .build()
        }
    }

    /**
     * Actualiza configuraciones de sincronización
     */
    suspend fun updateSyncSettings(
        syncFrequencyHours: Int = 12,
        recentlyWatchedLimit: Int = 10
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setSyncFrequencyHours(syncFrequencyHours)
                .setRecentlyWatchedLimit(recentlyWatchedLimit)
                .build()
        }
    }

    /**
     * Actualiza configuraciones de control parental
     */
    suspend fun updateParentalControl(
        enabled: Boolean = false,
        pin: String = "",
        blockedCategories: List<String> = emptyList()
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setParentalControlEnabled(enabled)
                .setParentalControlPin(pin)
                .clearBlockedCategories()
                .addAllBlockedCategories(blockedCategories)
                .build()
        }
    }

    /**
     * Actualiza categorías ocultas
     */
    suspend fun updateHiddenCategories(
        liveCategories: List<String> = emptyList(),
        movieCategories: List<String> = emptyList(),
        seriesCategories: List<String> = emptyList()
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .clearHiddenLiveCategories()
                .addAllHiddenLiveCategories(liveCategories)
                .clearHiddenMovieCategories()
                .addAllHiddenMovieCategories(movieCategories)
                .clearHiddenSeriesCategories()
                .addAllHiddenSeriesCategories(seriesCategories)
                .build()
        }
    }

    /**
     * Actualiza modos de visualización
     */
    suspend fun updateDisplayModes(
        channelsMode: String = "GRID",
        moviesMode: String = "GRID",
        seriesMode: String = "GRID"
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setDisplayModeChannels(channelsMode)
                .setDisplayModeMovies(moviesMode)
                .setDisplayModeSeries(seriesMode)
                .build()
        }
    }

    /**
     * Actualiza el usuario actual
     */
    suspend fun updateCurrentUser(userId: Int) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setCurrentUserId(userId)
                .build()
        }
    }

    /**
     * Actualiza favoritos
     */
    suspend fun updateFavorites(
        movieIds: List<String> = emptyList(),
        seriesIds: List<String> = emptyList()
    ) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .clearFavoriteMovieIds()
                .addAllFavoriteMovieIds(movieIds)
                .clearFavoriteSeriesIds()
                .addAllFavoriteSeriesIds(seriesIds)
                .build()
        }
    }

    /**
     * Restablece todas las configuraciones a valores por defecto
     */
    suspend fun resetToDefaults() {
        dataStore.updateData { 
            Settings.getDefaultInstance()
        }
    }

    /**
     * Flows específicos para observar configuraciones individuales
     */
    val playerPreferences: Flow<PlayerPreferences> = settings.map { settings ->
        PlayerPreferences(
            playerPref = settings.playerPref,
            stopOnBackground = settings.stopOnBackground,
            cooldownMs = settings.cooldownMs,
            enableAutoFallback = settings.enableAutoFallback
        )
    }

    val themePreferences: Flow<ThemePreferences> = settings.map { settings ->
        ThemePreferences(
            themeMode = settings.themeMode,
            themeColor = settings.themeColor
        )
    }
}

/**
 * Data classes para flows específicos
 */
data class PlayerPreferences(
    val playerPref: Settings.PlayerPref,
    val stopOnBackground: Boolean,
    val cooldownMs: Int,
    val enableAutoFallback: Boolean
)

data class ThemePreferences(
    val themeMode: Settings.ThemeMode,
    val themeColor: String
)