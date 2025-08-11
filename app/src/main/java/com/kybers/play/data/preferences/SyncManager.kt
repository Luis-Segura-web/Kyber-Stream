package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Manages the synchronization timestamps to determine if a data refresh is needed.
 * Now supports separate timestamps for different content types.
 */
class SyncManager(
    context: Context,
    private val preferenceManager: PreferenceManager
) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sync_manager_prefs"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_timestamp_"
        private const val KEY_MOVIES_LAST_SYNC_PREFIX = "movies_last_sync_timestamp_"
        private const val KEY_SERIES_LAST_SYNC_PREFIX = "series_last_sync_timestamp_"
        private const val KEY_LIVE_LAST_SYNC_PREFIX = "live_last_sync_timestamp_"
        private const val KEY_EPG_LAST_SYNC_PREFIX = "epg_last_sync_timestamp_"
        private val EPG_SYNC_THRESHOLD = TimeUnit.HOURS.toMillis(24) // 24 horas
    }

    /**
     * Gets the sync threshold based on user preferences.
     * Returns Long.MAX_VALUE if user selected "Never" (0 hours).
     */
    private fun getSyncThreshold(): Long {
        val userFrequency = preferenceManager.getSyncFrequency()
        return if (userFrequency == 0) {
            Long.MAX_VALUE // Nunca sincronizar automáticamente
        } else {
            TimeUnit.HOURS.toMillis(userFrequency.toLong())
        }
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
        val threshold = getSyncThreshold()
        val oldestTimestamp = getOldestSyncTimestamp(userId)
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - oldestTimestamp
        val isNeeded = timeDiff > threshold
        
        Log.d("SyncManager", "isSyncNeeded for userId $userId: userThreshold=${threshold}ms (${threshold/(60*60*1000)}h), timeDiff=${timeDiff}ms, isNeeded=$isNeeded")
        
        return isNeeded
    }

    /**
     * Comprueba si se necesita sincronización para un tipo de contenido específico.
     */
    fun isSyncNeeded(userId: Int, contentType: ContentType): Boolean {
        val threshold = getSyncThreshold()
        val lastSync = getLastSyncTimestamp(userId, contentType)
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastSync
        val isNeeded = timeDiff > threshold
        
        Log.d("SyncManager", "isSyncNeeded for userId $userId, contentType ${contentType.name}: userThreshold=${threshold}ms (${threshold/(60*60*1000)}h), timeDiff=${timeDiff}ms, isNeeded=$isNeeded")
        
        return isNeeded
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
     * --- ¡LÓGICA DE SINCRONIZACIÓN EPG MEJORADA! ---
     * Comprueba si se necesita una sincronización de la guía de programación (EPG).
     * Ahora considera también la calidad de los datos EPG existentes.
     */
    fun isEpgSyncNeeded(userId: Int): Boolean {
        val lastSync = sharedPreferences.getLong(KEY_EPG_LAST_SYNC_PREFIX + userId, 0L)
        val timeSinceLastSync = System.currentTimeMillis() - lastSync
        
        // Si nunca se ha sincronizado, necesita sincronización
        if (lastSync == 0L) {
            Log.d("SyncManager", "EPG sync needed: Nunca se ha sincronizado para userId $userId")
            return true
        }
        
        // Si ha pasado más del umbral de tiempo, necesita sincronización
        if (timeSinceLastSync > EPG_SYNC_THRESHOLD) {
            Log.d("SyncManager", "EPG sync needed: Tiempo transcurrido ${timeSinceLastSync}ms > ${EPG_SYNC_THRESHOLD}ms para userId $userId")
            return true
        }
        
        Log.d("SyncManager", "EPG sync not needed: Última sincronización hace ${timeSinceLastSync}ms para userId $userId")
        return false
    }

    /**
     * --- ¡NUEVA FUNCIÓN PARA SINCRONIZACIÓN INTELIGENTE! ---
     * Verifica si la sincronización EPG es necesaria basándose en datos obsoletos.
     */
    fun isEpgDataStale(userId: Int, latestEventTimestamp: Long?): Boolean {
        // Si no hay datos EPG, está obsoleto
        if (latestEventTimestamp == null) {
            Log.d("SyncManager", "EPG data stale: No hay datos EPG para userId $userId")
            return true
        }
        
        // Verificar si la información EPG cubre al menos las próximas 12 horas
        val twelveHoursFromNow = (System.currentTimeMillis() / 1000) + TimeUnit.HOURS.toSeconds(12)
        val isStale = latestEventTimestamp < twelveHoursFromNow
        
        if (isStale) {
            Log.d("SyncManager", "EPG data stale: Último evento termina en $latestEventTimestamp, necesario hasta $twelveHoursFromNow")
        } else {
            Log.d("SyncManager", "EPG data fresh: Cobertura hasta $latestEventTimestamp")
        }
        
        return isStale
    }

    /**
     * --- ¡FUNCIÓN MEJORADA! ---
     * Guarda el timestamp de la última sincronización de EPG con información adicional.
     */
    fun saveEpgLastSyncTimestamp(userId: Int, eventCount: Int = 0) {
        val timestamp = System.currentTimeMillis()
        with(sharedPreferences.edit()) {
            putLong(KEY_EPG_LAST_SYNC_PREFIX + userId, timestamp)
            if (eventCount > 0) {
                putInt("${KEY_EPG_LAST_SYNC_PREFIX}events_${userId}", eventCount)
            }
            apply()
        }
        Log.d("SyncManager", "EPG sync timestamp saved para userId $userId: $timestamp con $eventCount eventos")
    }
}