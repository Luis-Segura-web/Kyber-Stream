package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

/**
 * Manages the synchronization timestamps to determine if a data refresh is needed.
 */
class SyncManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sync_manager_prefs"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_timestamp_"
        private const val KEY_EPG_LAST_SYNC_PREFIX = "epg_last_sync_timestamp_"
        private val SYNC_THRESHOLD = TimeUnit.HOURS.toMillis(4) // 4 horas
        private val EPG_SYNC_THRESHOLD = TimeUnit.HOURS.toMillis(24) // 24 horas
    }

    /**
     * Comprueba si se necesita una sincronización de contenido general (canales, películas, series).
     */
    fun isSyncNeeded(userId: Int): Boolean {
        val lastSync = sharedPreferences.getLong(KEY_LAST_SYNC_PREFIX + userId, 0L)
        return System.currentTimeMillis() - lastSync > SYNC_THRESHOLD
    }

    /**
     * Guarda el timestamp de la última sincronización de contenido general.
     */
    fun saveLastSyncTimestamp(userId: Int) {
        with(sharedPreferences.edit()) {
            putLong(KEY_LAST_SYNC_PREFIX + userId, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * --- ¡NUEVA FUNCIÓN! ---
     * Obtiene el timestamp de la última sincronización de contenido general.
     */
    fun getLastSyncTimestamp(userId: Int): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_PREFIX + userId, 0L)
    }


    /**
     * Comprueba si se necesita una sincronización de la guía de programación (EPG).
     */
    fun isEpgSyncNeeded(userId: Int): Boolean {
        val lastSync = sharedPreferences.getLong(KEY_EPG_LAST_SYNC_PREFIX + userId, 0L)
        return System.currentTimeMillis() - lastSync > EPG_SYNC_THRESHOLD
    }

    /**
     * Guarda el timestamp de la última sincronización de EPG.
     */
    fun saveEpgLastSyncTimestamp(userId: Int) {
        with(sharedPreferences.edit()) {
            putLong(KEY_EPG_LAST_SYNC_PREFIX + userId, System.currentTimeMillis())
            apply()
        }
    }
}