package com.kybers.play

import android.app.Application
import android.content.Context
import android.util.Log
import java.util.Locale
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.kybers.play.BuildConfig
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
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.work.CacheWorker
import com.kybers.play.cache.CacheManager
import com.kybers.play.cache.PreloadingManager
import com.kybers.play.cache.StreamPreloader
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class MainApplication : Application(), androidx.work.Configuration.Provider, ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        setDefaultLocale()
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
            .apply {
                // Only enable debug logging in debug builds to reduce log noise
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    private fun scheduleCacheWorker() {
        val userFrequency = container.preferenceManager.getSyncFrequency()
        
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

class AppContainer(private val context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }
    
    // HTTP Client for cache operations
    private val httpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Cache components
    val cacheManager by lazy { CacheManager(context) }
    val streamPreloader by lazy { StreamPreloader(httpClient, cacheManager.getCacheDir()) }
    
    // --- ¡CAMBIO! Hacemos público el servicio de TMDB ---
    val tmdbApiService: ExternalApiService by lazy { ExternalApiRetrofitClient.createTMDbService() }

    val userRepository by lazy { UserRepository(database.userDao()) }
    val preferenceManager by lazy { PreferenceManager(context) }
    val syncManager by lazy { SyncManager(context, preferenceManager) }
    val parentalControlManager by lazy { ParentalControlManager(preferenceManager) }

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
    
    fun createPreloadingManager(
        vodRepository: VodRepository,
        liveRepository: LiveRepository,
        user: com.kybers.play.data.local.model.User
    ): com.kybers.play.cache.PreloadingManager {
        return com.kybers.play.cache.PreloadingManager(
            context = context,
            cacheManager = cacheManager,
            streamPreloader = streamPreloader,
            vodRepository = vodRepository,
            liveRepository = liveRepository,
            user = user,
            preferenceManager = preferenceManager
        )
    }
}
