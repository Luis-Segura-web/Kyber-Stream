package com.kybers.play.ui.player

import android.util.Log
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.MediaPlayer

/**
 * Un Composable reutilizable que muestra el video de un MediaPlayer de VLC
 * usando una TextureView para ser compatible con otros elementos de la UI.
 * Includes enhanced error handling for VLC operations.
 */
@Composable
fun VLCPlayer(mediaPlayer: MediaPlayer, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            try {
                                mediaPlayer.vlcVout.setVideoSurface(android.view.Surface(surface), null)
                                mediaPlayer.vlcVout.setWindowSize(width, height)
                                mediaPlayer.vlcVout.attachViews()
                                Log.d("VLCPlayer", "Surface attached successfully")
                            } catch (e: Exception) {
                                Log.e("VLCPlayer", "Error setting up video surface", e)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            try {
                                mediaPlayer.vlcVout.setWindowSize(width, height)
                                Log.d("VLCPlayer", "Surface size changed: ${width}x${height}")
                            } catch (e: Exception) {
                                Log.e("VLCPlayer", "Error updating surface size", e)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                            try {
                                mediaPlayer.vlcVout.detachViews()
                                Log.d("VLCPlayer", "Surface detached successfully")
                                return true
                            } catch (e: Exception) {
                                Log.e("VLCPlayer", "Error detaching surface", e)
                                return true // Return true anyway to free the surface
                            }
                        }

                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
