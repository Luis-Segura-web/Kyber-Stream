package com.kybers.play.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.AppContainer
import com.kybers.play.data.local.model.User
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Representa el estado de la navegación principal
// Mantiene al usuario y repositorios cargados

data class MainUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val vodRepository: VodRepository? = null,
    val liveRepository: LiveRepository? = null
)

class MainViewModel(
    private val appContainer: AppContainer,
    private val userId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = appContainer.userRepository.getUserById(userId)
            val vodRepo = user?.let { appContainer.createVodRepository(it.url) }
            val liveRepo = user?.let { appContainer.createLiveRepository(it.url) }
            _uiState.value = MainUiState(
                isLoading = false,
                user = user,
                vodRepository = vodRepo,
                liveRepository = liveRepo
            )
        }
    }
}
