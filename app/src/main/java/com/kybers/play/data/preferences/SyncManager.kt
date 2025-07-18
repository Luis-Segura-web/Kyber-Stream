package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log // <-- ¡IMPORTACIÓN NECESARIA!
import java.util.concurrent.TimeUnit

/**
 * Manages the logic for determining if a data sync is needed.
 * It uses SharedPreferences to persist the timestamp of the last successful sync.
 * ¡MODIFICADO! Ahora gestiona la marca de tiempo por usuario.
 *
 * @param context The application context to access SharedPreferences.
 */
class SyncManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_LAST_SYNC_TIMESTAMP_PREFIX = "last_sync_timestamp_" // ¡CAMBIO! Prefijo para almacenar por usuario
        // The sync interval is set to 12 hours.
        private val SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12)
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Checks if a new data sync is required for a specific user.
     *
     * @param userId The ID of the user to check.
     * @return `true` if the last sync for this user was more than 12 hours ago or if it has never been synced.
     * `false` otherwise.
     */
    fun isSyncNeeded(userId: Int): Boolean { // ¡ESTA ES LA FUNCIÓN QUE DEBE ACEPTAR userId!
        val lastSyncTimestamp = sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP_PREFIX + userId, 0L)
        Log.d("SyncManager", "isSyncNeeded para userId $userId: última sincronización = ${lastSyncTimestamp}")

        if (lastSyncTimestamp == 0L) {
            Log.d("SyncManager", "isSyncNeeded para userId $userId: Nunca se ha sincronizado. Necesita sincronización.")
            return true // Never synced for this user
        }
        val timeSinceLastSync = System.currentTimeMillis() - lastSyncTimestamp
        val needed = timeSinceLastSync > SYNC_INTERVAL_MILLIS
        Log.d("SyncManager", "isSyncNeeded para userId $userId: Tiempo desde última sync = ${timeSinceLastSync / 1000 / 60} minutos. Necesita sincronización: $needed")
        return needed
    }

    /**
     * Saves the current timestamp as the last successful sync time for a specific user.
     * This should be called after a data sync operation is completed successfully.
     *
     * @param userId The ID of the user for whom to save the timestamp.
     */
    fun saveLastSyncTimestamp(userId: Int) { // También debe aceptar userId
        val currentTimestamp = System.currentTimeMillis()
        with(sharedPreferences.edit()) {
            putLong(KEY_LAST_SYNC_TIMESTAMP_PREFIX + userId, currentTimestamp)
            apply()
        }
        Log.d("SyncManager", "saveLastSyncTimestamp para userId $userId: Guardado timestamp = $currentTimestamp")
    }

    /**
     * Retrieves the timestamp of the last successful sync for a specific user.
     *
     * @param userId The ID of the user for whom to retrieve the timestamp.
     * @return The timestamp in milliseconds, or 0L if no sync has occurred yet for this user.
     */
    fun getLastSyncTimestamp(userId: Int): Long { // También debe aceptar userId
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP_PREFIX + userId, 0L)
    }
}