package com.kybers.play

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.kybers.play.data.local.AppDatabase
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.data.repository.UserRepository

// ¡CORREGIDO! La forma correcta de proveer la configuración de WorkManager
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

class AppContainer(context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }
    val userRepository by lazy { UserRepository(database.userDao()) }

    fun createContentRepository(baseUrl: String): ContentRepository {
        val apiService = RetrofitClient.create(baseUrl)
        // ¡CORREGIDO! Pasamos los DAOs en el orden correcto que espera el constructor
        return ContentRepository(
            apiService = apiService,
            liveStreamDao = database.liveStreamDao(),
            movieDao = database.movieDao(),
            seriesDao = database.seriesDao()
        )
    }
}
