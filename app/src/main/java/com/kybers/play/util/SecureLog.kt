package com.kybers.play.util

import android.util.Log

/**
 * Utilidad de logging seguro que redacta automáticamente URLs y datos sensibles
 */
object SecureLog {
    
    private const val URL_REDACT_PATTERN = """(https?://[^/]+/).*""" 
    private const val CREDENTIALS_PATTERN = """(username|password|user|pass|token|key)=([^&\s]+)"""
    
    /**
     * Log de debug con redacción automática
     */
    fun d(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, redactSensitiveData(message))
        }
    }
    
    /**
     * Log de info con redacción automática
     */
    fun i(tag: String, message: String) {
        Log.i(tag, redactSensitiveData(message))
    }
    
    /**
     * Log de warning con redacción automática
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, redactSensitiveData(message), throwable)
    }
    
    /**
     * Log de error con redacción automática
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, redactSensitiveData(message), throwable)
    }
    
    /**
     * Log específico para URLs de streams con redacción
     */
    fun logStreamUrl(tag: String, url: String, action: String = "accessing") {
        val redactedUrl = redactStreamUrl(url)
        d(tag, "$action stream: $redactedUrl")
    }
    
    /**
     * Redacta datos sensibles de un mensaje
     */
    private fun redactSensitiveData(message: String): String {
        var result = message
        
        // Redactar URLs completas
        result = result.replace(Regex(URL_REDACT_PATTERN)) { matchResult ->
            "${matchResult.groupValues[1]}***"
        }
        
        // Redactar credenciales en query parameters
        result = result.replace(Regex(CREDENTIALS_PATTERN, RegexOption.IGNORE_CASE)) { matchResult ->
            "${matchResult.groupValues[1]}=***"
        }
        
        return result
    }
    
    /**
     * Redacta una URL de stream manteniendo información básica
     */
    fun redactStreamUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: "unknown"
            val scheme = uri.scheme ?: "unknown"
            val port = if (uri.port != -1) ":${uri.port}" else ""
            
            // Mantener solo scheme, host, port y el primer segmento del path
            val pathSegments = uri.pathSegments
            val firstSegment = if (pathSegments.isNotEmpty()) "/${pathSegments[0]}" else ""
            
            "$scheme://$host$port$firstSegment/***"
        } catch (e: Exception) {
            "***"
        }
    }
    
    /**
     * Redacta solo la parte después del último '/' en una URL
     */
    fun redactUrlAfterLastSlash(url: String): String {
        return url.replaceAfterLast("/", "***")
    }
    
    /**
     * Log de parámetros de Xtream con redacción automática
     */
    fun logXtreamParams(tag: String, baseUrl: String, username: String, action: String) {
        val redactedUrl = redactStreamUrl(baseUrl)
        d(tag, "Xtream $action - URL: $redactedUrl, User: $username")
    }
}