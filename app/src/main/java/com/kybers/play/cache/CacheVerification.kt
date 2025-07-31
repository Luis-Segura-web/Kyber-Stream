package com.kybers.play.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * Simple cache verification utility for manual testing
 */
class CacheVerification(private val context: Context) {
    private val cacheManager = CacheManager(context)
    
    fun runBasicTests() {
        runBlocking {
            Log.d("CacheVerification", "=== Iniciando verificación del sistema de cache ===")
            
            try {
                // Test 1: Verificar inicialización del cache
                val stats = cacheManager.getCacheStats()
                Log.d("CacheVerification", "Cache inicializado - Tamaño máximo: ${stats.maxSize / (1024 * 1024)}MB")
                
                // Test 2: Probar guardar y recuperar contenido
                val testData = "Test content data".toByteArray()
                cacheManager.cacheContent(12345, testData, CacheManager.CachePriority.HIGH)
                
                val retrievedData = cacheManager.getCachedContent(12345)
                if (retrievedData != null) {
                    Log.d("CacheVerification", "✓ Cache funcionando correctamente - Datos recuperados")
                } else {
                    Log.e("CacheVerification", "✗ Error en cache - No se pudieron recuperar los datos")
                }
                
                // Test 3: Verificar estadísticas del cache
                val finalStats = cacheManager.getCacheStats()
                Log.d("CacheVerification", "Estadísticas finales - Archivos: ${finalStats.fileCount}, Uso: ${finalStats.usagePercentage}%")
                
                Log.d("CacheVerification", "=== Verificación completada ===")
                
            } catch (e: Exception) {
                Log.e("CacheVerification", "Error durante verificación", e)
            }
        }
    }
}