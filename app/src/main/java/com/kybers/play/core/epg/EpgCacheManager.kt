package com.kybers.play.core.epg

import android.util.Log
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.remote.model.EpgEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * --- ¡CACHE INTELIGENTE PARA EPG! ---
 * Gestiona el cache en memoria de eventos EPG actuales y próximos para mejorar performance.
 * Mantiene datos frecuentemente consultados en memoria con invalidación automática.
 */
@Singleton
class EpgCacheManager @Inject constructor(
    private val epgEventDao: EpgEventDao
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Cache para eventos actuales por canal
    private val currentEventsCache = ConcurrentHashMap<String, CachedEvent>()
    
    // Cache para próximos eventos por canal  
    private val nextEventsCache = ConcurrentHashMap<String, CachedEvent>()
    
    // Cache para eventos en batch (múltiples canales)
    private val batchCurrentEventsCache = ConcurrentHashMap<String, CachedEventBatch>()
    
    companion object {
        internal val CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(5) // 5 minutos
        private const val MAX_CACHE_SIZE = 1000
    }
    
    /**
     * Obtiene el evento actual para un canal con cache inteligente.
     */
    suspend fun getCurrentEventForChannel(channelId: Int, userId: Int): EpgEvent? {
        val currentTime = System.currentTimeMillis() / 1000
        val cacheKey = "current_${channelId}_${userId}"
        
        val cached = currentEventsCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return cached.event
        }
        
        // Cache miss o expirado, consultar BD
        val event = epgEventDao.getCurrentEventForChannel(channelId, userId, currentTime)
        
        // Actualizar cache
        if (event != null) {
            currentEventsCache[cacheKey] = CachedEvent(event, System.currentTimeMillis())
        } else {
            // Cachear null también para evitar consultas repetidas
            currentEventsCache[cacheKey] = CachedEvent(null, System.currentTimeMillis())
        }
        
        cleanupCacheIfNeeded()
        return event
    }
    
    /**
     * Obtiene el próximo evento para un canal con cache.
     */
    suspend fun getNextEventForChannel(channelId: Int, userId: Int): EpgEvent? {
        val currentTime = System.currentTimeMillis() / 1000
        val cacheKey = "next_${channelId}_${userId}"
        
        val cached = nextEventsCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return cached.event
        }
        
        val event = epgEventDao.getNextEventForChannel(channelId, userId, currentTime)
        
        if (event != null) {
            nextEventsCache[cacheKey] = CachedEvent(event, System.currentTimeMillis())
        } else {
            nextEventsCache[cacheKey] = CachedEvent(null, System.currentTimeMillis())
        }
        
        cleanupCacheIfNeeded()
        return event
    }
    
    /**
     * Obtiene eventos actuales para múltiples canales de forma eficiente.
     */
    suspend fun getCurrentEventsForChannels(channelIds: List<Int>, userId: Int): Map<Int, EpgEvent> {
        val currentTime = System.currentTimeMillis() / 1000
        val cacheKey = "batch_current_${channelIds.sorted().joinToString(",")}_${userId}"
        
        val cached = batchCurrentEventsCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return cached.events
        }
        
        // Consultar BD para todos los canales
        val events = epgEventDao.getCurrentEventsForChannels(channelIds, userId, currentTime)
        val eventsMap = events.associateBy { it.channelId }
        
        // Cachear resultado
        batchCurrentEventsCache[cacheKey] = CachedEventBatch(eventsMap, System.currentTimeMillis())
        
        cleanupCacheIfNeeded()
        return eventsMap
    }
    
    /**
     * Obtiene eventos actuales y próximos para un canal de forma optimizada.
     */
    suspend fun getCurrentAndNextEventsForChannel(channelId: Int, userId: Int): Pair<EpgEvent?, EpgEvent?> {
        // Usar corrutinas para obtener ambos en paralelo
        return coroutineScope {
            val currentDeferred = async { getCurrentEventForChannel(channelId, userId) }
            val nextDeferred = async { getNextEventForChannel(channelId, userId) }
            
            Pair(currentDeferred.await(), nextDeferred.await())
        }
    }
    
    /**
     * Invalida el cache para un canal específico.
     */
    fun invalidateChannelCache(channelId: Int, userId: Int) {
        val currentKey = "current_${channelId}_${userId}"
        val nextKey = "next_${channelId}_${userId}"
        
        currentEventsCache.remove(currentKey)
        nextEventsCache.remove(nextKey)
        
        // Invalidar caches batch que contengan este canal
        batchCurrentEventsCache.keys.forEach { key ->
            if (key.contains("_${userId}")) {
                batchCurrentEventsCache.remove(key)
            }
        }
    }
    
    /**
     * Invalida todo el cache para un usuario.
     */
    fun invalidateUserCache(userId: Int) {
        val keysToRemove = mutableListOf<String>()
        
        currentEventsCache.keys.forEach { key ->
            if (key.endsWith("_${userId}")) {
                keysToRemove.add(key)
            }
        }
        
        nextEventsCache.keys.forEach { key ->
            if (key.endsWith("_${userId}")) {
                keysToRemove.add(key)
            }
        }
        
        batchCurrentEventsCache.keys.forEach { key ->
            if (key.contains("_${userId}")) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { key ->
            currentEventsCache.remove(key)
            nextEventsCache.remove(key)
            batchCurrentEventsCache.remove(key)
        }
        
        Log.d("EpgCacheManager", "Cache invalidado para usuario $userId. Llaves removidas: ${keysToRemove.size}")
    }
    
    /**
     * Limpia el cache si excede el tamaño máximo.
     */
    private fun cleanupCacheIfNeeded() {
        val totalSize = currentEventsCache.size + nextEventsCache.size + batchCurrentEventsCache.size
        
        if (totalSize > MAX_CACHE_SIZE) {
            // Remover entradas más antiguas
            val currentTime = System.currentTimeMillis()
            
            currentEventsCache.entries.removeIf { 
                currentTime - it.value.cachedAt > CACHE_DURATION_MS 
            }
            nextEventsCache.entries.removeIf { 
                currentTime - it.value.cachedAt > CACHE_DURATION_MS 
            }
            batchCurrentEventsCache.entries.removeIf { 
                currentTime - it.value.cachedAt > CACHE_DURATION_MS 
            }
            
            Log.d("EpgCacheManager", "Cache limpiado. Tamaño actual: ${currentEventsCache.size + nextEventsCache.size + batchCurrentEventsCache.size}")
        }
    }
    
    /**
     * Limpia todo el cache.
     */
    fun clearAllCache() {
        currentEventsCache.clear()
        nextEventsCache.clear()
        batchCurrentEventsCache.clear()
        Log.d("EpgCacheManager", "Todo el cache EPG ha sido limpiado")
    }
    
    /**
     * Obtiene estadísticas del cache.
     */
    fun getCacheStats(): EpgCacheStats {
        return EpgCacheStats(
            currentEventsSize = currentEventsCache.size,
            nextEventsSize = nextEventsCache.size,
            batchEventsSize = batchCurrentEventsCache.size
        )
    }
}

/**
 * Evento cacheado con timestamp.
 */
private data class CachedEvent(
    val event: EpgEvent?,
    val cachedAt: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - cachedAt > EpgCacheManager.CACHE_DURATION_MS
    }
}

/**
 * Batch de eventos cacheados.
 */
private data class CachedEventBatch(
    val events: Map<Int, EpgEvent>,
    val cachedAt: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - cachedAt > EpgCacheManager.CACHE_DURATION_MS
    }
}

/**
 * Estadísticas del cache EPG.
 */
data class EpgCacheStats(
    val currentEventsSize: Int,
    val nextEventsSize: Int,
    val batchEventsSize: Int
) {
    val totalSize: Int get() = currentEventsSize + nextEventsSize + batchEventsSize
}