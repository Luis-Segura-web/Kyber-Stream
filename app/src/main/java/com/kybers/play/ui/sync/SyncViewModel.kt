package com.kybers.play.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Representa los diferentes estados del proceso de sincronización de datos.
 */
sealed class SyncState {
    object Idle : SyncState()
    object SyncingChannels : SyncState()
    object SyncingMovies : SyncState()
    object SyncingSeries : SyncState()
    object SyncingEpg : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * --- ¡VIEWMODEL ACTUALIZADO! ---
 * ViewModel para la SyncScreen. Ahora utiliza los repositorios modulares.
 *
 * @property liveRepository Repositorio para contenido en vivo (canales y EPG).
 * @property vodRepository Repositorio para contenido VOD (películas y series).
 * @property syncManager Gestiona los timestamps de sincronización.
 * @property preferenceManager Gestiona las preferencias del usuario.
 */
class SyncViewModel(
    private val liveRepository: LiveRepository,
    private val vodRepository: VodRepository,
    private val syncManager: SyncManager,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun startSync(user: User) {
        viewModelScope.launch {
            Log.d("SyncViewModel", "Iniciando sincronización completa para: ${user.profileName}")
            try {
                // Tarea para sincronizar películas y series desde VodRepository
                val vodSyncJob = async {
                    _syncState.update { SyncState.SyncingMovies }
                    val downloadedCount = vodRepository.cacheMovies(user.username, user.password, user.id)
                    val totalInCache = vodRepository.getAllMovies(user.id).first().size
                    preferenceManager.saveMovieSyncStats(downloadedCount, totalInCache)
                    Log.d("SyncViewModel", "Películas sincronizadas. Descargadas: $downloadedCount, Total: $totalInCache")

                    _syncState.update { SyncState.SyncingSeries }
                    vodRepository.cacheSeries(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "Series sincronizadas.")
                }

                // Tarea para sincronizar canales y EPG desde LiveRepository
                val liveSyncJob = async {
                    _syncState.update { SyncState.SyncingChannels }
                    liveRepository.cacheLiveStreams(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "Canales sincronizados.")

                    if (syncManager.isEpgSyncNeeded(user.id)) {
                        Log.d("SyncViewModel", "Se necesita sincronización de EPG.")
                        _syncState.update { SyncState.SyncingEpg }
                        liveRepository.cacheEpgData(user.username, user.password, user.id)
                        syncManager.saveEpgLastSyncTimestamp(user.id)
                        Log.d("SyncViewModel", "EPG sincronizada.")
                    }
                }

                // Esperamos a que ambas tareas (VOD y Live) terminen.
                awaitAll(vodSyncJob, liveSyncJob)

                syncManager.saveLastSyncTimestamp(user.id)
                _syncState.update { SyncState.Success }
                Log.d("SyncViewModel", "Sincronización completa y exitosa para userId: ${user.id}")

            } catch (e: Exception) {
                _syncState.update { SyncState.Error("Fallo al sincronizar datos: ${e.message}") }
                Log.e("SyncViewModel", "Error durante la sincronización para userId ${user.id}: ${e.message}", e)
            }
        }
    }
}
