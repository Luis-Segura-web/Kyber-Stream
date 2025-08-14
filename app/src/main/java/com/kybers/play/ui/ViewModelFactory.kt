package com.kybers.play.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kybers.play.core.datastore.SettingsDataStore
import com.kybers.play.core.player.PlayerCoordinator
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.di.RepositoryFactory
import com.kybers.play.di.UserSession
import com.kybers.play.player.MediaManager
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.movies.MovieDetailsViewModel
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.movies.MoviesViewModel
// Legacy PlayerViewModel removed
import com.kybers.play.ui.series.SeriesDetailsViewModel
import com.kybers.play.ui.series.SeriesViewModel
import com.kybers.play.ui.settings.DynamicSettingsManager
import com.kybers.play.ui.settings.SettingsViewModel
import com.kybers.play.ui.theme.ThemeManager

// Legacy PlayerViewModel removed
// import com.kybers.play.ui.player.PlayerViewModel

class ContentViewModelFactory(
    private val application: Application,
    private val repositoryFactory: RepositoryFactory,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager,
    private val parentalControlManager: ParentalControlManager,
    private val userSession: UserSession,
    private val mediaManager: MediaManager,
    private val playerCoordinator: PlayerCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Asegurar referencia en MediaManager para compatibilidad
        mediaManager.playerCoordinator = playerCoordinator
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    repositoryFactory = repositoryFactory,
                    detailsRepository = detailsRepository,
                    externalApiService = externalApiService,
                    preferenceManager = preferenceManager,
                    userSession = userSession,
                    parentalControlManager = parentalControlManager
                ) as T
            }
            modelClass.isAssignableFrom(ChannelsViewModel::class.java) -> {
                ChannelsViewModel(
                    application, repositoryFactory, currentUser, preferenceManager, syncManager, parentalControlManager, mediaManager, playerCoordinator
                ) as T
            }
            modelClass.isAssignableFrom(MoviesViewModel::class.java) -> {
                MoviesViewModel(repositoryFactory, detailsRepository, syncManager, preferenceManager, currentUser, parentalControlManager) as T
            }
            modelClass.isAssignableFrom(SeriesViewModel::class.java) -> {
                SeriesViewModel(repositoryFactory, syncManager, preferenceManager, currentUser, parentalControlManager) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class in ContentViewModelFactory: ${modelClass.name}")
        }
    }
}

class MovieDetailsViewModelFactory(
    private val application: Application,
    private val repositoryFactory: RepositoryFactory,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val movieId: Int,
    private val mediaManager: MediaManager,
    private val playerCoordinator: PlayerCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        mediaManager.playerCoordinator = playerCoordinator
        if (modelClass.isAssignableFrom(MovieDetailsViewModel::class.java)) {
            return MovieDetailsViewModel(
                application = application,
                repositoryFactory = repositoryFactory,
                detailsRepository = detailsRepository,
                externalApiService = externalApiService,
                preferenceManager = preferenceManager,
                currentUser = currentUser,
                movieId = movieId,
                mediaManager = mediaManager,
                playerCoordinator = playerCoordinator
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class SeriesDetailsViewModelFactory(
    private val application: Application,
    private val preferenceManager: PreferenceManager,
    private val repositoryFactory: RepositoryFactory,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val currentUser: User,
    private val seriesId: Int,
    private val mediaManager: MediaManager,
    private val playerCoordinator: PlayerCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        mediaManager.playerCoordinator = playerCoordinator
        if (modelClass.isAssignableFrom(SeriesDetailsViewModel::class.java)) {
            return SeriesDetailsViewModel(
                application = application,
                repositoryFactory = repositoryFactory,
                detailsRepository = detailsRepository,
                externalApiService = externalApiService,
                preferenceManager = preferenceManager,
                currentUser = currentUser,
                seriesId = seriesId,
                mediaManager = mediaManager,
                playerCoordinator = playerCoordinator
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// PlayerViewModelFactory removed with legacy player

class SettingsViewModelFactory(
    private val context: Context,
    private val repositoryFactory: RepositoryFactory,
    private val preferenceManager: PreferenceManager,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val currentUser: User,
    private val parentalControlManager: ParentalControlManager,
    private val themeManager: ThemeManager,
    private val dynamicSettingsManager: DynamicSettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(context, repositoryFactory, preferenceManager, settingsDataStore, syncManager, currentUser, parentalControlManager, themeManager, dynamicSettingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
