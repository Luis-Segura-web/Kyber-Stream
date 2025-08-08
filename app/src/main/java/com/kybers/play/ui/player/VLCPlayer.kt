package com.kybers.play.ui.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.IVLCVout
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Composable reutilizable que muestra el video de un MediaPlayer de VLC
 * usando [VLCVideoLayout] para gestionar correctamente las superficies y
 * callbacks del video.
 */
@Composable
fun VLCPlayer(mediaPlayer: MediaPlayer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vlcVout = mediaPlayer.vlcVout
    val videoLayout = remember { VLCVideoLayout(context) }
    val voutCallback = remember {
        object : IVLCVout.Callback {
            override fun onNewVideoLayout(
                vlcVout: IVLCVout?,
                width: Int,
                height: Int,
                visibleWidth: Int,
                visibleHeight: Int,
                sarNum: Int,
                sarDen: Int
            ) {
                Log.d("VLCPlayer", "New video layout: ${width}x${height}")
            }

            override fun onSurfacesCreated(vlcVout: IVLCVout?) {
                Log.d("VLCPlayer", "Surfaces created")
            }

            override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {
                Log.d("VLCPlayer", "Surfaces destroyed")
            }
        }
    }

    DisposableEffect(vlcVout, videoLayout) {
        vlcVout.setVideoView(videoLayout)
        vlcVout.attachViews()
        vlcVout.addCallback(voutCallback)
        onDispose {
            vlcVout.removeCallback(voutCallback)
            vlcVout.detachViews()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { videoLayout },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
