package com.kybers.play.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Representa los diferentes estados del proceso de sincronización de datos,
 * ahora con más detalle para mostrar el progreso paso a paso.
 */
sealed class SyncState {
    object Idle : SyncState()
    object SyncingChannels : SyncState()
    object SyncingMovies : SyncState()
    object SyncingSeries : SyncState()
    object SyncingEpg : SyncState() // <--- ¡NUEVO ESTADO!
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * ViewModel para la SyncScreen. Maneja la lógica para obtener todo el contenido
 * del servidor remoto y almacenarlo en la base de datos local de manera concurrente.
 *
 * @param contentRepository El repositorio para obtener contenido.
 * @param syncManager El gestor para manejar las marcas de tiempo de sincronización.
 */
class SyncViewModel(
    private val contentRepository: ContentRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Inicia el proceso de sincronización de datos para el usuario dado.
     * Actualiza el estado de la UI para reflejar el paso actual del proceso.
     * Las operaciones de caché se ejecutan de forma asíncrona (en paralelo) por tipo de contenido.
     *
     * @param user El perfil de usuario para el cual sincronizar los datos.
     */
    fun startSync(user: User) {
        viewModelScope.launch {
            Log.d("SyncViewModel", "Iniciando sincronización para usuario: ${user.profileName} (ID: ${user.id})")
            try {
                // Lanzamos cada operación de caché en una corrutina separada usando 'async'.
                // Esto permite que se ejecuten en paralelo.

                val channelsJob = async {
                    _syncState.update { SyncState.SyncingChannels }
                    Log.d("SyncViewModel", "Lanzando sincronización de canales para userId: ${user.id}")
                    contentRepository.cacheLiveStreams(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "Canales sincronizados (tarea completada) para userId: ${user.id}")
                }

                val moviesJob = async {
                    _syncState.update { SyncState.SyncingMovies }
                    Log.d("SyncViewModel", "Lanzando sincronización de películas para userId: ${user.id}")
                    contentRepository.cacheMovies(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "Películas sincronizadas (tarea completada) para userId: ${user.id}")
                }

                val seriesJob = async {
                    _syncState.update { SyncState.SyncingSeries }
                    Log.d("SyncViewModel", "Lanzando sincronización de series para userId: ${user.id}")
                    contentRepository.cacheSeries(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "Series sincronizadas (tarea completada) para userId: ${user.id}")
                }

                val epgJob = async { // <--- ¡NUEVA TAREA ASÍNCRONA PARA EPG!
                    _syncState.update { SyncState.SyncingEpg }
                    Log.d("SyncViewModel", "Lanzando sincronización de EPG para userId: ${user.id}")
                    contentRepository.cacheEpgEvents(user.username, user.password, user.id)
                    Log.d("SyncViewModel", "EPG sincronizada (tarea completada) para userId: ${user.id}")
                }

                // 'awaitAll' espera a que todas las tareas asíncronas lanzadas anteriormente terminen.
                awaitAll(channelsJob, moviesJob, seriesJob, epgJob) // <--- ¡AÑADIR epgJob aquí!

                // Después de que todas las operaciones paralelas se completen con éxito, actualizamos la marca de tiempo.
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
