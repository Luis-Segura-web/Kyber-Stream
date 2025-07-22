package com.kybers.play.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.ContentRepository
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
 * ViewModel para la SyncScreen. Maneja la lógica para obtener todo el contenido,
 * incluyendo la EPG, del servidor remoto.
 * ¡MODIFICADO PARA LA NUEVA LÓGICA DE CARGA!
 */
class SyncViewModel(
    private val contentRepository: ContentRepository,
    private val syncManager: SyncManager,
    // --- ¡NUEVA DEPENDENCIA! ---
    // Necesitamos el PreferenceManager para guardar nuestras estadísticas.
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun startSync(user: User) {
        viewModelScope.launch {
            Log.d("SyncViewModel", "Iniciando sincronización completa para usuario: ${user.profileName}")
            try {
                // --- ¡NUEVA LÓGICA DE SINCRONIZACIÓN! ---

                // Tarea para sincronizar películas
                val moviesSyncJob = async {
                    _syncState.update { SyncState.SyncingMovies }
                    // 1. Llamamos a la nueva función que devuelve el conteo
                    val downloadedCount = contentRepository.cacheMovies(user.username, user.password, user.id)
                    // 2. Obtenemos el total de películas en la base de datos
                    val totalInCache = contentRepository.getAllMovies(user.id).first().size
                    // 3. Guardamos ambas estadísticas
                    preferenceManager.saveMovieSyncStats(downloadedCount, totalInCache)
                    Log.d("SyncViewModel", "Sincronización de películas completada. Descargadas: $downloadedCount, Total en caché: $totalInCache")
                }

                // Tareas para Canales/EPG y Series (pueden correr en paralelo con las películas)
                val otherJobs = listOf(
                    async {
                        _syncState.update { SyncState.SyncingSeries }
                        contentRepository.cacheSeries(user.username, user.password, user.id)
                        Log.d("SyncViewModel", "Sincronización de series completada.")
                    },
                    async {
                        _syncState.update { SyncState.SyncingChannels }
                        contentRepository.cacheLiveStreams(user.username, user.password, user.id)
                        Log.d("SyncViewModel", "Sincronización de canales completada.")
                        if (syncManager.isEpgSyncNeeded(user.id)) {
                            Log.d("SyncViewModel", "Se necesita sincronización de EPG.")
                            _syncState.update { SyncState.SyncingEpg }
                            contentRepository.cacheEpgData(user.username, user.password, user.id)
                            syncManager.saveEpgLastSyncTimestamp(user.id)
                            Log.d("SyncViewModel", "Sincronización de EPG completada.")
                        }
                    }
                )

                // Esperamos a que todas las tareas terminen
                awaitAll(moviesSyncJob, *otherJobs.toTypedArray())

                // Marcamos la sincronización de contenido general como completada
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