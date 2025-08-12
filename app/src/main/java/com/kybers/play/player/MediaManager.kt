package com.kybers.play.player

import android.util.Log
import com.kybers.play.core.player.PlayerCoordinator
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Safely manages VLC Media object lifecycle with proper error handling
 * to prevent memory leaks that cause: VLCObject finalized but not natively released
 */
class MediaManager {
    
    companion object {
        private const val TAG = "MediaManager"
    }
    
    var playerCoordinator: PlayerCoordinator? = null
    
    /**
     * Get the current PlayerEngine from the PlayerCoordinator
     */
    fun getCurrentEngine() = playerCoordinator?.getCurrentEngine()
    
    /**
     * Safely sets new media on MediaPlayer, properly releasing previous media
     * @param mediaPlayer The VLC MediaPlayer instance
     * @param newMedia The new Media object to set
     * @throws Exception if setting media fails (newMedia will be released automatically)
     */
    fun setMediaSafely(mediaPlayer: MediaPlayer, newMedia: Media) {
        try {
            // Release previous media if it exists
            releaseCurrentMedia(mediaPlayer)
            
            // Set the new media
            mediaPlayer.media = newMedia
            Log.d(TAG, "Media set successfully")
        } catch (e: Exception) {
            // If setting fails, release the new media to prevent leak
            try {
                newMedia.release()
            } catch (releaseException: Exception) {
                Log.e(TAG, "Error releasing media after failed set", releaseException)
            }
            Log.e(TAG, "Error setting media", e)
            throw e
        }
    }
    
    /**
     * Safely releases current media from MediaPlayer
     * @param mediaPlayer The VLC MediaPlayer instance
     */
    fun releaseCurrentMedia(mediaPlayer: MediaPlayer) {
        try {
            mediaPlayer.media?.let { media ->
                mediaPlayer.media = null
                media.release()
                Log.d(TAG, "Previous media released successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing current media", e)
            // Don't throw - this is cleanup, should not fail the operation
        }
    }
    
    /**
     * Safely stops and releases all media resources
     * @param mediaPlayer The VLC MediaPlayer instance
     */
    fun stopAndReleaseMedia(mediaPlayer: MediaPlayer) {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            releaseCurrentMedia(mediaPlayer)
            Log.d(TAG, "Media stopped and released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping and releasing media", e)
            // Don't throw - this is cleanup
        }
    }
}
