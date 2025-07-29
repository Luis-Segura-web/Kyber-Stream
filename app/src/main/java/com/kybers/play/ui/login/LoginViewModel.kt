package com.kybers.play.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.repository.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Represents the possible states of the Login screen UI.
 * This sealed interface makes the UI logic explicit and robust.
 */
sealed interface LoginUiState {
    /**
     * The initial state while checking for existing users in the database.
     */
    object Loading : LoginUiState

    /**
     * The state when at least one user profile exists, showing the list.
     * @param users The list of existing user profiles.
     */
    data class UserList(val users: List<User>) : LoginUiState

    /**
     * The state when no user profiles are found, prompting for registration.
     */
    object ShowRegistration : LoginUiState
}

/**
 * ViewModel for the Login/Profile Selection screen.
 * It is responsible for the business logic and preparing the data for the UI.
 *
 * @param userRepository The repository to access user data.
 */
class LoginViewModel(private val userRepository: UserRepository) : ViewModel() {

    /**
     * A StateFlow that emits the current state of the login screen.
     * The UI will observe this Flow to automatically update when the state changes.
     * It starts in a Loading state, then transitions to either UserList or ShowRegistration.
     */
    val uiState: StateFlow<LoginUiState> = userRepository.allUsers
        .map { users ->
            if (users.isEmpty()) {
                LoginUiState.ShowRegistration
            } else {
                LoginUiState.UserList(users)
            }
        }
        .stateIn(
            scope = viewModelScope,
            // We start with a 5-second timeout. If no collector is active, the upstream flow is cancelled.
            started = SharingStarted.WhileSubscribed(5000),
            // The initial state is Loading.
            initialValue = LoginUiState.Loading
        )

    /**
     * Saves a new user profile to the database.
     * It launches a coroutine in the viewModelScope to perform the insertion
     * on a background thread, without blocking the UI.
     *
     * @param profileName The name for the new profile.
     * @param url The server URL.
     * @param username The username for authentication.
     * @param password The password for authentication.
     */
    fun addUser(profileName: String, url: String, username: String, password: String) {
        viewModelScope.launch {
            val newUser = User(
                profileName = profileName,
                url = url,
                username = username,
                password = password
            )
            userRepository.insert(newUser)
        }
    }

    /**
     * Deletes a user profile from the database.
     *
     * @param user The user to be deleted.
     */
    fun deleteUser(user: User) {
        viewModelScope.launch {
            userRepository.delete(user)
        }
    }
}
