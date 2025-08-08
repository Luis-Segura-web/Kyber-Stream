package com.kybers.play.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages retry logic for VLC playback failures with exponential backoff
 * Implements 3 retry attempts with delays: 1s, 2s, 4s
 */
class RetryManager(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000L,
    private val onRetryAttempt: (attempt: Int, maxRetries: Int) -> Unit,
    private val onRetrySuccess: () -> Unit,
    private val onRetryFailed: () -> Unit
) {
    
    companion object {
        private const val TAG = "RetryManager"
    }
    
    private var currentAttempt = 0
    private var retryJob: Job? = null
    
    /**
     * Starts the retry sequence with exponential backoff
     * @param scope Coroutine scope for the retry operations
     * @param retryAction Suspend function that returns true on success, false on failure
     */
    fun startRetry(scope: CoroutineScope, retryAction: suspend () -> Boolean) {
        cancelRetry() // Cancel any existing retry
        currentAttempt = 0

        retryJob = scope.launch {
            Log.d(TAG, "Starting retry sequence, max attempts: $maxRetries")

            while (currentAttempt < maxRetries) {
                val attempt = currentAttempt + 1
                Log.d(TAG, "Retry attempt $attempt of $maxRetries")
                onRetryAttempt(attempt, maxRetries)

                val delayMs = baseDelayMs * (1L shl (attempt - 1))
                Log.d(TAG, "Waiting ${delayMs}ms before attempt")
                delay(delayMs)

                val success = try {
                    retryAction()
                } catch (e: Exception) {
                    Log.w(TAG, "Retry attempt $attempt failed with exception", e)
                    false
                }

                if (success) {
                    Log.d(TAG, "Retry succeeded on attempt $attempt")
                    onRetrySuccess()
                    reset()
                    return@launch
                }

                currentAttempt = attempt
            }

            Log.e(TAG, "All $maxRetries retry attempts failed")
            onRetryFailed()
            retryJob = null
        }
    }
    
    /**
     * Cancels the current retry sequence
     */
    fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
        Log.d(TAG, "Retry sequence cancelled")
    }
    
    /**
     * Resets the retry state
     */
    fun reset() {
        currentAttempt = 0
        retryJob = null
        Log.d(TAG, "Retry state reset")
    }
    
    /**
     * Returns true if a retry sequence is currently active
     */
    fun isRetrying(): Boolean = retryJob?.isActive == true
    
    /**
     * Returns the current attempt number (0 if not retrying)
     */
    fun getCurrentAttempt(): Int = if (isRetrying()) currentAttempt else 0
    
    /**
     * Returns the maximum number of retry attempts
     */
    fun getMaxRetries(): Int = maxRetries
}