package com.kybers.play

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.kybers.play.data.local.AppDatabase
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.ExternalApiRetrofitClient
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.work.CacheWorker
import java.util.concurrent.TimeUnit

/**
 * Clase principal de la aplicación.
 * Responsable de inicializar dependencias globales y el contenedor de dependencias (AppContainer).
 */
class MainApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    // Contenedor de dependencias disponible en toda la aplicación.
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

    /**
     * Crea una instancia única y optimizada de ImageLoader para Coil.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Usa hasta el 25% de la RAM disponible
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // Usa hasta el 2% del almacenamiento
                    .build()
            }
            .respectCacheHeaders(false)
            .logger(DebugLogger())
            .build()
    }

    /**
     * Programa el Worker en segundo plano para sincronizar el contenido periódicamente.
     */
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
 * Contenedor de dependencias para la aplicación.
 * Gestiona la creación y provisión de instancias de repositorios y otros servicios.
 */
class AppContainer(context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }
    private val tmdbApiService by lazy { ExternalApiRetrofitClient.createTMDbService() }

    val userRepository by lazy { UserRepository(database.userDao()) }
    val syncManager by lazy { SyncManager(context) }
    val preferenceManager by lazy { PreferenceManager(context) }

    val detailsRepository by lazy {
        DetailsRepository(
            tmdbApiService = tmdbApiService,
            movieDetailsCacheDao = database.movieDetailsCacheDao()
        )
    }

    fun createLiveRepository(baseUrl: String): LiveRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return LiveRepository(
            xtreamApiService = xtreamApiService,
            liveStreamDao = database.liveStreamDao(),
            epgEventDao = database.epgEventDao()
        )
    }

    /**
     * FÁBRICA para crear un VodRepository.
     *
     * @param baseUrl La URL del servidor del usuario.
     * @return Una nueva instancia de [VodRepository].
     */
    fun createVodRepository(baseUrl: String): VodRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return VodRepository(
            xtreamApiService = xtreamApiService,
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao(),
            // --- ¡CORRECCIÓN! ---
            // Añadimos el episodeDao que faltaba al constructor.
            episodeDao = database.episodeDao()
        )
    }
}
