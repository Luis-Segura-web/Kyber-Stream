package com.kybers.play.di

import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CurrentUser

/**
 * UserSession provides access to the current logged-in user.
 * This is a singleton that manages the current user state.
 */
class UserSession(
    private val userRepository: UserRepository,
    private val preferenceManager: PreferenceManager
) {
    @Volatile
    private var cachedUser: User? = null
    
    /**
     * Gets the current user synchronously.
     * This method caches the user to avoid repeated database calls.
     */
    fun getCurrentUser(): User? {
        if (cachedUser == null) {
            val currentUserId = preferenceManager.getCurrentUserId()
            if (currentUserId != -1) {
                // Load user synchronously using runBlocking
                // This is acceptable since it's called during DI setup
                cachedUser = runBlocking {
                    userRepository.getUserById(currentUserId)
                }
            } else {
                // Fallback: get the first user available
                cachedUser = runBlocking {
                    userRepository.allUsers.first().firstOrNull()
                }
            }
        }
        return cachedUser
    }
    
    /**
     * Sets the current user and updates preferences
     */
    fun setCurrentUser(user: User) {
        cachedUser = user
        preferenceManager.saveCurrentUserId(user.id)
    }
    
    /**
     * Clears the current user session
     */
    fun clearSession() {
        cachedUser = null
        preferenceManager.clearCurrentUserId()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object UserModule {

    @Provides
    @Singleton
    fun provideUserSession(
        userRepository: UserRepository,
        preferenceManager: PreferenceManager
    ): UserSession {
        return UserSession(userRepository, preferenceManager)
    }

    /**
     * Provides the current logged-in user.
     * For ViewModels that are already using @Inject constructor, we can inject User directly.
     * This assumes the UserSession has been initialized with a valid user.
     */
    @Provides
    @CurrentUser
    fun provideCurrentUser(userSession: UserSession): User {
        return userSession.getCurrentUser() 
            ?: throw IllegalStateException("No user is currently logged in")
    }
}