package com.kybers.play.cache

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.math.pow

class StreamPreloader(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) {
    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY = 1000L // 1 second
        private const val MAX_RETRY_DELAY = 10000L // 10 seconds
    }
    
    suspend fun preloadSegments(streamUrl: String, segmentCount: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("StreamPreloader", "Iniciando precarga de $segmentCount segmentos para: $streamUrl")
                
                // Obtener playlist M3U8 con reintentos
                val playlistContent = downloadPlaylist(streamUrl)
                val segmentUrls = parseM3U8Segments(playlistContent, streamUrl)
                
                if (segmentUrls.isEmpty()) {
                    Log.w("StreamPreloader", "No se encontraron segmentos en la playlist: $streamUrl")
                    return@withContext
                }
                
                // Precargar los primeros segmentos
                val segmentsToPreload = segmentUrls.take(segmentCount)
                Log.d("StreamPreloader", "Precargando ${segmentsToPreload.size} segmentos de ${segmentUrls.size} disponibles")
                
                var successCount = 0
                var failureCount = 0
                
                segmentsToPreload.forEachIndexed { index, segmentUrl ->
                    try {
                        downloadAndCacheSegment(segmentUrl, index)
                        successCount++
                        Log.d("StreamPreloader", "Segmento $index precargado exitosamente ($successCount/${segmentsToPreload.size})")
                    } catch (e: SocketTimeoutException) {
                        failureCount++
                        Log.w("StreamPreloader", "Timeout precargando segmento $index ($failureCount fallos hasta ahora)", e)
                        // Continuar con el siguiente segmento en caso de timeout
                    } catch (e: Exception) {
                        failureCount++
                        Log.e("StreamPreloader", "Error precargando segmento $index ($failureCount fallos hasta ahora)", e)
                        // Continuar con el siguiente segmento en caso de error
                    }
                }
                
                Log.d("StreamPreloader", "Precarga completada: $successCount exitosos, $failureCount fallos de $segmentCount solicitados")
                
                if (successCount == 0) {
                    throw Exception("No se pudo precargar ningún segmento de $segmentCount intentados")
                }
                
            } catch (e: Exception) {
                Log.e("StreamPreloader", "Error en precarga de stream: $streamUrl", e)
                throw e
            }
        }
    }
    
    private suspend fun downloadPlaylist(url: String): String {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            
            for (attempt in 0 until MAX_RETRIES) {
                try {
                    Log.d("StreamPreloader", "Descargando playlist (intento ${attempt + 1}/$MAX_RETRIES): $url")
                    
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Kyber-Stream/1.0")
                        .build()
                        
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Error descargando playlist: ${response.code}")
                        }
                        val content = response.body?.string() ?: throw Exception("Playlist vacío")
                        Log.d("StreamPreloader", "Playlist descargado exitosamente en intento ${attempt + 1}")
                        return@withContext content
                    }
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    Log.w("StreamPreloader", "Timeout en intento ${attempt + 1}/$MAX_RETRIES para playlist: $url", e)
                    
                    if (attempt < MAX_RETRIES - 1) {
                        val delay = calculateRetryDelay(attempt)
                        Log.d("StreamPreloader", "Reintentando en ${delay}ms...")
                        delay(delay)
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.e("StreamPreloader", "Error no recuperable descargando playlist en intento ${attempt + 1}: $url", e)
                    // Para errores no recuperables, no reintentar
                    break
                }
            }
            
            throw lastException ?: Exception("Error desconocido descargando playlist")
        }
    }
    
    private fun parseM3U8Segments(playlistContent: String, baseUrl: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = playlistContent.lines()
        
        lines.forEach { line ->
            if (!line.startsWith("#") && line.isNotBlank()) {
                val segmentUrl = if (line.startsWith("http")) {
                    line
                } else {
                    // URL relativa, construir URL completa
                    val basePath = baseUrl.substringBeforeLast("/")
                    "$basePath/$line"
                }
                segments.add(segmentUrl)
            }
        }
        
        return segments
    }
    
    private suspend fun downloadAndCacheSegment(segmentUrl: String, index: Int) {
        withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            
            for (attempt in 0 until MAX_RETRIES) {
                try {
                    Log.d("StreamPreloader", "Descargando segmento $index (intento ${attempt + 1}/$MAX_RETRIES): $segmentUrl")
                    
                    val request = Request.Builder()
                        .url(segmentUrl)
                        .addHeader("User-Agent", "Kyber-Stream/1.0")
                        .build()
                        
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { inputStream ->
                                val fileName = "segment_${segmentUrl.hashCode()}_$index.ts"
                                val cacheFile = File(cacheDir, fileName)
                                
                                FileOutputStream(cacheFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                                Log.d("StreamPreloader", "Segmento $index descargado exitosamente en intento ${attempt + 1}")
                                return@withContext
                            }
                        } else {
                            throw Exception("Error descargando segmento: ${response.code}")
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    Log.w("StreamPreloader", "Timeout en intento ${attempt + 1}/$MAX_RETRIES para segmento $index: $segmentUrl", e)
                    
                    if (attempt < MAX_RETRIES - 1) {
                        val delay = calculateRetryDelay(attempt)
                        Log.d("StreamPreloader", "Reintentando descarga de segmento $index en ${delay}ms...")
                        delay(delay)
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.e("StreamPreloader", "Error no recuperable descargando segmento $index en intento ${attempt + 1}: $segmentUrl", e)
                    // Para errores no recuperables, no reintentar
                    break
                }
            }
            
            // Si llegamos aquí, todos los intentos fallaron
            Log.e("StreamPreloader", "Falló la descarga del segmento $index después de $MAX_RETRIES intentos: $segmentUrl", lastException)
            throw lastException ?: Exception("Error desconocido descargando segmento")
        }
    }
    
    /**
     * Calcula el delay para el siguiente reintento usando backoff exponencial
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val exponentialDelay = INITIAL_RETRY_DELAY * 2.0.pow(attempt).toLong()
        return min(exponentialDelay, MAX_RETRY_DELAY)
    }
}