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
 * --- ¡WORKER OPTIMIZADO! ---
 * Worker en segundo plano para sincronizar y cachear el contenido periódicamente.
 * Ahora respeta los diferentes umbrales de tiempo para la sincronización de contenido y de la EPG.
 */
class CacheWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        val syncManager = container.syncManager

        // ADVERTENCIA: Este worker siempre sincroniza el primer usuario de la lista.
        // En una futura mejora, se podría guardar y sincronizar el último perfil activo.
        val user = container.userRepository.allUsers.first().firstOrNull()
            ?: run {
                Log.e("CacheWorker", "No se encontró ningún usuario para la sincronización. Fallando el trabajo.")
                return Result.failure()
            }

        var somethingWasSynced = false

        try {
            // 1. Sincronización de Contenido Principal (Canales, Películas, Series)
            if (syncManager.isSyncNeeded(user.id)) {
                somethingWasSynced = true
                Log.d("CacheWorker", "Iniciando sincronización de contenido para: ${user.profileName}")

                val liveRepository = container.createLiveRepository(user.url)
                val vodRepository = container.createVodRepository(user.url)

                liveRepository.cacheLiveStreams(user.username, user.password, user.id)
                Log.d("CacheWorker", "Canales sincronizados para userId: ${user.id}")

                vodRepository.cacheMovies(user.username, user.password, user.id)
                Log.d("CacheWorker", "Películas sincronizadas para userId: ${user.id}")

                vodRepository.cacheSeries(user.username, user.password, user.id)
                Log.d("CacheWorker", "Series sincronizadas para userId: ${user.id}")

                syncManager.saveLastSyncTimestamp(user.id)
                Log.d("CacheWorker", "Sincronización de contenido completada.")
            }

            // 2. Sincronización de EPG (solo si es necesario)
            if (syncManager.isEpgSyncNeeded(user.id)) {
                somethingWasSynced = true
                Log.d("CacheWorker", "Iniciando sincronización de EPG para: ${user.profileName}")
                val liveRepository = container.createLiveRepository(user.url)
                liveRepository.cacheEpgData(user.username, user.password, user.id)
                syncManager.saveEpgLastSyncTimestamp(user.id)
                Log.d("CacheWorker", "Sincronización de EPG completada.")
            }

            if (!somethingWasSynced) {
                Log.d("CacheWorker", "No se necesita ninguna sincronización en este momento para userId: ${user.id}.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("CacheWorker", "Error durante la sincronización en segundo plano para userId ${user.id}: ${e.message}", e)
            return Result.retry()
        }
    }
}
