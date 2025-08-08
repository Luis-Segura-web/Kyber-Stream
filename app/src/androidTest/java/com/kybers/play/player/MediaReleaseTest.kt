package com.kybers.play.player

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@RunWith(AndroidJUnit4::class)
class MediaReleaseTest {

    @Test
    fun mediaIsReleasedWithoutNativeLeaks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val libVLC = LibVLC(context)
        val mediaPlayer = MediaPlayer(libVLC)
        val mediaManager = MediaManager()

        val media = Media(libVLC, Uri.parse("http://example.com"))
        try {
            mediaManager.setMediaSafely(mediaPlayer, media)
        } finally {
            mediaManager.stopAndReleaseMedia(mediaPlayer)
            mediaPlayer.release()
            libVLC.release()
        }

        // Allow time for log messages to be written
        Thread.sleep(500)

        val logProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "VLCObject"))
        val logs = logProcess.inputStream.bufferedReader().use { it.readText() }
        assertFalse("VLCObject leak detected", logs.contains("VLCObject"))
    }
}
