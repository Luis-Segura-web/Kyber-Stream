package com.kybers.play

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.kybers.play.data.local.AppDatabase
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.data.repository.UserRepository

class MainApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

/**
 * Dependency Injection container at the application level.
 */
class AppContainer(context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }

    // The repositories and the sync manager are now public properties of the container.
    val userRepository by lazy { UserRepository(database.userDao()) }
    val syncManager by lazy { SyncManager(context) } // ¡NUEVO! Se crea la instancia del SyncManager.

    /**
     * Creates a ContentRepository instance for a specific base URL.
     * This allows connecting to different IPTV servers.
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
