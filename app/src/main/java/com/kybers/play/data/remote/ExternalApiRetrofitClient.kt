package com.kybers.play.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Objeto Singleton para crear clientes de Retrofit para las APIs externas.
 * Separado del cliente de Xtream para manejar diferentes URLs base.
 */
object ExternalApiRetrofitClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val rateLimitInterceptor = RateLimitInterceptor()
        
        OkHttpClient.Builder()
            .addInterceptor(rateLimitInterceptor) // Add rate limiting first
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Crea y devuelve una instancia del servicio para la API de TMDb.
     */
    fun createTMDbService(): ExternalApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(ExternalApiService::class.java)
    }

    /**
     * Crea y devuelve una instancia del servicio para la API de OMDb.
     */
    fun createOMDbService(): ExternalApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(ExternalApiService::class.java)
    }
}
