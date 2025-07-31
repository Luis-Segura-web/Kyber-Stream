package com.kybers.play.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import com.kybers.play.player.MediaManager

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val libVLC = LibVLC(application)
    val mediaPlayer = MediaPlayer(libVLC)
    private val mediaManager = MediaManager()

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.stop()
        mediaManager.releaseCurrentMedia(mediaPlayer)
        mediaPlayer.release()
        libVLC.release()
    }
}
