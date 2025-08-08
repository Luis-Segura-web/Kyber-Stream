package com.kybers.play.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Simple client for writing playback events to an internal log file. Ensures that
 * the target directory exists before writing and never throws if the file cannot
 * be created.
 */
object XLogClient {
    private const val TAG = "XLogClient"
    private const val LOG_DIR = "xlog"

    /**
     * Appends a message to the playback log. Errors during file access are logged
     * but won't crash the app.
     */
    fun sendVideoPlay(context: Context, message: String) {
        try {
            val dir = File(context.filesDir, LOG_DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create xlog directory: ${dir.absolutePath}")
                return
            }
            val logFile = File(dir, "video_play.log")
            logFile.appendText("$message\n")
            Log.d(TAG, "Logged playback event to xlog")
        } catch (e: Exception) {
            Log.e(TAG, "xlog open fail", e)
        }
    }
}

