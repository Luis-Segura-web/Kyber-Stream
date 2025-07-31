package com.kybers.play.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager

/**
 * Local data models for the preloading system
 */
data class Content(
    val id: Int,
    val streamUrl: String,
    val title: String,
    val type: ContentType
)

enum class ContentType {
    MOVIE, SERIES, CHANNEL
}

data class ViewingRecord(
    val contentId: Int,
    val userId: Int,
    val timestamp: Long,
    val duration: Long
)

data class Episode(
    val id: Int,
    val seriesId: Int,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val streamUrl: String,
    val title: String
)

class PreloadingManager(
    private val context: Context,
    private val cacheManager: CacheManager,
    private val streamPreloader: StreamPreloader,
    private val vodRepository: VodRepository,
    private val liveRepository: LiveRepository,
    private val user: User,
    private val preferenceManager: PreferenceManager
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
            
            if (popularContent.isEmpty()) {
                Log.w("PreloadingManager", "No se encontró contenido popular para precargar")
                return
            }
            
            popularContent.take(10).forEach { content ->
                try {
                    addToPreloadQueue(
                        PreloadItem(
                            contentId = content.id,
                            streamUrl = content.streamUrl,
                            priority = PreloadPriority.MEDIUM,
                            segmentCount = 3
                        )
                    )
                } catch (e: Exception) {
                    Log.w("PreloadingManager", "Error añadiendo contenido ${content.id} a la cola de precarga", e)
                }
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
            
            if (recommendations.isEmpty()) {
                Log.w("PreloadingManager", "No se encontraron recomendaciones para el usuario $userId")
                return
            }
            
            recommendations.take(5).forEach { content ->
                try {
                    addToPreloadQueue(
                        PreloadItem(
                            contentId = content.id,
                            streamUrl = content.streamUrl,
                            priority = PreloadPriority.HIGH,
                            segmentCount = 5
                        )
                    )
                } catch (e: Exception) {
                    Log.w("PreloadingManager", "Error añadiendo recomendación ${content.id} a la cola de precarga", e)
                }
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
                    Log.d("PreloadingManager", "Iniciando precarga de contenido: ${item.contentId}")
                    streamPreloader.preloadSegments(item.streamUrl, item.segmentCount)
                    cacheManager.markAsPreloaded(item.contentId)
                    Log.d("PreloadingManager", "Contenido precargado exitosamente: ${item.contentId}")
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w("PreloadingManager", "Timeout precargando contenido ${item.contentId} - esto es normal en conexiones lentas", e)
                    // Para timeouts, no marcamos como error crítico, solo advertencia
                } catch (e: java.net.UnknownHostException) {
                    Log.e("PreloadingManager", "Error de conectividad precargando ${item.contentId} - verifica la conexión a internet", e)
                } catch (e: java.io.IOException) {
                    Log.e("PreloadingManager", "Error de E/O precargando ${item.contentId} - problema de red o servidor", e)
                } catch (e: Exception) {
                    Log.e("PreloadingManager", "Error inesperado precargando ${item.contentId}", e)
                }
            }
        }
    }
    
    private suspend fun getPopularContentForToday(): List<Content> {
        Log.d("PreloadingManager", "Obteniendo contenido popular del día desde la base de datos")
        val popularContent = mutableListOf<Content>()
        
        try {
            // Obtener 5 películas populares desde vodRepository
            val movies = vodRepository.getAllMovies(user.id).first().take(5)
            movies.forEach { movie ->
                popularContent.add(
                    Content(
                        id = movie.streamId,
                        streamUrl = buildStreamUrl(movie.streamId, ContentType.MOVIE, movie.containerExtension),
                        title = movie.name,
                        type = ContentType.MOVIE
                    )
                )
            }
            Log.d("PreloadingManager", "Encontradas ${movies.size} películas populares")
            
            // Series preloading disabled to avoid 403 errors
            // TODO: Implement episode-specific preloading logic in the future
            /*
            val series = vodRepository.getAllSeries(user.id).first().take(3)
            series.forEach { serie ->
                popularContent.add(
                    Content(
                        id = serie.seriesId,
                        streamUrl = buildStreamUrl(serie.seriesId, ContentType.SERIES, "m3u8"),
                        title = serie.name,
                        type = ContentType.SERIES
                    )
                )
            }
            Log.d("PreloadingManager", "Encontradas ${series.size} series populares")
            */
            
            // Obtener 2 canales desde liveRepository
            val liveStreams = liveRepository.getRawLiveStreams(user.id).first().take(2)
            liveStreams.forEach { stream ->
                popularContent.add(
                    Content(
                        id = stream.streamId,
                        streamUrl = buildStreamUrl(stream.streamId, ContentType.CHANNEL, "ts"),
                        title = stream.name,
                        type = ContentType.CHANNEL
                    )
                )
            }
            Log.d("PreloadingManager", "Encontrados ${liveStreams.size} canales populares")
            
        } catch (e: Exception) {
            Log.e("PreloadingManager", "Error obteniendo contenido popular", e)
        }
        
        Log.d("PreloadingManager", "Total contenido popular obtenido: ${popularContent.size}")
        return popularContent
    }
    
    private suspend fun getUserViewingHistory(userId: Int): List<ViewingRecord> {
        Log.d("PreloadingManager", "Obteniendo historial de visualización real para usuario: $userId")
        val viewingRecords = mutableListOf<ViewingRecord>()
        
        try {
            // Obtener posiciones de reproducción desde preferenceManager
            val playbackPositions = preferenceManager.getAllPlaybackPositions()
            val episodeStates = preferenceManager.getAllEpisodePlaybackStates()
            
            // Convertir posiciones de películas/series a ViewingRecord objects
            playbackPositions.forEach { (contentId, position) ->
                try {
                    val id = contentId.toIntOrNull() ?: return@forEach
                    viewingRecords.add(
                        ViewingRecord(
                            contentId = id,
                            userId = userId,
                            timestamp = System.currentTimeMillis() - (position / 1000), // Aproximar timestamp
                            duration = position
                        )
                    )
                } catch (e: Exception) {
                    Log.w("PreloadingManager", "Error procesando posición de reproducción: $contentId", e)
                }
            }
            
            // Convertir estados de episodios a ViewingRecord objects
            episodeStates.forEach { (episodeId, state) ->
                try {
                    val id = episodeId.toIntOrNull() ?: return@forEach
                    val (position, duration) = state
                    viewingRecords.add(
                        ViewingRecord(
                            contentId = id,
                            userId = userId,
                            timestamp = System.currentTimeMillis() - (position / 1000), // Aproximar timestamp
                            duration = position
                        )
                    )
                } catch (e: Exception) {
                    Log.w("PreloadingManager", "Error procesando estado de episodio: $episodeId", e)
                }
            }
            
            Log.d("PreloadingManager", "Encontrados ${viewingRecords.size} registros de visualización")
            
        } catch (e: Exception) {
            Log.e("PreloadingManager", "Error obteniendo historial de visualización", e)
        }
        
        return viewingRecords.sortedByDescending { it.timestamp }.take(20) // Limit to recent 20 items
    }
    
    private suspend fun generateRecommendations(history: List<ViewingRecord>): List<Content> {
        Log.d("PreloadingManager", "Generando recomendaciones basadas en historial real")
        val recommendations = mutableListOf<Content>()
        
        try {
            if (history.isNotEmpty()) {
                // Usar historial real para generar recomendaciones
                Log.d("PreloadingManager", "Usando historial de ${history.size} elementos para recomendaciones")
                
                // Obtener IDs de contenido más visto
                val mostWatchedIds = history
                    .groupBy { it.contentId }
                    .mapValues { it.value.sumOf { record -> record.duration } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(3)
                    .map { it.first }
                
                // Buscar contenido similar en la base de datos
                val movies = vodRepository.getAllMovies(user.id).first()
                
                // Recomendar películas similares a las más vistas
                movies.filter { !mostWatchedIds.contains(it.streamId) }
                    .take(5)
                    .forEach { movie ->
                        recommendations.add(
                            Content(
                                id = movie.streamId,
                                streamUrl = buildStreamUrl(movie.streamId, ContentType.MOVIE, movie.containerExtension),
                                title = movie.name,
                                type = ContentType.MOVIE
                            )
                        )
                    }
                
                // Series recommendations disabled to avoid 403 errors
                // TODO: Implement episode-specific recommendation logic in the future
                /*
                val series = vodRepository.getAllSeries(user.id).first()
                series.filter { !mostWatchedIds.contains(it.seriesId) }
                    .take(2)
                    .forEach { serie ->
                        recommendations.add(
                            Content(
                                id = serie.seriesId,
                                streamUrl = buildStreamUrl(serie.seriesId, ContentType.SERIES, "m3u8"),
                                title = serie.name,
                                type = ContentType.SERIES
                            )
                        )
                    }
                */
            } else {
                // Fallback a contenido popular si no hay suficiente historial
                Log.d("PreloadingManager", "Sin historial suficiente, usando contenido popular como fallback")
                return getPopularContentForToday().take(3)
            }
            
        } catch (e: Exception) {
            Log.e("PreloadingManager", "Error generando recomendaciones", e)
            // Fallback a contenido popular en caso de error
            return getPopularContentForToday().take(3)
        }
        
        Log.d("PreloadingManager", "Generadas ${recommendations.size} recomendaciones")
        return recommendations
    }
    
    private suspend fun getNextEpisode(currentEpisodeId: Int, seriesId: Int): Episode? {
        Log.d("PreloadingManager", "Obteniendo siguiente episodio real para serie: $seriesId, episodio actual: $currentEpisodeId")
        
        try {
            // Obtener episodios reales de la base de datos
            val episodes = vodRepository.getEpisodesForSeries(seriesId, user.id).first()
            
            if (episodes.isEmpty()) {
                Log.w("PreloadingManager", "No se encontraron episodios para la serie: $seriesId")
                return null
            }
            
            // Encontrar el episodio actual por ID (convertir a String para coincidir con el modelo)
            val currentEpisode = episodes.find { it.id == currentEpisodeId.toString() }
            if (currentEpisode == null) {
                Log.w("PreloadingManager", "No se encontró el episodio actual: $currentEpisodeId")
                return null
            }
            
            // Buscar el siguiente episodio en la misma temporada
            val nextEpisodeInSeason = episodes
                .filter { it.season == currentEpisode.season && it.episodeNum > currentEpisode.episodeNum }
                .minByOrNull { it.episodeNum }
            
            if (nextEpisodeInSeason != null) {
                Log.d("PreloadingManager", "Encontrado siguiente episodio en la misma temporada: ${nextEpisodeInSeason.title}")
                return Episode(
                    id = nextEpisodeInSeason.id.toIntOrNull() ?: 0,
                    seriesId = seriesId,
                    episodeNumber = nextEpisodeInSeason.episodeNum,
                    seasonNumber = nextEpisodeInSeason.season,
                    streamUrl = buildStreamUrl(nextEpisodeInSeason.id.toIntOrNull() ?: 0, ContentType.SERIES, nextEpisodeInSeason.containerExtension),
                    title = nextEpisodeInSeason.title ?: "Episodio ${nextEpisodeInSeason.episodeNum}"
                )
            }
            
            // Si no hay más episodios en la temporada actual, buscar el primer episodio de la siguiente temporada
            val nextSeason = episodes
                .filter { it.season > currentEpisode.season }
                .minByOrNull { it.season }
            
            if (nextSeason != null) {
                val firstEpisodeNextSeason = episodes
                    .filter { it.season == nextSeason.season }
                    .minByOrNull { it.episodeNum }
                
                if (firstEpisodeNextSeason != null) {
                    Log.d("PreloadingManager", "Encontrado primer episodio de la siguiente temporada: ${firstEpisodeNextSeason.title}")
                    return Episode(
                        id = firstEpisodeNextSeason.id.toIntOrNull() ?: 0,
                        seriesId = seriesId,
                        episodeNumber = firstEpisodeNextSeason.episodeNum,
                        seasonNumber = firstEpisodeNextSeason.season,
                        streamUrl = buildStreamUrl(firstEpisodeNextSeason.id.toIntOrNull() ?: 0, ContentType.SERIES, firstEpisodeNextSeason.containerExtension),
                        title = firstEpisodeNextSeason.title ?: "Episodio ${firstEpisodeNextSeason.episodeNum}"
                    )
                }
            }
            
            Log.d("PreloadingManager", "No se encontró siguiente episodio para la serie: $seriesId")
            return null
            
        } catch (e: Exception) {
            Log.e("PreloadingManager", "Error obteniendo siguiente episodio", e)
            return null
        }
    }
    
    /**
     * Construye URLs reales para streams usando las credenciales del usuario
     */
    private fun buildStreamUrl(streamId: Int, contentType: ContentType, extension: String): String {
        val baseUrl = user.url.trimEnd('/')
        val username = user.username
        val password = user.password
        
        return when (contentType) {
            ContentType.MOVIE -> "$baseUrl/movie/$username/$password/$streamId.$extension"
            ContentType.SERIES -> "$baseUrl/series/$username/$password/$streamId.$extension"
            ContentType.CHANNEL -> "$baseUrl/live/$username/$password/$streamId.$extension"
        }
    }
}