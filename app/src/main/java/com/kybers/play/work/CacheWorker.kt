package com.kybers.play.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kybers.play.MainApplication
import com.kybers.play.data.preferences.SyncManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first // Importar first para usarlo en el Flow
import android.util.Log // Importar Log para mensajes de depuración

class CacheWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        val syncManager = container.syncManager

        // Obtener el primer usuario guardado para obtener sus credenciales.
        // En una aplicación multiusuario real, podrías querer sincronizar para todos los usuarios
        // o para el último usuario activo. Por simplicidad, tomamos el primero.
        val user = container.userRepository.allUsers.first().firstOrNull()
            ?: run {
                Log.e("CacheWorker", "No se encontró ningún usuario para la sincronización en segundo plano. Fallando el trabajo.")
                return Result.failure() // Si no hay usuario, no podemos trabajar.
            }

        // El worker de fondo solo debe ejecutarse si la sincronización es realmente necesaria para este usuario específico.
        // ¡CORRECCIÓN! Pasar el userId a isSyncNeeded
        if (!syncManager.isSyncNeeded(user.id)) {
            Log.d("CacheWorker", "Sincronización no necesaria para userId: ${user.id}. Saltando el trabajo.")
            return Result.success()
        }

        val contentRepository = container.createContentRepository(user.url)
        Log.d("CacheWorker", "Iniciando sincronización en segundo plano para usuario: ${user.profileName} (ID: ${user.id})")

        return try {
            // ¡CORRECCIÓN! Pasar el userId a las funciones de caché del repositorio
            contentRepository.cacheLiveStreams(user.username, user.password, user.id)
            Log.d("CacheWorker", "Canales sincronizados en segundo plano para userId: ${user.id}")
            contentRepository.cacheMovies(user.username, user.password, user.id)
            Log.d("CacheWorker", "Películas sincronizadas en segundo plano para userId: ${user.id}")
            contentRepository.cacheSeries(user.username, user.password, user.id)
            Log.d("CacheWorker", "Series sincronizadas en segundo plano para userId: ${user.id}")

            // Después de una sincronización exitosa en segundo plano, actualizar la marca de tiempo para este usuario.
            syncManager.saveLastSyncTimestamp(user.id) // ¡CORRECCIÓN! Pasar el userId
            Log.d("CacheWorker", "Sincronización en segundo plano completada y timestamp guardado para userId: ${user.id}")

            Result.success()
        } catch (e: Exception) {
            // Si algo sale mal (ej. no hay internet), WorkManager reintentará más tarde.
            Log.e("CacheWorker", "Error durante la sincronización en segundo plano para userId ${user.id}: ${e.message}", e)
            Result.retry()
        }
    }
}
