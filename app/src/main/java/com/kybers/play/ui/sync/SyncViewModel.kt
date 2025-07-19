package com.kybers.play.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
class SyncViewModel(
    private val contentRepository: ContentRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * ¡LÓGICA CORREGIDA! Ahora la EPG espera a que los canales terminen de sincronizarse.
     */
    fun startSync(user: User) {
        viewModelScope.launch {
            Log.d("SyncViewModel", "Iniciando sincronización completa para usuario: ${user.profileName}")
            try {
                // Tareas que no tienen dependencias y pueden correr en paralelo.
                val independentJobs = listOf(
                    async {
                        _syncState.update { SyncState.SyncingMovies }
                        contentRepository.cacheMovies(user.username, user.password, user.id)
                    },
                    async {
                        _syncState.update { SyncState.SyncingSeries }
                        contentRepository.cacheSeries(user.username, user.password, user.id)
                    }
                )

                // Tarea principal que tiene una secuencia interna (Canales -> EPG)
                val dependentJob = async {
                    // Paso 1: Sincronizar canales y esperar a que termine. Esto es crucial.
                    _syncState.update { SyncState.SyncingChannels }
                    contentRepository.cacheLiveStreams(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "Sincronización de canales completada. Procediendo a EPG.")

                    // Paso 2: Ahora que los canales están en la DB, sincronizar EPG si es necesario.
                    if (syncManager.isEpgSyncNeeded(user.id)) {
                        Log.d("SyncViewModel", "Se necesita sincronización de EPG. Iniciando descarga.")
                        _syncState.update { SyncState.SyncingEpg }
                        contentRepository.cacheEpgData(user.username, user.password, user.id)
                        syncManager.saveEpgLastSyncTimestamp(user.id)
                        Log.d("SyncViewModel", "Sincronización de EPG completada.")
                    } else {
                        Log.d("SyncViewModel", "Saltando sincronización de EPG, caché aún válido.")
                    }
                }

                // Esperamos a que todas las tareas (independientes y la principal) terminen.
                awaitAll(*independentJobs.toTypedArray(), dependentJob)

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
