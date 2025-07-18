package com.kybers.play

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kybers.play.data.local.AppDatabase
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.work.CacheWorker // <--- ¡NUEVA IMPORTACIÓN!
import java.util.concurrent.TimeUnit // <--- ¡NUEVA IMPORTACIÓN!

class MainApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // ¡NUEVO! Programar el CacheWorker al iniciar la aplicación
        scheduleCacheWorker()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * Programa el CacheWorker para que se ejecute periódicamente cada 12 horas.
     * Utiliza ExistingPeriodicWorkPolicy.KEEP para evitar programar múltiples veces
     * si ya hay una tarea pendiente.
     */
    private fun scheduleCacheWorker() {
        val syncRequest = PeriodicWorkRequestBuilder<CacheWorker>(
            12, TimeUnit.HOURS // Ejecutar cada 12 horas
        )
            .setInitialDelay(10, TimeUnit.MINUTES) // Retraso inicial para no sobrecargar al inicio
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheSyncWorker", // Nombre único para la tarea
            ExistingPeriodicWorkPolicy.KEEP, // Mantener la tarea existente si ya está programada
            syncRequest
        )
        android.util.Log.d("MainApplication", "CacheWorker programado para ejecutarse cada 12 horas.")
    }
}

/**
 * Contenedor de Inyección de Dependencias a nivel de aplicación.
 */
class AppContainer(context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }

    // Los repositorios y los gestores son propiedades públicas del contenedor.
    val userRepository by lazy { UserRepository(database.userDao()) }
    val syncManager by lazy { SyncManager(context) }
    val preferenceManager by lazy { PreferenceManager(context) }

    /**
     * Crea una instancia de ContentRepository para una URL base específica.
     * Esto permite conectarse a diferentes servidores IPTV.
     */
    fun createContentRepository(baseUrl: String): ContentRepository {
        val apiService = RetrofitClient.create(baseUrl)
        return ContentRepository(
            apiService = apiService,
            liveStreamDao = database.liveStreamDao(),
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao()
        )
    }
}
