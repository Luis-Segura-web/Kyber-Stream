package com.kybers.play.core.player

import android.app.Application
import android.util.Log
import com.kybers.play.core.datastore.SettingsDataStore
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selecciona el motor de reproducción apropiado basado en preferencias del usuario
 * y capacidades del dispositivo, con fallback automático
 */
@Singleton
class PlayerSelector @Inject constructor(
    private val application: Application,
    private val preferenceManager: PreferenceManager,
    private val settingsDataStore: SettingsDataStore
) {
    
    companion object {
        private const val TAG = "PlayerSelector"
    }
    
    private var lastEngineType: EngineType? = null
    private var fallbackAttempted = false

    /**
     * Crea una instancia del motor apropiado
     * @param mediaSpec Especificación del media para determinar el mejor motor
     * @return Instancia del PlayerEngine
     */
    suspend fun createEngine(mediaSpec: MediaSpec): PlayerEngine {
        val preferredEngine = getPreferredEngineType()
        
        Log.d(TAG, "Creating engine - preferred: $preferredEngine, media: ${mediaSpec.url}")
        
        return try {
            when (preferredEngine) {
                EngineType.MEDIA3 -> {
                    val engine = createMedia3Engine()
                    lastEngineType = EngineType.MEDIA3
                    fallbackAttempted = false
                    engine
                }
                EngineType.VLC -> {
                    val engine = createVlcEngine()
                    lastEngineType = EngineType.VLC
                    fallbackAttempted = false
                    engine
                }
                EngineType.AUTO -> {
                    // Intentar Media3 primero, fallback a VLC si falla
                    selectAutomaticEngine(mediaSpec)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create preferred engine $preferredEngine", e)
            
            // Intentar fallback automático si está habilitado
            if (shouldAttemptFallback(preferredEngine)) {
                Log.w(TAG, "Attempting fallback to alternative engine")
                createFallbackEngine(preferredEngine)
            } else {
                throw e
            }
        }
    }

    /**
     * Selección automática de motor basada en el media
     */
    private suspend fun selectAutomaticEngine(mediaSpec: MediaSpec): PlayerEngine {
        // Intentar Media3 primero (motor principal)
        return try {
            val engine = createMedia3Engine()
            lastEngineType = EngineType.MEDIA3
            fallbackAttempted = false
            Log.d(TAG, "Auto-selected Media3 engine")
            engine
        } catch (e: Exception) {
            Log.w(TAG, "Media3 failed, falling back to VLC", e)
            val engine = createVlcEngine()
            lastEngineType = EngineType.VLC
            fallbackAttempted = true
            engine
        }
    }

    /**
     * Crea motor de fallback cuando el preferido falla
     */
    private suspend fun createFallbackEngine(failedEngine: EngineType): PlayerEngine {
        return when (failedEngine) {
            EngineType.MEDIA3 -> {
                Log.d(TAG, "Falling back from Media3 to VLC")
                val engine = createVlcEngine()
                lastEngineType = EngineType.VLC
                fallbackAttempted = true
                engine
            }
            EngineType.VLC -> {
                Log.d(TAG, "Falling back from VLC to Media3")
                val engine = createMedia3Engine()
                lastEngineType = EngineType.MEDIA3
                fallbackAttempted = true
                engine
            }
            EngineType.AUTO -> {
                // Ya se intentó automático, usar VLC como último recurso
                Log.d(TAG, "Auto selection failed, using VLC as last resort")
                val engine = createVlcEngine()
                lastEngineType = EngineType.VLC
                fallbackAttempted = true
                engine
            }
        }
    }

    /**
     * Determina si se debe intentar fallback automático
     */
    private suspend fun shouldAttemptFallback(failedEngine: EngineType): Boolean {
        val settings = settingsDataStore.settings.first()
        return settings.enableAutoFallback && !fallbackAttempted
    }

    /**
     * Obtiene el tipo de motor preferido por el usuario
     * Usa el nuevo SettingsDataStore cuando esté disponible, con fallback al PreferenceManager
     */
    private suspend fun getPreferredEngineType(): EngineType {
        return try {
            val settings = settingsDataStore.settings.first()
            when (settings.playerPref) {
                Settings.PlayerPref.MEDIA3 -> EngineType.MEDIA3
                Settings.PlayerPref.VLC -> EngineType.VLC
                Settings.PlayerPref.AUTO -> EngineType.AUTO
                else -> EngineType.AUTO // Valor por defecto
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read from SettingsDataStore, falling back to PreferenceManager", e)
            // Fallback al sistema anterior
            when (preferenceManager.getPlayerPreference()) {
                "MEDIA3" -> EngineType.MEDIA3
                "VLC" -> EngineType.VLC
                "AUTO" -> EngineType.AUTO
                else -> EngineType.AUTO // Valor por defecto
            }
        }
    }

    /**
     * Crea una instancia del motor Media3
     */
    private suspend fun createMedia3Engine(): PlayerEngine {
        return Media3Engine(application, preferenceManager)
    }

    /**
     * Crea una instancia del motor VLC
     */
    private suspend fun createVlcEngine(): PlayerEngine {
        return VlcEngine(application, preferenceManager)
    }

    /**
     * Obtiene información sobre el último motor utilizado
     */
    fun getLastEngineInfo(): EngineInfo {
        return EngineInfo(
            type = lastEngineType ?: EngineType.AUTO,
            wasFallback = fallbackAttempted
        )
    }

    /**
     * Tipos de motores de reproducción disponibles
     */
    enum class EngineType {
        MEDIA3,     // Reproductor principal (ExoPlayer)
        VLC,        // Reproductor alternativo (LibVLC)
        AUTO        // Selección automática
    }

    /**
     * Información sobre el motor seleccionado
     */
    data class EngineInfo(
        val type: EngineType,
        val wasFallback: Boolean
    )
}