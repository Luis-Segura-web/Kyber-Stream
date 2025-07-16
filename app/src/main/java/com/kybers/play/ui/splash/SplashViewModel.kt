package com.kybers.play.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Define los posibles destinos de navegación desde el Splash
sealed class SplashNavigationState {
    object Loading : SplashNavigationState()
    object GoToLogin : SplashNavigationState()
    data class GoToMain(val userId: Int) : SplashNavigationState()
}

class SplashViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _navigationState = MutableStateFlow<SplashNavigationState>(SplashNavigationState.Loading)
    val navigationState: StateFlow<SplashNavigationState> = _navigationState.asStateFlow()

    init {
        // Inicia la lógica de decisión al crear el ViewModel
        viewModelScope.launch {
            // Un pequeño retraso para que el logo sea visible y la app se sienta fluida
            delay(2000)

            // Obtenemos la lista de usuarios de la base de datos
            val users = userRepository.allUsers.first() // .first() toma la primera lista emitida por el Flow

            if (users.isEmpty()) {
                // Si no hay usuarios, navegamos al Login
                _navigationState.value = SplashNavigationState.GoToLogin
            } else {
                // Si hay usuarios, tomamos el primero y navegamos a la pantalla principal
                _navigationState.value = SplashNavigationState.GoToMain(users.first().id)
            }
        }
    }
}
