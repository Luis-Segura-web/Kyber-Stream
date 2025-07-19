package com.kybers.play.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.details.MovieDetailsViewModel // ¡IMPORTACIÓN CORREGIDA! Apunta al paquete 'details'.
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.login.LoginViewModel
import com.kybers.play.ui.movies.MoviesViewModel
import com.kybers.play.ui.player.PlayerViewModel
import com.kybers.play.ui.sync.SyncViewModel

/**
 * Fábrica para el LoginViewModel.
 * No necesita cambios, pero se queda para mantener la estructura.
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
 * Fábrica para los ViewModels que dependen del contenido principal.
 * ¡IMPORTANTE! Hemos quitado la responsabilidad de crear MovieDetailsViewModel de aquí.
 * Cada ViewModel complejo debe tener su propia fábrica para mayor claridad.
 */
class ContentViewModelFactory(
    private val application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(contentRepository, currentUser) as T
            }
            modelClass.isAssignableFrom(ChannelsViewModel::class.java) -> {
                ChannelsViewModel(application, contentRepository, currentUser, preferenceManager, syncManager) as T
            }
            modelClass.isAssignableFrom(MoviesViewModel::class.java) -> {
                MoviesViewModel(contentRepository, syncManager, preferenceManager, currentUser) as T
            }
            // El caso de MovieDetailsViewModel se ha movido a su propia fábrica.
            else -> throw IllegalArgumentException("Unknown ViewModel class in ContentViewModelFactory: ${modelClass.name}")
        }
    }
}

/**
 * ¡NUEVA FÁBRICA!
 * Esta es una fábrica dedicada exclusivamente para crear el MovieDetailsViewModel.
 * Es la forma correcta de hacerlo, ya que nos permite pasarle dependencias específicas
 * como el 'movieId' que necesita para saber qué película mostrar.
 */
class MovieDetailsViewModelFactory(
    private val application: Application,
    private val contentRepository: ContentRepository,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val movieId: Int // Dependencia específica para este ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovieDetailsViewModel::class.java)) {
            return MovieDetailsViewModel(
                application,
                contentRepository,
                preferenceManager,
                currentUser,
                movieId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


/**
 * Fábrica para el PlayerViewModel. Sin cambios.
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

/**
 * Fábrica para el SyncViewModel. Sin cambios.
 */
class SyncViewModelFactory(
    private val contentRepository: ContentRepository,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SyncViewModel(contentRepository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
