package com.kybers.play.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kybers.play.di.AppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import dagger.hilt.android.EntryPointAccessors

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
        // Get dependencies from Hilt using EntryPoint
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AppDependencies::class.java
        )
        
        val syncManager = hiltEntryPoint.syncManager()
        val userRepository = hiltEntryPoint.userRepository()
        val repositoryFactory = hiltEntryPoint.repositoryFactory()
        // ADVERTENCIA: Este worker siempre sincroniza el primer usuario de la lista.
        // En una futura mejora, se podría guardar y sincronizar el último perfil activo.
        val user = userRepository.allUsers.first().firstOrNull()
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

                val liveRepository = repositoryFactory.createLiveRepository(user.url)
                val vodRepository = repositoryFactory.createVodRepository(user.url)

                liveRepository.cacheLiveStreams(user.username, user.password, user.id)
                Log.d("CacheWorker", "Canales sincronizados para userId: ${user.id}")

                vodRepository.cacheMovies(user.username, user.password, user.id)
                Log.d("CacheWorker", "Películas sincronizadas para userId: ${user.id}")

                vodRepository.cacheSeries(user.username, user.password, user.id)
                Log.d("CacheWorker", "Series sincronizadas para userId: ${user.id}")

                syncManager.saveLastSyncTimestamp(user.id)
                Log.d("CacheWorker", "Sincronización de contenido completada.")
            }

            // --- ¡LÓGICA EPG MEJORADA CON VERIFICACIONES INTELIGENTES! ---
            // 2. Sincronización de EPG (con verificaciones adicionales)
            val liveRepository = repositoryFactory.createLiveRepository(user.url)
            
            // Verificar si necesita sincronización por tiempo o por datos obsoletos
            val needsEpgSyncByTime = syncManager.isEpgSyncNeeded(user.id)
            val needsEpgSyncByData = liveRepository.isEpgDataStale(user.id)
            
            if (needsEpgSyncByTime || needsEpgSyncByData) {
                somethingWasSynced = true
                Log.d("CacheWorker", "Iniciando sincronización de EPG para: ${user.profileName} (tiempo: $needsEpgSyncByTime, datos: $needsEpgSyncByData)")
                
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
