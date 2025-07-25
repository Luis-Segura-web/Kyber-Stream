package com.kybers.play.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.details.MovieDetailsViewModel
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.login.LoginViewModel
import com.kybers.play.ui.movies.MoviesViewModel
import com.kybers.play.ui.player.PlayerViewModel
import com.kybers.play.ui.series.SeriesDetailsViewModel
import com.kybers.play.ui.series.SeriesViewModel
import com.kybers.play.ui.sync.SyncViewModel

/**
 * Fábrica para el LoginViewModel.
 */
class LoginViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/**
 * --- ¡FÁBRICA ACTUALIZADA! ---
 * Fábrica para los ViewModels de contenido principal (Home, Canales, Películas, Series).
 */
class ContentViewModelFactory(
    private val application: Application,
    private val vodRepository: VodRepository,
    private val liveRepository: LiveRepository,
    private val detailsRepository: DetailsRepository,
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(vodRepository, currentUser) as T
            }
            modelClass.isAssignableFrom(ChannelsViewModel::class.java) -> {
                ChannelsViewModel(application, liveRepository, currentUser, preferenceManager, syncManager) as T
            }
            modelClass.isAssignableFrom(MoviesViewModel::class.java) -> {
                MoviesViewModel(vodRepository, detailsRepository, syncManager, preferenceManager, currentUser) as T
            }
            modelClass.isAssignableFrom(SeriesViewModel::class.java) -> {
                SeriesViewModel(vodRepository, syncManager, preferenceManager, currentUser) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class in ContentViewModelFactory: ${modelClass.name}")
        }
    }
}

/**
 * Fábrica dedicada para crear el MovieDetailsViewModel.
 */
class MovieDetailsViewModelFactory(
    private val application: Application,
    private val vodRepository: VodRepository,
    private val detailsRepository: DetailsRepository,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val movieId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovieDetailsViewModel::class.java)) {
            return MovieDetailsViewModel(
                application,
                vodRepository,
                detailsRepository,
                preferenceManager,
                currentUser,
                movieId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/**
 * --- ¡FÁBRICA CORREGIDA! ---
 * Fábrica dedicada exclusivamente a crear el SeriesDetailsViewModel.
 * Ahora acepta `application` y `preferenceManager` para pasárselos al ViewModel.
 */
class SeriesDetailsViewModelFactory(
    // --- ¡NUEVOS PARÁMETROS! ---
    private val application: Application,
    private val preferenceManager: PreferenceManager,
    // ---
    private val vodRepository: VodRepository,
    private val currentUser: User,
    private val seriesId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SeriesDetailsViewModel::class.java)) {
            return SeriesDetailsViewModel(
                // --- ¡SE PASAN LOS NUEVOS PARÁMETROS! ---
                application = application,
                preferenceManager = preferenceManager,
                // ---
                vodRepository = vodRepository,
                currentUser = currentUser,
                seriesId = seriesId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


/**
 * Fábrica para el SyncViewModel.
 */
class SyncViewModelFactory(
    private val liveRepository: LiveRepository,
    private val vodRepository: VodRepository,
    private val syncManager: SyncManager,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SyncViewModel(liveRepository, vodRepository, syncManager, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


/**
 * Fábrica para el PlayerViewModel.
 */
class PlayerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
