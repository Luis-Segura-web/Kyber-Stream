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
import com.kybers.play.work.CacheWorker
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleCacheWorker()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun scheduleCacheWorker() {
        val syncRequest = PeriodicWorkRequestBuilder<CacheWorker>(
            12, TimeUnit.HOURS
        )
            .setInitialDelay(10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
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

    val userRepository by lazy { UserRepository(database.userDao()) }
    val syncManager by lazy { SyncManager(context) }
    val preferenceManager by lazy { PreferenceManager(context) }

    /**
     * ¡CORREGIDO! Se pasa el parámetro `baseUrl` que ahora es requerido por el constructor
     * de ContentRepository, solucionando el error de compilación.
     */
    fun createContentRepository(baseUrl: String): ContentRepository {
        val apiService = RetrofitClient.create(baseUrl)
        return ContentRepository(
            apiService = apiService,
            liveStreamDao = database.liveStreamDao(),
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao(),
            epgEventDao = database.epgEventDao(),
            baseUrl = baseUrl // <-- ¡LA LÍNEA QUE FALTABA!
        )
    }
}
