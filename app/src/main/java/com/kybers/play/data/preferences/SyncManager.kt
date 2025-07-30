package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

/**
 * Manages the synchronization timestamps to determine if a data refresh is needed.
 * Now supports separate timestamps for different content types.
 */
class SyncManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sync_manager_prefs"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_timestamp_"
        private const val KEY_MOVIES_LAST_SYNC_PREFIX = "movies_last_sync_timestamp_"
        private const val KEY_SERIES_LAST_SYNC_PREFIX = "series_last_sync_timestamp_"
        private const val KEY_LIVE_LAST_SYNC_PREFIX = "live_last_sync_timestamp_"
        private const val KEY_EPG_LAST_SYNC_PREFIX = "epg_last_sync_timestamp_"
        private val SYNC_THRESHOLD = TimeUnit.HOURS.toMillis(4) // 4 horas
        private val EPG_SYNC_THRESHOLD = TimeUnit.HOURS.toMillis(24) // 24 horas
    }

    /**
     * Content types for separate synchronization tracking
     */
    enum class ContentType {
        MOVIES, SERIES, LIVE_TV
    }

    /**
     * Comprueba si se necesita una sincronización de contenido general (basado en el timestamp más antiguo).
     */
    fun isSyncNeeded(userId: Int): Boolean {
        val oldestTimestamp = getOldestSyncTimestamp(userId)
        return System.currentTimeMillis() - oldestTimestamp > SYNC_THRESHOLD
    }

    /**
     * Comprueba si se necesita sincronización para un tipo de contenido específico.
     */
    fun isSyncNeeded(userId: Int, contentType: ContentType): Boolean {
        val lastSync = getLastSyncTimestamp(userId, contentType)
        return System.currentTimeMillis() - lastSync > SYNC_THRESHOLD
    }

    /**
     * Guarda el timestamp de la última sincronización de contenido general (para compatibilidad).
     */
    fun saveLastSyncTimestamp(userId: Int) {
        with(sharedPreferences.edit()) {
            putLong(KEY_LAST_SYNC_PREFIX + userId, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Guarda el timestamp de la última sincronización para un tipo de contenido específico.
     */
    fun saveLastSyncTimestamp(userId: Int, contentType: ContentType) {
        val key = when (contentType) {
            ContentType.MOVIES -> KEY_MOVIES_LAST_SYNC_PREFIX + userId
            ContentType.SERIES -> KEY_SERIES_LAST_SYNC_PREFIX + userId
            ContentType.LIVE_TV -> KEY_LIVE_LAST_SYNC_PREFIX + userId
        }
        with(sharedPreferences.edit()) {
            putLong(key, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Obtiene el timestamp de la última sincronización de contenido general.
     */
    fun getLastSyncTimestamp(userId: Int): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_PREFIX + userId, 0L)
    }

    /**
     * Obtiene el timestamp de la última sincronización para un tipo de contenido específico.
     */
    fun getLastSyncTimestamp(userId: Int, contentType: ContentType): Long {
        val key = when (contentType) {
            ContentType.MOVIES -> KEY_MOVIES_LAST_SYNC_PREFIX + userId
            ContentType.SERIES -> KEY_SERIES_LAST_SYNC_PREFIX + userId
            ContentType.LIVE_TV -> KEY_LIVE_LAST_SYNC_PREFIX + userId
        }
        return sharedPreferences.getLong(key, 0L)
    }

    /**
     * Obtiene el timestamp más antiguo entre todos los tipos de contenido.
     * Esto se usa para determinar si se necesita sincronización global.
     */
    fun getOldestSyncTimestamp(userId: Int): Long {
        val moviesTimestamp = getLastSyncTimestamp(userId, ContentType.MOVIES)
        val seriesTimestamp = getLastSyncTimestamp(userId, ContentType.SERIES)
        val liveTimestamp = getLastSyncTimestamp(userId, ContentType.LIVE_TV)
        val generalTimestamp = getLastSyncTimestamp(userId)
        
        // Si no hay timestamps específicos, usa el general
        if (moviesTimestamp == 0L && seriesTimestamp == 0L && liveTimestamp == 0L) {
            return generalTimestamp
        }
        
        // Encuentra el timestamp más antiguo (no cero)
        val timestamps = listOf(moviesTimestamp, seriesTimestamp, liveTimestamp)
            .filter { it > 0L }
        
        return if (timestamps.isNotEmpty()) {
            timestamps.minOrNull() ?: 0L
        } else {
            generalTimestamp
        }
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