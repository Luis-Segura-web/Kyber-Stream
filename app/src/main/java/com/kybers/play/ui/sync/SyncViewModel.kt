package com.kybers.play.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the different states of the data synchronization process,
 * now with more detail to show step-by-step progress.
 */
sealed class SyncState {
    object Idle : SyncState()
    object SyncingChannels : SyncState()
    object SyncingMovies : SyncState()
    object SyncingSeries : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * ViewModel for the SyncScreen. It handles the logic for fetching all content
 * from the remote server and caching it into the local database in a sequential manner.
 *
 * @param contentRepository The repository to fetch content.
 * @param syncManager The manager to handle sync timestamps.
 */
class SyncViewModel(
    private val contentRepository: ContentRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Starts the sequential data synchronization process for the given user.
     * It updates the UI state to reflect the current step of the process.
     *
     * @param user The user profile for which to sync the data.
     */
    fun startSync(user: User) {
        viewModelScope.launch {
            try {
                // Step 1: Sync Channels
                _syncState.update { SyncState.SyncingChannels }
                contentRepository.cacheLiveStreams(user.username, user.password)

                // Step 2: Sync Movies
                _syncState.update { SyncState.SyncingMovies }
                contentRepository.cacheMovies(user.username, user.password)

                // Step 3: Sync Series
                _syncState.update { SyncState.SyncingSeries }
                contentRepository.cacheSeries(user.username, user.password)

                // After all steps are complete, update the timestamp and set state to Success.
                syncManager.saveLastSyncTimestamp()
                _syncState.update { SyncState.Success }

            } catch (e: Exception) {
                // If any error occurs during any step, update the UI state with an error message.
                _syncState.update { SyncState.Error("Failed to sync data: ${e.message}") }
            }
        }
    }
}
