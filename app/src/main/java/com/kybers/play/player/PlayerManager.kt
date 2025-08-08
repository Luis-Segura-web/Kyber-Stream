package com.kybers.play.player

import android.app.Application
import android.util.Log
import android.media.MediaCodec
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLConnection
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Centralized manager for VLC player operations with proper lifecycle management,
 * network connectivity handling, and resource cleanup to prevent memory leaks.
 */
class PlayerManager(
    private val application: Application,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "PlayerManager"
        private const val NETWORK_RETRY_DELAY_MS = 2000L
    }
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mediaManager = MediaManager()
    private var retryManager: RetryManager? = null
    private var networkObserver: NetworkConnectivityObserver? = null
    private var currentUrl: String? = null // Store current URL for retry
    private var isPlaybackActive = false
    private var useHardwareDecoder = true
    
    private var onRetryAttempt: ((Int, Int) -> Unit)? = null
    private var onRetrySuccess: (() -> Unit)? = null
    private var onRetryFailed: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    /**
     * Initialize VLC components and network observer if not already initialized
     */
    private fun initializeVLC() {
        if (libVLC == null) {
            val options = if (useHardwareDecoder) emptyList() else listOf("--avcodec-hw=none")
            libVLC = LibVLC(application, options)
            Log.d(TAG, "LibVLC initialized with hardwareDecoding=$useHardwareDecoder")
        }
        
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer(libVLC!!)
            setupEventListener()
            Log.d(TAG, "MediaPlayer initialized")
        }
        
        if (networkObserver == null) {
            networkObserver = NetworkConnectivityObserver(application)
            setupNetworkObserver()
            Log.d(TAG, "NetworkConnectivityObserver initialized")
        }
    }

    private fun restartPipelineWithSoftwareCodec() {
        useHardwareDecoder = false
        try {
            mediaPlayer?.setEventListener(null)
            mediaPlayer?.release()
        } catch (_: Exception) {}

        try {
            libVLC?.release()
        } catch (_: Exception) {}

        mediaPlayer = null
        libVLC = null
        initializeVLC()
    }

    private fun guessVideoMimeType(url: String): String? = URLConnection.guessContentTypeFromName(url)
    
    /**
     * Setup network connectivity observer
     */
    private fun setupNetworkObserver() {
        networkObserver?.setNetworkCallbacks(
            onNetworkLost = {
                Log.w(TAG, "Network lost during playback")
                if (isPlaybackActive) {
                    onError?.invoke("Conexión perdida. Reintentando...")
                    // Pause playback until network is restored
                    pause()
                }
            },
            onNetworkAvailable = {
                Log.d(TAG, "Network available, attempting to resume playback")
                if (isPlaybackActive && currentUrl != null) {
                    // Wait a bit for network to stabilize, then retry
                    scope.launch {
                        delay(NETWORK_RETRY_DELAY_MS)
                        if (networkObserver?.isCurrentlyConnected() == true) {
                            retryCurrentMedia()
                        }
                    }
                }
            },
            onNetworkChanged = { networkType ->
                Log.d(TAG, "Network type changed to: $networkType")
                if (isPlaybackActive && currentUrl != null) {
                    // Brief pause and resume to handle network transition
                    scope.launch {
                        delay(1000) // Short delay for network transition
                        if (networkObserver?.isCurrentlyConnected() == true) {
                            resume()
                        }
                    }
                }
            }
        )
        networkObserver?.startMonitoring()
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
                MediaPlayer.Event.Playing -> {
                    Log.d(TAG, "VLC Event: Playing")
                    isPlaybackActive = true
                }
                MediaPlayer.Event.Paused -> {
                    Log.d(TAG, "VLC Event: Paused")
                }
                MediaPlayer.Event.Stopped -> {
                    Log.d(TAG, "VLC Event: Stopped")
                    isPlaybackActive = false
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "VLC Error encountered, triggering retry")
                    isPlaybackActive = false
                    if (useHardwareDecoder) {
                        Log.w(TAG, "Attempting fallback to software decoder")
                        restartPipelineWithSoftwareCodec()
                    }
                    onError?.invoke("Error de reproducción detectado")
                    retryCurrentMedia()
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "Media playback ended")
                    isPlaybackActive = false
                }
                MediaPlayer.Event.Buffering -> {
                    Log.d(TAG, "VLC Event: Buffering (${event.buffering}%)")
                    // Could add buffering timeout handling here
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
        isPlaybackActive = true

        // Verify codec support before playback
        guessVideoMimeType(url)?.let { mime ->
            if (!CodecUtils.isVideoMimeTypeSupported(mime)) {
                Log.e(TAG, "Unsupported video mime type: $mime")
                onError?.invoke("Formato de video no soportado: $mime")
                return
            }
        }
        
        // Check network connectivity before attempting playback
        if (networkObserver?.isCurrentlyConnected() == false) {
            Log.w(TAG, "No network connectivity, cannot start playback")
            onError?.invoke("Sin conexión a internet. Verifica tu red.")
            return
        }
        
        retryManager?.startRetry(scope) {
            try {
                initializeVLC()
                val media = Media(libVLC!!, android.net.Uri.parse(url))
                mediaManager.setMediaSafely(mediaPlayer!!, media)
                mediaPlayer!!.play()
                Log.d(TAG, "Media playback started successfully")
                true
            } catch (e: Exception) {
                if (e is MediaCodec.CodecException && useHardwareDecoder) {
                    Log.e(TAG, "MediaCodec error, switching to software codec", e)
                    restartPipelineWithSoftwareCodec()
                    currentUrl = url
                } else {
                    Log.e(TAG, "Failed to play media", e)
                }
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
                if (e is MediaCodec.CodecException && useHardwareDecoder) {
                    Log.e(TAG, "MediaCodec error (no retry), switching to software codec", e)
                    restartPipelineWithSoftwareCodec()
                    currentUrl = url
                    playMedia(url)
                    return
                }
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
            isPlaybackActive = false
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
            isPlaybackActive = false
            retryManager?.cancelRetry()
            
            networkObserver?.stopMonitoring()
            networkObserver = null
            
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
            currentUrl = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource release", e)
        }
    }
}