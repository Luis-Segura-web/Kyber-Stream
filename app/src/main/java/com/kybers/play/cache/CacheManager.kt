package com.kybers.play.cache

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CacheManager(
    private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "stream_cache")
    private val maxCacheSize = calculateOptimalCacheSize()
    private val preloadedContent = ConcurrentHashMap<Int, Long>()
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    enum class CachePriority {
        HIGH, MEDIUM, LOW
    }
    
    data class CacheItem(
        val contentId: Int,
        val filePath: String,
        val size: Long,
        val timestamp: Long,
        val priority: CachePriority
    )
    
    suspend fun cacheContent(contentId: Int, data: ByteArray, priority: CachePriority) {
        withContext(Dispatchers.IO) {
            try {
                val fileName = "content_$contentId.cache"
                val file = File(cacheDir, fileName)
                
                // Verificar espacio disponible
                if (getCurrentCacheSize() + data.size > maxCacheSize) {
                    cleanupOldCache()
                }
                
                file.writeBytes(data)
                Log.d("CacheManager", "Contenido cacheado: $contentId, tamaño: ${data.size}")
                
            } catch (e: Exception) {
                Log.e("CacheManager", "Error cacheando contenido $contentId", e)
            }
        }
    }
    
    suspend fun getCachedContent(contentId: Int): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "content_$contentId.cache"
                val file = File(cacheDir, fileName)
                
                if (file.exists()) {
                    // Actualizar tiempo de acceso
                    file.setLastModified(System.currentTimeMillis())
                    file.readBytes()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("CacheManager", "Error leyendo cache para $contentId", e)
                null
            }
        }
    }
    
    fun getCacheDir(): File = cacheDir
    
    fun markAsPreloaded(contentId: Int) {
        preloadedContent[contentId] = System.currentTimeMillis()
    }
    
    fun isPreloaded(contentId: Int): Boolean {
        return preloadedContent.containsKey(contentId)
    }
    
    private fun calculateOptimalCacheSize(): Long {
        return try {
            val stat = StatFs(context.cacheDir.path)
            val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
            
            // Usar máximo 10% del espacio disponible o 2GB, lo que sea menor
            minOf((availableBytes * 0.1).toLong(), 2L * 1024 * 1024 * 1024)
        } catch (e: Exception) {
            Log.e("CacheManager", "Error calculando tamaño de cache", e)
            // Fallback to 512MB
            512L * 1024 * 1024
        }
    }
    
    private fun getCurrentCacheSize(): Long {
        return try {
            cacheDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            Log.e("CacheManager", "Error calculando tamaño actual de cache", e)
            0L
        }
    }
    
    private suspend fun cleanupOldCache() {
        withContext(Dispatchers.IO) {
            try {
                val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return@withContext
                val targetSize = maxCacheSize * 0.7 // Liberar 30% del espacio
                
                for (file in files) {
                    if (getCurrentCacheSize() <= targetSize) break
                    
                    val deletedSize = file.length()
                    file.delete()
                    Log.d("CacheManager", "Archivo eliminado del cache: ${file.name}")
                }
                
                Log.d("CacheManager", "Limpieza de cache completada.")
                
            } catch (e: Exception) {
                Log.e("CacheManager", "Error en limpieza de cache", e)
            }
        }
    }
    
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                preloadedContent.clear()
                Log.d("CacheManager", "Cache completamente limpiado")
            } catch (e: Exception) {
                Log.e("CacheManager", "Error limpiando cache", e)
            }
        }
    }
    
    fun getCacheStats(): CacheStats {
        val totalSize = getCurrentCacheSize()
        val fileCount = cacheDir.listFiles()?.size ?: 0
        val availableSpace = maxCacheSize - totalSize
        
        return CacheStats(
            totalSize = totalSize,
            maxSize = maxCacheSize,
            fileCount = fileCount,
            availableSpace = availableSpace,
            usagePercentage = (totalSize.toDouble() / maxCacheSize * 100).toFloat()
        )
    }
    
    data class CacheStats(
        val totalSize: Long,
        val maxSize: Long,
        val fileCount: Int,
        val availableSpace: Long,
        val usagePercentage: Float
    )
}