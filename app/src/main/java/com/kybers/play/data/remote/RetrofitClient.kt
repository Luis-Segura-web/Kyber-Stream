package com.kybers.play.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Un objeto Singleton que se encarga de crear y configurar el cliente de Retrofit.
 * Nos proporciona una instancia del servicio de API para una URL base específica.
 */
object RetrofitClient {

    // Configuración de Moshi, nuestro parser de JSON.
    // Añadimos el adaptador de Kotlin para que entienda las data classes de Kotlin.
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Interceptor para manejar URLs dinámicas de Xtream Codes
    private val dynamicUrlInterceptor = DynamicUrlInterceptor()

    // Configuración del cliente HTTP (OkHttp).
    private val okHttpClient: OkHttpClient by lazy {
        // El HttpLoggingInterceptor nos permite ver las llamadas de red en el Logcat.
        // Es extremadamente útil para depurar.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Muestra todo: headers, body, etc.
        }

        OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor) // Agregar interceptor dinámico primero
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Aumentamos el tiempo de espera
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Crea una instancia de nuestro XtreamApiService para una URL base específica.
     * Esto nos permite conectarnos a cualquier servidor IPTV que el usuario configure.
     *
     * @param baseUrl La URL del servidor del usuario (ej. "http://servidor.com:8080").
     * @return Una instancia de XtreamApiService lista para usar.
     */
    fun create(baseUrl: String): XtreamApiService {
        // Actualizar la URL base en el interceptor dinámico
        dynamicUrlInterceptor.updateBaseUrl(baseUrl)
        
        val retrofit = Retrofit.Builder()
            .baseUrl("http://example.com/") // URL placeholder que será interceptada
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(XtreamApiService::class.java)
    }
}
