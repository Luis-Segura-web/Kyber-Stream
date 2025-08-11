package com.kybers.play

import android.app.Application
import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.work.CacheWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), androidx.work.Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    // Temporary compatibility layer for gradual migration
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        setDefaultLocale()
        
        // Create compatibility container after Hilt injection
        container = AppContainer(this)
        
        scheduleCacheWorker()
    }

    private fun setDefaultLocale() {
        val locale = Locale.forLanguageTag("es-MX")
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        createConfigurationContext(config)
    }

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .apply {
                // Only enable debug logging in debug builds to reduce log noise
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    private fun scheduleCacheWorker() {
        val userFrequency = preferenceManager.getSyncFrequency()
        
        if (userFrequency == 0) {
            Log.d("MainApplication", "Sincronización automática deshabilitada por el usuario")
            return
        }
        
        val syncRequest = PeriodicWorkRequestBuilder<CacheWorker>(
            userFrequency.toLong(), TimeUnit.HOURS
        )
            .setInitialDelay(10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.d("MainApplication", "CacheWorker programado para ejecutarse cada $userFrequency horas según configuración del usuario")
    }
}

// Temporary compatibility layer - will be removed in future steps
class AppContainer(private val context: Context) {

    private val database by lazy { com.kybers.play.data.local.AppDatabase.getDatabase(context) }
    
    val tmdbApiService by lazy { com.kybers.play.data.remote.ExternalApiRetrofitClient.createTMDbService() }

    val userRepository by lazy { com.kybers.play.data.repository.UserRepository(database.userDao()) }
    val preferenceManager by lazy { com.kybers.play.data.preferences.PreferenceManager(context) }
    val syncManager by lazy { com.kybers.play.data.preferences.SyncManager(context, preferenceManager) }
    val parentalControlManager by lazy { com.kybers.play.ui.components.ParentalControlManager(preferenceManager) }

    val detailsRepository by lazy {
        com.kybers.play.data.repository.DetailsRepository(
            tmdbApiService = tmdbApiService,
            movieDetailsCacheDao = database.movieDetailsCacheDao(),
            seriesDetailsCacheDao = database.seriesDetailsCacheDao(),
            actorDetailsCacheDao = database.actorDetailsCacheDao(),
            episodeDetailsCacheDao = database.episodeDetailsCacheDao()
        )
    }

    fun createLiveRepository(baseUrl: String): com.kybers.play.data.repository.LiveRepository {
        val xtreamApiService = com.kybers.play.data.remote.RetrofitClient.create(baseUrl)
        return com.kybers.play.data.repository.LiveRepository(
            xtreamApiService = xtreamApiService,
            liveStreamDao = database.liveStreamDao(),
            epgEventDao = database.epgEventDao(),
            categoryCacheDao = database.categoryCacheDao()
        )
    }

    fun createVodRepository(baseUrl: String): com.kybers.play.data.repository.VodRepository {
        val xtreamApiService = com.kybers.play.data.remote.RetrofitClient.create(baseUrl)
        return com.kybers.play.data.repository.VodRepository(
            xtreamApiService = xtreamApiService,
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao(),
            episodeDao = database.episodeDao(),
            categoryCacheDao = database.categoryCacheDao()
        )
    }
}
