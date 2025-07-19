package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * ¡MODIFICADO! Vuelve a gestionar una única marca de tiempo para la EPG por usuario.
 */
class SyncManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        // Clave para la sincronización de contenido (canales, películas, series)
        private const val KEY_CONTENT_LAST_SYNC_TIMESTAMP_PREFIX = "content_last_sync_timestamp_"
        private val CONTENT_SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12)

        // ¡NUEVO! Clave y duración para el caché global de EPG.
        private const val KEY_EPG_LAST_SYNC_TIMESTAMP_PREFIX = "epg_last_sync_timestamp_"
        private val EPG_SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12)
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Sincronización de Contenido (Canales/Películas/Series) ---

    fun isSyncNeeded(userId: Int): Boolean {
        val lastSyncTimestamp = sharedPreferences.getLong(KEY_CONTENT_LAST_SYNC_TIMESTAMP_PREFIX + userId, 0L)
        if (lastSyncTimestamp == 0L) return true
        return (System.currentTimeMillis() - lastSyncTimestamp) > CONTENT_SYNC_INTERVAL_MILLIS
    }

    fun saveLastSyncTimestamp(userId: Int) {
        val currentTimestamp = System.currentTimeMillis()
        with(sharedPreferences.edit()) {
            putLong(KEY_CONTENT_LAST_SYNC_TIMESTAMP_PREFIX + userId, currentTimestamp)
            apply()
        }
        Log.d("SyncManager", "saveLastSyncTimestamp para userId $userId guardado.")
    }

    // --- Sincronización de EPG ---

    /**
     * ¡NUEVO! Comprueba si se necesita una nueva sincronización de EPG para un usuario.
     * @param userId El ID del usuario.
     * @return `true` si la última sincronización de EPG fue hace más de 12 horas.
     */
    fun isEpgSyncNeeded(userId: Int): Boolean {
        val key = KEY_EPG_LAST_SYNC_TIMESTAMP_PREFIX + userId
        val lastSyncTimestamp = sharedPreferences.getLong(key, 0L)
        if (lastSyncTimestamp == 0L) {
            Log.d("SyncManager", "isEpgSyncNeeded para userId $userId: Nunca se ha sincronizado. Se necesita sync.")
            return true
        }
        val needed = (System.currentTimeMillis() - lastSyncTimestamp) > EPG_SYNC_INTERVAL_MILLIS
        Log.d("SyncManager", "isEpgSyncNeeded para userId $userId: Necesita sync: $needed")
        return needed
    }

    /**
     * ¡NUEVO! Guarda la marca de tiempo actual como la última sincronización de EPG exitosa para un usuario.
     * @param userId El ID del usuario.
     */
    fun saveEpgLastSyncTimestamp(userId: Int) {
        val key = KEY_EPG_LAST_SYNC_TIMESTAMP_PREFIX + userId
        with(sharedPreferences.edit()) {
            putLong(key, System.currentTimeMillis())
            apply()
        }
        Log.d("SyncManager", "saveEpgLastSyncTimestamp para userId $userId guardado.")
    }
}
