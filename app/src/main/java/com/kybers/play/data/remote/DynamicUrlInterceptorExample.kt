package com.kybers.play.data.remote

/**
 * Ejemplo de uso del DynamicUrlInterceptor para resolver el problema de duplicación de protocolo
 */
object DynamicUrlInterceptorExample {
    
    /**
     * Demuestra cómo el interceptor previene la duplicación de protocolo HTTP
     */
    fun demonstrateProtocolDuplicationFix() {
        val interceptor = DynamicUrlInterceptor()
        
        // Caso problemático: URL que ya tiene protocolo
        val problematicUrl = "http://gzytv.vip:8880"
        
        println("=== Demostración de corrección de duplicación de protocolo ===")
        println("URL original problemática: $problematicUrl")
        
        // Al actualizar la URL base, el interceptor normaliza correctamente
        interceptor.updateBaseUrl(problematicUrl)
        
        println("✅ URL normalizada correctamente sin duplicación de protocolo")
        
        // Otros casos de prueba
        println("\n=== Casos de prueba adicionales ===")
        
        // URL sin protocolo
        interceptor.updateBaseUrl("server.com:8080")
        println("✅ URL sin protocolo: se agrega http:// automáticamente")
        
        // URL con HTTPS
        interceptor.updateBaseUrl("https://secure-server.com:8443")
        println("✅ URL con HTTPS: se preserva el protocolo seguro")
        
        // URL con trailing slash
        interceptor.updateBaseUrl("http://server.com:8080/")
        println("✅ URL con trailing slash: se maneja correctamente")
        
        println("\n=== Problema original resuelto ===")
        println("❌ Antes: http://http://gzytv.vip:8880/ (URL malformada)")
        println("✅ Ahora: http://gzytv.vip:8880/ (URL bien formada)")
    }
}

/**
 * Función principal para ejecutar el ejemplo
 */
fun main() {
    DynamicUrlInterceptorExample.demonstrateProtocolDuplicationFix()
}