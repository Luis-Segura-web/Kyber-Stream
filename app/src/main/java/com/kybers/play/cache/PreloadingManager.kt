package com.kybers.play.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreloadingManager(
    private val context: Context,
    private val cacheManager: CacheManager,
    private val streamPreloader: StreamPreloader
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isPreloading = MutableStateFlow(false)
    val isPreloading: StateFlow<Boolean> = _isPreloading.asStateFlow()
    
    private val preloadQueue = mutableListOf<PreloadItem>()
    private val maxConcurrentPreloads = 3
    
    data class PreloadItem(
        val contentId: Int,
        val streamUrl: String,
        val priority: PreloadPriority,
        val segmentCount: Int = 5
    )
    
    enum class PreloadPriority {
        HIGH, MEDIUM, LOW
    }
    
    suspend fun preloadPopularContent() {
        Log.d("PreloadingManager", "Iniciando precarga de contenido popular")
        _isPreloading.value = true
        
        try {
            // Obtener contenido popular del día
            val popularContent = getPopularContentForToday()
            Log.d("PreloadingManager", "Encontrado ${popularContent.size} contenidos populares")
            
            popularContent.take(10).forEach { content ->
                addToPreloadQueue(
                    PreloadItem(
                        contentId = content.id,
                        streamUrl = content.streamUrl,
                        priority = PreloadPriority.MEDIUM,
                        segmentCount = 3
                    )
                )
            }
            
            processPreloadQueue()
        } catch (e: Exception) {
            Log.e("PreloadingManager", "Error en precarga de contenido popular", e)
        } finally {
            _isPreloading.value = false
        }
    }
    
    suspend fun preloadUserPreferences(userId: Int) {
        Log.d("PreloadingManager", "Iniciando precarga de preferencias de usuario: $userId")
        
        try {
            // Obtener historial del usuario
            val userHistory = getUserViewingHistory(userId)
            val recommendations = generateRecommendations(userHistory)
            Log.d("PreloadingManager", "Generadas ${recommendations.size} recomendaciones para usuario $userId")
            
            recommendations.take(5).forEach { content ->
                addToPreloadQueue(
                    PreloadItem(
                        contentId = content.id,
                        streamUrl = content.streamUrl,
                        priority = PreloadPriority.HIGH,
                        segmentCount = 5
                    )
                )
            }
            
            processPreloadQueue()
        } catch (e: Exception) {
            Log.e("PreloadingManager", "Error en precarga de preferencias", e)
        }
    }
    
    fun preloadNextInSeries(currentEpisodeId: Int, seriesId: Int) {
        scope.launch {
            try {
                val nextEpisode = getNextEpisode(currentEpisodeId, seriesId)
                nextEpisode?.let { episode ->
                    Log.d("PreloadingManager", "Precargando siguiente episodio: ${episode.title}")
                    addToPreloadQueue(
                        PreloadItem(
                            contentId = episode.id,
                            streamUrl = episode.streamUrl,
                            priority = PreloadPriority.HIGH,
                            segmentCount = 10
                        )
                    )
                    processPreloadQueue()
                }
            } catch (e: Exception) {
                Log.e("PreloadingManager", "Error precargando siguiente episodio", e)
            }
        }
    }
    
    fun getPreloadingStats(): PreloadingStats {
        val queueSize = synchronized(preloadQueue) { preloadQueue.size }
        val cacheStats = cacheManager.getCacheStats()
        return PreloadingStats(
            isPreloading = _isPreloading.value,
            queueSize = queueSize,
            cacheStats = cacheStats
        )
    }
    
    data class PreloadingStats(
        val isPreloading: Boolean,
        val queueSize: Int,
        val cacheStats: CacheManager.CacheStats
    )
    
    private fun addToPreloadQueue(item: PreloadItem) {
        synchronized(preloadQueue) {
            if (!preloadQueue.any { it.contentId == item.contentId }) {
                preloadQueue.add(item)
                preloadQueue.sortByDescending { it.priority.ordinal }
            }
        }
    }
    
    private suspend fun processPreloadQueue() {
        val itemsToProcess = synchronized(preloadQueue) {
            preloadQueue.take(maxConcurrentPreloads).also {
                preloadQueue.removeAll(it.toSet())
            }
        }
        
        itemsToProcess.forEach { item ->
            scope.launch {
                try {
                    streamPreloader.preloadSegments(item.streamUrl, item.segmentCount)
                    cacheManager.markAsPreloaded(item.contentId)
                    Log.d("PreloadingManager", "Contenido precargado: ${item.contentId}")
                } catch (e: Exception) {
                    Log.e("PreloadingManager", "Error precargando ${item.contentId}", e)
                }
            }
        }
    }
    
    private suspend fun getPopularContentForToday(): List<Content> {
        // Implementación simulada para contenido popular
        // En una implementación real, esto se obtendría de una API o base de datos
        return listOf(
            Content(1, "https://example.com/stream1.m3u8", "Popular Movie 1", ContentType.MOVIE),
            Content(2, "https://example.com/stream2.m3u8", "Popular Series 1", ContentType.SERIES),
            Content(3, "https://example.com/stream3.m3u8", "Popular Channel 1", ContentType.CHANNEL)
        )
    }
    
    private suspend fun getUserViewingHistory(userId: Int): List<ViewingRecord> {
        // Implementación simulada para historial de usuario
        // En una implementación real, esto se obtendría de la base de datos local
        return listOf(
            ViewingRecord(1, userId, System.currentTimeMillis() - 86400000, 3600),
            ViewingRecord(2, userId, System.currentTimeMillis() - 172800000, 7200)
        )
    }
    
    private suspend fun generateRecommendations(history: List<ViewingRecord>): List<Content> {
        // Algoritmo simple de recomendaciones basado en historial
        // En una implementación real, esto sería más sofisticado
        return getPopularContentForToday().take(3)
    }
    
    private suspend fun getNextEpisode(currentEpisodeId: Int, seriesId: Int): Episode? {
        // Implementación simulada para obtener siguiente episodio
        // En una implementación real, esto consultaría la base de datos
        return Episode(
            id = currentEpisodeId + 1,
            seriesId = seriesId,
            episodeNumber = 2,
            seasonNumber = 1,
            streamUrl = "https://example.com/next_episode.m3u8",
            title = "Next Episode"
        )
    }
}