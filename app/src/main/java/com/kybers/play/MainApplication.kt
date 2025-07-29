package com.kybers.play

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
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
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.work.CacheWorker
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        setDefaultLocale()
        container = AppContainer(this)
        scheduleCacheWorker()
    }

    private fun setDefaultLocale() {
        val locale = Locale("es", "ES")
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
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
            .logger(DebugLogger())
            .build()
    }

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

class AppContainer(context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }
    // --- ¡CAMBIO! Hacemos público el servicio de TMDB ---
    val tmdbApiService: ExternalApiService by lazy { ExternalApiRetrofitClient.createTMDbService() }

    val userRepository by lazy { UserRepository(database.userDao()) }
    val syncManager by lazy { SyncManager(context) }
    val preferenceManager by lazy { PreferenceManager(context) }

    val detailsRepository by lazy {
        DetailsRepository(
            tmdbApiService = tmdbApiService,
            movieDetailsCacheDao = database.movieDetailsCacheDao(),
            seriesDetailsCacheDao = database.seriesDetailsCacheDao(),
            actorDetailsCacheDao = database.actorDetailsCacheDao(),
            episodeDetailsCacheDao = database.episodeDetailsCacheDao()
        )
    }

    fun createLiveRepository(baseUrl: String): LiveRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return LiveRepository(
            xtreamApiService = xtreamApiService,
            liveStreamDao = database.liveStreamDao(),
            epgEventDao = database.epgEventDao(),
            categoryCacheDao = database.categoryCacheDao()
        )
    }

    fun createVodRepository(baseUrl: String): VodRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return VodRepository(
            xtreamApiService = xtreamApiService,
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao(),
            episodeDao = database.episodeDao(),
            categoryCacheDao = database.categoryCacheDao()
        )
    }
}
