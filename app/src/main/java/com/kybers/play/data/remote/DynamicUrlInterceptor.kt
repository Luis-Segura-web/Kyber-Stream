package com.kybers.play.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Interceptor que permite cambiar dinámicamente la URL base para las requests de Xtream Codes.
 * Resuelve el problema de duplicación de protocolo HTTP que causa URLs malformadas.
 */
class DynamicUrlInterceptor : Interceptor {
    
    companion object {
        private const val TAG = "DynamicUrlInterceptor"
        private const val DEFAULT_BASE_URL = "http://example.com/"
    }
    
    private val baseUrl = AtomicReference<String>(DEFAULT_BASE_URL)
    
    /**
     * Actualiza la URL base de manera segura, evitando duplicación de protocolos
     */
    fun updateBaseUrl(newBaseUrl: String) {
        val normalizedUrl = normalizeUrl(newBaseUrl)
        baseUrl.set(normalizedUrl)
        
        // Usar Android Log si está disponible, sino usar println para tests
        try {
            android.util.Log.d(TAG, "Base URL updated to: $normalizedUrl")
        } catch (e: Exception) {
            println("$TAG: Base URL updated to: $normalizedUrl")
        }
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentBaseUrl = baseUrl.get()
        
        // Solo redirigir si la request original es hacia example.com (URL placeholder)
        if (originalRequest.url.host == "example.com") {
            val newUrl = buildNewUrl(originalRequest.url, currentBaseUrl)
            
            if (newUrl != null) {
                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .build()
                
                try {
                    android.util.Log.d(TAG, "Redirecting request from ${originalRequest.url} to $newUrl")
                } catch (e: Exception) {
                    println("$TAG: Redirecting request from ${originalRequest.url} to $newUrl")
                }
                
                return chain.proceed(newRequest)
            }
        }
        
        return chain.proceed(originalRequest)
    }
    
    /**
     * Normaliza una URL evitando duplicación de protocolos
     */
    private fun normalizeUrl(url: String): String {
        val trimmedUrl = url.trim()
        
        // Si ya tiene protocolo, usarlo como está
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
        }
        
        // Si no tiene protocolo, agregar http://
        val urlWithProtocol = "http://$trimmedUrl"
        return if (urlWithProtocol.endsWith("/")) urlWithProtocol else "$urlWithProtocol/"
    }
    
    /**
     * Construye una nueva URL combinando la URL base con el path de la request original
     */
    private fun buildNewUrl(originalUrl: HttpUrl, baseUrl: String): HttpUrl? {
        return try {
            val normalizedBaseUrl = normalizeUrl(baseUrl)
            val baseHttpUrl = normalizedBaseUrl.toHttpUrl()
            
            // Preservar el path, query parameters, etc. de la request original
            baseHttpUrl.newBuilder()
                .encodedPath(originalUrl.encodedPath)
                .query(originalUrl.query)
                .fragment(originalUrl.fragment)
                .build()
                
        } catch (e: Exception) {
            try {
                android.util.Log.e(TAG, "Error building new URL from base: $baseUrl", e)
            } catch (logException: Exception) {
                println("$TAG: Error building new URL from base: $baseUrl - ${e.message}")
            }
            null
        }
    }
}