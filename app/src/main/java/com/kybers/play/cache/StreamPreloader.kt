package com.kybers.play.cache

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class StreamPreloader(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) {
    
    suspend fun preloadSegments(streamUrl: String, segmentCount: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("StreamPreloader", "Iniciando precarga de $segmentCount segmentos para: $streamUrl")
                
                // Obtener playlist M3U8
                val playlistContent = downloadPlaylist(streamUrl)
                val segmentUrls = parseM3U8Segments(playlistContent, streamUrl)
                
                // Precargar los primeros segmentos
                segmentUrls.take(segmentCount).forEachIndexed { index, segmentUrl ->
                    try {
                        downloadAndCacheSegment(segmentUrl, index)
                        Log.d("StreamPreloader", "Segmento $index precargado exitosamente")
                    } catch (e: Exception) {
                        Log.e("StreamPreloader", "Error precargando segmento $index", e)
                    }
                }
                
            } catch (e: Exception) {
                Log.e("StreamPreloader", "Error en precarga de stream: $streamUrl", e)
            }
        }
    }
    
    private suspend fun downloadPlaylist(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Kyber-Stream/1.0")
                .build()
                
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Error descargando playlist: ${response.code}")
                }
                response.body?.string() ?: throw Exception("Playlist vacío")
            }
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
                    }
                }
            }
        }
    }
}