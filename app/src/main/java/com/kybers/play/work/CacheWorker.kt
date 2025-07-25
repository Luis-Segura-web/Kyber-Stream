package com.kybers.play.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kybers.play.MainApplication
import com.kybers.play.data.preferences.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Worker en segundo plano para sincronizar y cachear el contenido periódicamente.
 * Utiliza los nuevos repositorios modulares para realizar las tareas de red.
 */
class CacheWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        val syncManager = container.syncManager

        // Obtiene el primer usuario para realizar la sincronización.
        // En una app real, se podría elegir al último usuario activo.
        val user = container.userRepository.allUsers.first().firstOrNull()
            ?: run {
                Log.e("CacheWorker", "No se encontró ningún usuario para la sincronización. Fallando el trabajo.")
                return Result.failure()
            }

        // El worker solo se ejecuta si la sincronización es necesaria para este usuario.
        if (!syncManager.isSyncNeeded(user.id)) {
            Log.d("CacheWorker", "Sincronización no necesaria para userId: ${user.id}. Saltando el trabajo.")
            return Result.success()
        }

        // --- ¡CAMBIO CLAVE! ---
        // Se crean instancias de los repositorios específicos usando el AppContainer.
        val liveRepository = container.createLiveRepository(user.url)
        val vodRepository = container.createVodRepository(user.url)

        Log.d("CacheWorker", "Iniciando sincronización en segundo plano para: ${user.profileName} (ID: ${user.id})")

        return try {
            // Llama a los métodos de cacheo de cada repositorio especializado.
            liveRepository.cacheLiveStreams(user.username, user.password, user.id)
            Log.d("CacheWorker", "Canales sincronizados en segundo plano para userId: ${user.id}")

            vodRepository.cacheMovies(user.username, user.password, user.id)
            Log.d("CacheWorker", "Películas sincronizadas en segundo plano para userId: ${user.id}")

            vodRepository.cacheSeries(user.username, user.password, user.id)
            Log.d("CacheWorker", "Series sincronizadas en segundo plano para userId: ${user.id}")

            // Actualiza la marca de tiempo después de una sincronización exitosa.
            syncManager.saveLastSyncTimestamp(user.id)
            Log.d("CacheWorker", "Sincronización en segundo plano completada para userId: ${user.id}")

            Result.success()
        } catch (e: Exception) {
            // Si algo falla (ej. sin red), WorkManager reintentará la tarea más tarde.
            Log.e("CacheWorker", "Error durante la sincronización en segundo plano para userId ${user.id}: ${e.message}", e)
            Result.retry()
        }
    }
}
