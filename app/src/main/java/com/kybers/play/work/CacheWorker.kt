package com.kybers.play.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kybers.play.MainApplication
import com.kybers.play.data.preferences.SyncManager
import kotlinx.coroutines.flow.firstOrNull

class CacheWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        val syncManager = container.syncManager

        // The background worker should only run if a sync is actually needed.
        // This prevents unnecessary background work.
        if (!syncManager.isSyncNeeded()) {
            return Result.success()
        }

        // Get the first saved user to obtain credentials.
        val user = container.userRepository.allUsers.firstOrNull()?.firstOrNull()
            ?: return Result.failure() // If there's no user, we can't work.

        val contentRepository = container.createContentRepository(user.url)

        return try {
            // ¡CORRECCIÓN! Call the new granular functions in sequence.
            contentRepository.cacheLiveStreams(user.username, user.password)
            contentRepository.cacheMovies(user.username, user.password)
            contentRepository.cacheSeries(user.username, user.password)

            // After a successful background sync, update the timestamp.
            syncManager.saveLastSyncTimestamp()

            Result.success()
        } catch (e: Exception) {
            // If something goes wrong (e.g., no internet), WorkManager will retry later.
            Result.retry()
        }
    }
}
