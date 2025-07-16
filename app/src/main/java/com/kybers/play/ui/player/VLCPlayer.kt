package com.kybers.play.ui.player

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
 */
@Composable
fun VLCPlayer(mediaPlayer: MediaPlayer, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            mediaPlayer.vlcVout.setVideoSurface(android.view.Surface(surface), null)
                            mediaPlayer.vlcVout.setWindowSize(width, height)
                            mediaPlayer.vlcVout.attachViews()
                        }

                        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            mediaPlayer.vlcVout.setWindowSize(width, height)
                        }

                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                            mediaPlayer.vlcVout.detachViews()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
