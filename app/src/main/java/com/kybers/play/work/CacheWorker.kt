package com.kybers.play.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kybers.play.MainApplication
import kotlinx.coroutines.flow.firstOrNull

class CacheWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container

        // Obtenemos el primer usuario guardado para obtener las credenciales
        val user = container.userRepository.allUsers.firstOrNull()?.firstOrNull()
            ?: return Result.failure() // Si no hay usuario, no podemos trabajar

        val contentRepository = container.createContentRepository(user.url)

        return try {
            // Llamamos a la nueva función del repositorio para que haga la magia
            contentRepository.cacheAllData(user.username, user.password)
            Result.success()
        } catch (e: Exception) {
            // Si algo sale mal (ej. sin internet), WorkManager lo reintentará más tarde
            Result.retry()
        }
    }
}
