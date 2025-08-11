package com.kybers.play.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.di.UserSession
import com.kybers.play.player.MediaManager
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.movies.MovieDetailsViewModel
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.login.LoginViewModel
import com.kybers.play.ui.movies.MoviesViewModel
import com.kybers.play.ui.player.PlayerViewModel
import com.kybers.play.ui.series.SeriesDetailsViewModel
import com.kybers.play.ui.series.SeriesViewModel
import com.kybers.play.ui.settings.DynamicSettingsManager
import com.kybers.play.ui.settings.SettingsViewModel
import com.kybers.play.ui.sync.SyncViewModel
import com.kybers.play.ui.main.MainViewModel
import com.kybers.play.ui.theme.ThemeManager

class ContentViewModelFactory(
    private val application: Application,
    private val vodRepository: VodRepository,
    private val liveRepository: LiveRepository,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager,
    private val parentalControlManager: ParentalControlManager,
    private val userSession: UserSession,
    private val mediaManager: MediaManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    vodRepository = vodRepository,
                    liveRepository = liveRepository,
                    detailsRepository = detailsRepository,
                    externalApiService = externalApiService,
                    preferenceManager = preferenceManager,
                    userSession = userSession,
                    parentalControlManager = parentalControlManager
                ) as T
            }
            modelClass.isAssignableFrom(ChannelsViewModel::class.java) -> {
                ChannelsViewModel(application, liveRepository, currentUser, preferenceManager, syncManager, parentalControlManager, mediaManager) as T
            }
            modelClass.isAssignableFrom(MoviesViewModel::class.java) -> {
                MoviesViewModel(vodRepository, detailsRepository, syncManager, preferenceManager, currentUser, parentalControlManager) as T
            }
            modelClass.isAssignableFrom(SeriesViewModel::class.java) -> {
                SeriesViewModel(vodRepository, syncManager, preferenceManager, currentUser, parentalControlManager) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class in ContentViewModelFactory: ${modelClass.name}")
        }
    }
}

class MovieDetailsViewModelFactory(
    private val application: Application,
    private val vodRepository: VodRepository,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val movieId: Int,
    private val mediaManager: MediaManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovieDetailsViewModel::class.java)) {
            return MovieDetailsViewModel(
                application = application,
                vodRepository = vodRepository,
                detailsRepository = detailsRepository,
                externalApiService = externalApiService,
                preferenceManager = preferenceManager,
                currentUser = currentUser,
                movieId = movieId,
                mediaManager = mediaManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class SeriesDetailsViewModelFactory(
    private val application: Application,
    private val preferenceManager: PreferenceManager,
    private val vodRepository: VodRepository,
    private val detailsRepository: DetailsRepository,
    private val externalApiService: ExternalApiService,
    private val currentUser: User,
    private val seriesId: Int,
    private val mediaManager: MediaManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SeriesDetailsViewModel::class.java)) {
            return SeriesDetailsViewModel(
                application = application,
                preferenceManager = preferenceManager,
                vodRepository = vodRepository,
                detailsRepository = detailsRepository,
                externalApiService = externalApiService,
                currentUser = currentUser,
                seriesId = seriesId,
                mediaManager = mediaManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


class PlayerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// --- ¡NUEVA FÁBRICA AÑADIDA! ---
class SettingsViewModelFactory(
    private val context: Context,
    private val liveRepository: LiveRepository,
    private val vodRepository: VodRepository,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager,
    private val currentUser: User,
    private val parentalControlManager: ParentalControlManager,
    private val themeManager: ThemeManager,
    private val dynamicSettingsManager: DynamicSettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(context, liveRepository, vodRepository, preferenceManager, syncManager, currentUser, parentalControlManager, themeManager, dynamicSettingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
