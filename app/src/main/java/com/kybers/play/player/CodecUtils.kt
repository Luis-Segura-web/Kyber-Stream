package com.kybers.play.player

import android.media.MediaCodecList

/**
 * Utility for checking device codec capabilities.
 */
object CodecUtils {
    /**
     * Returns true if the device has a decoder that supports the provided
     * MIME type. Only decoders are considered, encoders are ignored.
     */
    fun isVideoMimeTypeSupported(mimeType: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        return codecList.codecInfos.any { info ->
            if (!info.isEncoder) {
                info.supportedTypes.any { type ->
                    type.equals(mimeType, ignoreCase = true)
                }
            } else {
                false
            }
        }
    }
}
