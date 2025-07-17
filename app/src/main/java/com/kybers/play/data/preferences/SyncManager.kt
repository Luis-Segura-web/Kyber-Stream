package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

/**
 * Manages the logic for determining if a data sync is needed.
 * It uses SharedPreferences to persist the timestamp of the last successful sync.
 *
 * @param context The application context to access SharedPreferences.
 */
class SyncManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        // The sync interval is set to 12 hours.
        private val SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12)
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Checks if a new data sync is required.
     *
     * @return `true` if the last sync was more than 12 hours ago or if it has never been synced.
     * `false` otherwise.
     */
    fun isSyncNeeded(): Boolean {
        val lastSyncTimestamp = sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        // If lastSyncTimestamp is 0, it means it has never been synced.
        if (lastSyncTimestamp == 0L) {
            return true
        }
        // Calculate the time elapsed since the last sync.
        val timeSinceLastSync = System.currentTimeMillis() - lastSyncTimestamp
        return timeSinceLastSync > SYNC_INTERVAL_MILLIS
    }

    /**
     * Saves the current timestamp as the last successful sync time.
     * This should be called after a data sync operation is completed successfully.
     */
    fun saveLastSyncTimestamp() {
        with(sharedPreferences.edit()) {
            putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }
}
