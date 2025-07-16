package com.kybers.play.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val libVLC = LibVLC(application)
    val mediaPlayer = MediaPlayer(libVLC)

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.stop()
        mediaPlayer.release()
        libVLC.release()
    }
}
