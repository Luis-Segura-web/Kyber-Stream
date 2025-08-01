package com.kybers.play.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Centralized manager for VLC player operations with proper lifecycle management
 * and resource cleanup to prevent memory leaks.
 */
class PlayerManager(
    private val application: Application,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "PlayerManager"
    }
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mediaManager = MediaManager()
    private var retryManager: RetryManager? = null
    private var currentUrl: String? = null // Store current URL for retry
    
    private var onRetryAttempt: ((Int, Int) -> Unit)? = null
    private var onRetrySuccess: (() -> Unit)? = null
    private var onRetryFailed: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    /**
     * Initialize VLC components if not already initialized
     */
    private fun initializeVLC() {
        if (libVLC == null) {
            libVLC = LibVLC(application)
            Log.d(TAG, "LibVLC initialized")
        }
        
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer(libVLC!!)
            setupEventListener()
            Log.d(TAG, "MediaPlayer initialized")
        }
    }
    
    /**
     * Get the MediaPlayer instance, initializing if necessary
     */
    fun getMediaPlayer(): MediaPlayer {
        initializeVLC()
        return mediaPlayer!!
    }
    
    /**
     * Set up retry callbacks
     */
    fun setRetryCallbacks(
        onRetryAttempt: (Int, Int) -> Unit,
        onRetrySuccess: () -> Unit,
        onRetryFailed: () -> Unit,
        onError: (String) -> Unit
    ) {
        this.onRetryAttempt = onRetryAttempt
        this.onRetrySuccess = onRetrySuccess
        this.onRetryFailed = onRetryFailed
        this.onError = onError
        
        // Initialize retry manager with callbacks
        retryManager = RetryManager(
            maxRetries = 3,
            onRetryAttempt = onRetryAttempt,
            onRetrySuccess = onRetrySuccess,
            onRetryFailed = onRetryFailed
        )
    }
    
    /**
     * Setup VLC event listener with error handling and retry logic
     */
    private fun setupEventListener() {
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "VLC Error encountered, triggering retry")
                    onError?.invoke("Error de reproducción detectado")
                    retryCurrentMedia()
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "Media playback ended")
                }
                MediaPlayer.Event.Stopped -> {
                    Log.d(TAG, "Media playback stopped")
                }
                else -> {
                    // Handle other events as needed
                }
            }
        }
    }
    
    /**
     * Play media with safe resource management and retry capability
     */
    fun playMedia(url: String) {
        initializeVLC()
        currentUrl = url // Store current URL for retry
        
        retryManager?.startRetry(scope) {
            try {
                val media = Media(libVLC!!, android.net.Uri.parse(url))
                mediaManager.setMediaSafely(mediaPlayer!!, media)
                mediaPlayer!!.play()
                Log.d(TAG, "Media playback started successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play media", e)
                false
            }
        } ?: run {
            // Fallback if retry manager not set up
            try {
                val media = Media(libVLC!!, android.net.Uri.parse(url))
                mediaManager.setMediaSafely(mediaPlayer!!, media)
                mediaPlayer!!.play()
                Log.d(TAG, "Media playback started (no retry)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play media (no retry)", e)
                onError?.invoke("Error al reproducir el contenido")
            }
        }
    }
    
    /**
     * Retry current media playback
     */
    fun retryCurrentMedia() {
        val url = currentUrl
        if (url != null && retryManager != null) {
            Log.d(TAG, "Retrying current media: $url")
            playMedia(url)
        } else {
            Log.w(TAG, "Cannot retry: no current URL or retry manager")
            onError?.invoke("Error al reintentar la reproducción")
        }
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        try {
            mediaPlayer?.pause()
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        try {
            mediaPlayer?.play()
            Log.d(TAG, "Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }
    
    /**
     * Stop playback and clean up current media
     */
    fun stop() {
        try {
            mediaPlayer?.let { player ->
                mediaManager.stopAndReleaseMedia(player)
                Log.d(TAG, "Playback stopped and media released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }
    
    /**
     * Check if player is currently playing
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking isPlaying", e)
            false
        }
    }
    
    // Lifecycle observer methods
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d(TAG, "Lifecycle: onResume")
        // VLC will be initialized when needed
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "Lifecycle: onPause - pausing playback")
        pause()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "Lifecycle: onStop - stopping playback")
        stop()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.d(TAG, "Lifecycle: onDestroy - releasing all resources")
        release()
    }
    
    /**
     * Release all VLC resources
     */
    fun release() {
        try {
            retryManager?.cancelRetry()
            
            mediaPlayer?.let { player ->
                player.setEventListener(null)
                mediaManager.stopAndReleaseMedia(player)
                player.release()
                Log.d(TAG, "MediaPlayer released")
            }
            
            libVLC?.release()
            Log.d(TAG, "LibVLC released")
            
            mediaPlayer = null
            libVLC = null
            retryManager = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource release", e)
        }
    }
}