package com.kybers.play.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.data.repository.UserRepository
import com.kybers.play.di.RepositoryFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Representa el estado de la navegación principal
// Mantiene al usuario y repositorios cargados

data class MainUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val vodRepository: VodRepository? = null,
    val liveRepository: LiveRepository? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val repositoryFactory: RepositoryFactory,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: Int = savedStateHandle.get<Int>("userId") ?: 0

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = userRepository.getUserById(userId)
            val vodRepo = user?.let { repositoryFactory.createVodRepository(it.url) }
            val liveRepo = user?.let { repositoryFactory.createLiveRepository(it.url) }
            _uiState.value = MainUiState(
                isLoading = false,
                user = user,
                vodRepository = vodRepo,
                liveRepository = liveRepo
            )
        }
    }
}
