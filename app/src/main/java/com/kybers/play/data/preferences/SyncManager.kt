package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - oldestTimestamp
        val isNeeded = timeDiff > SYNC_THRESHOLD
        
        Log.d("SyncManager", "isSyncNeeded for userId $userId: oldestTimestamp=$oldestTimestamp, currentTime=$currentTime, timeDiff=${timeDiff}ms (${timeDiff/(60*60*1000)}h), threshold=${SYNC_THRESHOLD}ms (${SYNC_THRESHOLD/(60*60*1000)}h), isNeeded=$isNeeded")
        
        return isNeeded
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
        val timestamp = System.currentTimeMillis()
        with(sharedPreferences.edit()) {
            putLong(KEY_LAST_SYNC_PREFIX + userId, timestamp)
            apply()
        }
        Log.d("SyncManager", "Saved general timestamp for userId $userId: $timestamp")
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
        val timestamp = System.currentTimeMillis()
        with(sharedPreferences.edit()) {
            putLong(key, timestamp)
            apply()
        }
        Log.d("SyncManager", "Saved ${contentType.name} timestamp for userId $userId: $timestamp")
    }

    /**
     * Obtiene el timestamp de la última sincronización de contenido general.
     */
    fun getLastSyncTimestamp(userId: Int): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_PREFIX + userId, 0L)
    }

    /**
     * Obtiene el timestamp de la última sincronización para un tipo de contenido específico.
     * Si no existe un timestamp específico pero existe uno general, lo migra.
     */
    fun getLastSyncTimestamp(userId: Int, contentType: ContentType): Long {
        val key = when (contentType) {
            ContentType.MOVIES -> KEY_MOVIES_LAST_SYNC_PREFIX + userId
            ContentType.SERIES -> KEY_SERIES_LAST_SYNC_PREFIX + userId
            ContentType.LIVE_TV -> KEY_LIVE_LAST_SYNC_PREFIX + userId
        }
        
        val contentSpecificTimestamp = sharedPreferences.getLong(key, 0L)
        
        // Migration: If no content-specific timestamp exists, use general timestamp
        if (contentSpecificTimestamp == 0L) {
            val generalTimestamp = getLastSyncTimestamp(userId)
            if (generalTimestamp > 0L) {
                Log.d("SyncManager", "Migrating general timestamp ($generalTimestamp) to ${contentType.name} for userId $userId")
                // Migrate the general timestamp to content-specific
                with(sharedPreferences.edit()) {
                    putLong(key, generalTimestamp)
                    apply()
                }
                return generalTimestamp
            }
        }
        
        return contentSpecificTimestamp
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
        
        Log.d("SyncManager", "getOldestSyncTimestamp for userId $userId: movies=$moviesTimestamp, series=$seriesTimestamp, live=$liveTimestamp, general=$generalTimestamp")
        
        // Collect all non-zero timestamps
        val allTimestamps = listOf(moviesTimestamp, seriesTimestamp, liveTimestamp, generalTimestamp)
            .filter { it > 0L }
        
        val result = if (allTimestamps.isNotEmpty()) {
            // Return the oldest (minimum) timestamp
            allTimestamps.minOrNull() ?: 0L
        } else {
            // If no timestamps exist, return 0L to force sync
            0L
        }
        
        Log.d("SyncManager", "Oldest timestamp result: $result")
        return result
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