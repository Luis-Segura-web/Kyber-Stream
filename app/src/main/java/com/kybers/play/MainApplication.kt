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
 *
 * Implementa ImageLoaderFactory para proporcionar una instancia única y optimizada de Coil
 * para la carga de imágenes en toda la app.
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
     * Configura cachés de memoria y disco para un rendimiento eficiente en la carga de imágenes.
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
 * Este es el núcleo de nuestra inyección de dependencias manual.
 */
class AppContainer(context: Context) {

    // --- DEPENDENCIAS BASE (sin cambios) ---
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val tmdbApiService by lazy { ExternalApiRetrofitClient.createTMDbService() }

    // --- REPOSITORIOS PRINCIPALES (sin cambios) ---
    val userRepository by lazy { UserRepository(database.userDao()) }
    val syncManager by lazy { SyncManager(context) }
    val preferenceManager by lazy { PreferenceManager(context) }

    // --- NUEVOS REPOSITORIOS MODULARIZADOS ---

    /**
     * Repositorio para detalles de TMDb.
     * Se puede crear una sola vez como un singleton (`lazy`) ya que no depende
     * de la URL específica del usuario.
     */
    val detailsRepository by lazy {
        DetailsRepository(
            tmdbApiService = tmdbApiService,
            movieDetailsCacheDao = database.movieDetailsCacheDao()
        )
    }

    /**
     * FÁBRICA para crear un LiveRepository.
     * Se necesita una fábrica porque este repositorio depende de la 'baseUrl'
     * específica del perfil del usuario, la cual puede cambiar.
     *
     * @param baseUrl La URL del servidor del usuario.
     * @return Una nueva instancia de [LiveRepository].
     */
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
     * También necesita la 'baseUrl' del usuario para crear su propia instancia de la API.
     *
     * @param baseUrl La URL del servidor del usuario.
     * @return Una nueva instancia de [VodRepository].
     */
    fun createVodRepository(baseUrl: String): VodRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return VodRepository(
            xtreamApiService = xtreamApiService,
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao()
        )
    }
}
