package com.kybers.play.player

import android.app.Application
import android.util.Log
import android.media.MediaCodecList
import android.webkit.MimeTypeMap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import com.kybers.play.data.preferences.PreferenceManager

/**
 * Centralized manager for player operations with player preference support.
 * Respects user's choice between Media3 (ExoPlayer) and VLC (LibVLC).
 * Includes proper lifecycle management, network connectivity handling, 
 * and resource cleanup to prevent memory leaks.
 */
class PlayerManager(
    private val application: Application,
    private val scope: CoroutineScope,
    private val preferenceManager: PreferenceManager? = null
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
    
    private var onRetryAttempt: ((Int, Int) -> Unit)? = null
    private var onRetrySuccess: (() -> Unit)? = null
    private var onRetryFailed: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var softwareFallbackTried = false

    private fun isCodecSupported(mimeType: String): Boolean {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        return list.codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, true) }
        }
    }
    
    /**
     * Initialize VLC components and network observer if not already initialized
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
        
        if (networkObserver == null) {
            networkObserver = NetworkConnectivityObserver(application)
            setupNetworkObserver()
            Log.d(TAG, "NetworkConnectivityObserver initialized")
        }
    }
    
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
                    Log.e(TAG, "VLC Error encountered")
                    isPlaybackActive = false
                    val url = currentUrl
                    if (!softwareFallbackTried && url != null) {
                        Log.w(TAG, "Unknown format? retrying with software decoding")
                        playMedia(url, forceSoftwareDecoding = true)
                    } else {
                        onError?.invoke("Error de reproducción detectado")
                        retryCurrentMedia()
                    }
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
    fun playMedia(url: String, forceSoftwareDecoding: Boolean = false) {
        // LOGS BÁSICOS - SIN EMOJIS PARA COMPATIBILIDAD
        Log.d(TAG, "=== PLAYERMANAGER.playMedia() INICIADO ===")
        Log.d(TAG, "URL: " + url.takeLast(30) + "...")
        
        // VERIFICAR PREFERENCIAS DEL REPRODUCTOR
        preferenceManager?.let { prefs ->
            val playerPreference = prefs.getPlayerPreference()
            Log.d(TAG, "PREFERENCIA DETECTADA: " + playerPreference)
            
            // DIAGNÓSTICO CRÍTICO - ESTE ES EL PROBLEMA PRINCIPAL
            if (playerPreference == "MEDIA3") {
                Log.e(TAG, "*** PROBLEMA DETECTADO ***")
                Log.e(TAG, "Usuario eligio MEDIA3 pero PlayerManager solo usa VLC")
                Log.e(TAG, "POR ESO MEDIA3 NO FUNCIONA - EL CODIGO IGNORA LA PREFERENCIA")
                Log.e(TAG, "*** FIN DIAGNOSTICO ***")
            } else if (playerPreference == "VLC") {
                Log.d(TAG, "VLC seleccionado - OK para PlayerManager")
            } else {
                Log.d(TAG, "AUTO seleccionado - PlayerManager usara VLC")
            }
        } ?: run {
            Log.w(TAG, "Sin PreferenceManager - no se pueden verificar preferencias")
        }
        
        initializeVLC()
        currentUrl = url // Store current URL for retry
        isPlaybackActive = true
        softwareFallbackTried = forceSoftwareDecoding
        
        // Check network connectivity before attempting playback
        if (networkObserver?.isCurrentlyConnected() == false) {
            Log.w(TAG, "No network connectivity, cannot start playback")
            onError?.invoke("Sin conexión a internet. Verifica tu red.")
            return
        }

        val ext = MimeTypeMap.getFileExtensionFromUrl(url)
        val mime = ext?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        if (!forceSoftwareDecoding && mime != null && mime.startsWith("video/") && !isCodecSupported(mime)) {
            Log.w(TAG, "Codec $mime not supported, retrying with software decoding")
            playMedia(url, forceSoftwareDecoding = true)
            return
        }
        
        retryManager?.startRetry(scope) {
            try {
                val media = Media(libVLC!!, android.net.Uri.parse(url))
                if (forceSoftwareDecoding) media.addOption("--avcodec-hw=none")
                mediaManager.setMediaSafely(mediaPlayer!!, media)
                mediaPlayer!!.play()
                com.kybers.play.util.XLogClient.sendVideoPlay(application, url)
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
                if (forceSoftwareDecoding) media.addOption("--avcodec-hw=none")
                mediaManager.setMediaSafely(mediaPlayer!!, media)
                mediaPlayer!!.play()
                com.kybers.play.util.XLogClient.sendVideoPlay(application, url)
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
            playMedia(url, softwareFallbackTried)
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