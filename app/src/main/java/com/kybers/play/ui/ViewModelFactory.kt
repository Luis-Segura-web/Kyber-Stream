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
import com.kybers.play.ui.sync.SyncViewModel

/**
 * Fábrica para el LoginViewModel. No cambia, ya que solo depende de UserRepository.
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
 * Fábrica para los ViewModels de la pantalla principal que dependen del contenido.
 * Ahora inyecta los repositorios modulares específicos que cada ViewModel necesita.
 */
class ContentViewModelFactory(
    private val application: Application,
    private val vodRepository: VodRepository,
    private val liveRepository: LiveRepository,
    private val detailsRepository: DetailsRepository, // Añadido para futuros usos
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                // HomeViewModel ahora usa VodRepository para obtener películas y series.
                HomeViewModel(vodRepository, currentUser) as T
            }
            modelClass.isAssignableFrom(ChannelsViewModel::class.java) -> {
                // ChannelsViewModel usa LiveRepository.
                ChannelsViewModel(application, liveRepository, currentUser, preferenceManager, syncManager) as T
            }
            modelClass.isAssignableFrom(MoviesViewModel::class.java) -> {
                // MoviesViewModel usa VodRepository y DetailsRepository.
                MoviesViewModel(vodRepository, detailsRepository, syncManager, preferenceManager, currentUser) as T
            }
            // Aquí se añadirían otros ViewModels como SeriesViewModel en el futuro.
            else -> throw IllegalArgumentException("Unknown ViewModel class in ContentViewModelFactory: ${modelClass.name}")
        }
    }
}

/**
 * --- ¡FÁBRICA ACTUALIZADA! ---
 * Fábrica dedicada para crear el MovieDetailsViewModel.
 * Ahora depende de VodRepository y DetailsRepository.
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
 * --- ¡FÁBRICA ACTUALIZADA! ---
 * Fábrica para el SyncViewModel.
 * Ahora recibe los repositorios de Live y VOD para realizar la sincronización.
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
 * Fábrica para el PlayerViewModel (sin cambios).
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
