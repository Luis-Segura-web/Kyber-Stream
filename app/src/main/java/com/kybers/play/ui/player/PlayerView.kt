package com.kybers.play.ui.player

import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.kybers.play.core.player.PlayerEngine
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "PlayerView"

/**
 * A generic player view that can handle both VLC and Media3 engines.
 * This replaces the direct use of VLCPlayer and provides a unified interface
 * for displaying video content regardless of the underlying player engine.
 *
 * @param playerEngine The player engine to display (VLC or Media3)
 * @param modifier Modifier for the composable
 */
@Composable
fun PlayerView(
    playerEngine: PlayerEngine?,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "PlayerView composable created")
    
    // State to hold the surface/texture view for VLC
    var vlcSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var vlcTextureView by remember { mutableStateOf<TextureView?>(null) }
    
    // State to hold the Media3 PlayerView
    var media3PlayerView by remember { mutableStateOf<PlayerView?>(null) }
    
    // Track if views are attached
    var isVlcSurfaceAttached by remember { mutableStateOf(false) }
    var isMedia3ViewAttached by remember { mutableStateOf(false) }
    
    LaunchedEffect(playerEngine) {
        Log.d(TAG, "PlayerEngine changed, setting up listeners")
        playerEngine?.state?.collectLatest { state ->
            Log.d(TAG, "Player state changed: $state")
            // Handle state changes if needed
        }
    }
    
    Box(modifier = modifier) {
        // Handle VLC engine (using SurfaceView)
        if (playerEngine != null && (playerEngine::class.simpleName?.contains("Vlc", ignoreCase = true) == true || 
                                     playerEngine is com.kybers.play.core.player.VlcPlayerEngineAdapter)) {
            Log.d(TAG, "Setting up VLC player view")
            AndroidView(
                factory = { context ->
                    Log.d(TAG, "Creating SurfaceView for VLC")
                    SurfaceView(context).apply {
                        vlcSurfaceView = this
                    }
                },
                update = { surfaceView ->
                    Log.d(TAG, "Updating SurfaceView for VLC")
                    // Check if we need to reattach
                    if (!isVlcSurfaceAttached) {
                        try {
                            // Get the VLC MediaPlayer and attach the surface
                            val vlcMediaPlayer = when (playerEngine) {
                                is com.kybers.play.core.player.VlcPlayerEngineAdapter -> {
                                    playerEngine.getMediaPlayer()
                                }
                                is com.kybers.play.core.player.VlcEngine -> {
                                    // This would need to be implemented in VlcEngine
                                    Log.w(TAG, "VlcEngine surface attachment not implemented")
                                    null
                                }
                                else -> null
                            }
                            
                            vlcMediaPlayer?.let { mediaPlayer ->
                                Log.d(TAG, "Attaching SurfaceView to VLC MediaPlayer")
                                mediaPlayer.vlcVout.setVideoSurface(surfaceView.holder.surface, surfaceView.holder)
                                mediaPlayer.vlcVout.attachViews()
                                isVlcSurfaceAttached = true
                                Log.d(TAG, "VLC surface attached successfully")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error attaching SurfaceView to VLC MediaPlayer", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // Handle Media3 engine (using PlayerView)
        else if (playerEngine != null && playerEngine::class.simpleName?.contains("Media3", ignoreCase = true) == true) {
            Log.d(TAG, "Setting up Media3 player view")
            AndroidView(
                factory = { context ->
                    Log.d(TAG, "Creating PlayerView for Media3")
                    PlayerView(context).apply {
                        media3PlayerView = this
                        useController = false // We'll use our own controls
                    }
                },
                update = { playerView ->
                    Log.d(TAG, "Updating PlayerView for Media3")
                    // Check if we need to reattach
                    if (!isMedia3ViewAttached && playerEngine is com.kybers.play.core.player.Media3Engine) {
                        try {
                            // Get the ExoPlayer instance and attach it to the PlayerView
                            val exoPlayer = playerEngine.getExoPlayer()
                            if (exoPlayer != null) {
                                playerView.player = exoPlayer
                                Log.d(TAG, "Attached ExoPlayer to PlayerView")
                                isMedia3ViewAttached = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error attaching ExoPlayer to PlayerView", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    
    // Cleanup effect
    DisposableEffect(playerEngine) {
        onDispose {
            Log.d(TAG, "Disposing PlayerView")
            try {
                // Clean up VLC surface if needed
                if (isVlcSurfaceAttached) {
                    Log.d(TAG, "Detaching SurfaceView from VLC MediaPlayer")
                    try {
                        val vlcMediaPlayer = when (playerEngine) {
                            is com.kybers.play.core.player.VlcPlayerEngineAdapter -> {
                                playerEngine.getMediaPlayer()
                            }
                            else -> null
                        }
                        vlcMediaPlayer?.vlcVout?.detachViews()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error detaching VLC surface", e)
                    }
                    isVlcSurfaceAttached = false
                }
                
                // Clean up Media3 PlayerView if needed
                if (isMedia3ViewAttached) {
                    media3PlayerView?.player = null
                    Log.d(TAG, "Detached ExoPlayer from PlayerView")
                    isMedia3ViewAttached = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during PlayerView cleanup", e)
            }
        }
    }
}
