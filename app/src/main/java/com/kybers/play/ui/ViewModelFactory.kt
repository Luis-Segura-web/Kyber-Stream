package com.kybers.play.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kybers.play.data.local.model.User
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.ui.channels.ChannelsViewModel // ¡IMPORTACIÓN CORREGIDA!
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.login.LoginViewModel
import com.kybers.play.ui.player.PlayerViewModel // ¡IMPORTACIÓN CORREGIDA!

/**
 * Fábrica para el LoginViewModel, que solo necesita el UserRepository.
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
 * Fábrica para todos los ViewModels que dependen del contenido (Home, Canales, etc.).
 * Ahora también necesita la instancia de Application para los ViewModels que gestionan un reproductor.
 */
class ContentViewModelFactory(
    private val application: Application,
    private val contentRepository: ContentRepository,
    private val currentUser: User
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(contentRepository, currentUser) as T
            }
            modelClass.isAssignableFrom(ChannelsViewModel::class.java) -> { // ¡CORREGIDO!
                ChannelsViewModel(application, contentRepository, currentUser) as T
            }
            // Aquí añadiremos los ViewModels de Películas y Series en el futuro
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * Fábrica para el PlayerViewModel, que solo necesita la Application.
 * (Esta fábrica es la que usaría PlayerActivity si la mantienes)
 */
class PlayerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) { // ¡CORREGIDO!
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
