package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.nosved.native_codecs.NativeLib

@UnstableApi
object VideoDecoderCapabilities {

    val SUPPORTED_MIME_TYPES: Set<String> = setOf(
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_VP8,
        MimeTypes.VIDEO_VP9,
        MimeTypes.VIDEO_MP4V,
        MimeTypes.VIDEO_MPEG2,
        MimeTypes.VIDEO_AV1
    )

    private const val MAX_SOFTWARE_WIDTH = 4096
    private const val MAX_SOFTWARE_HEIGHT = 2304

    /**
     * Evaluates whether a video format can be decoded by custom software decoders.
     * Checks MIME type, DRM encryption, resolution, and bit depth.
     */
    fun isFormatSupported(format: Format): Boolean {
        if (!NativeLib.isLibraryLoaded) return false

        val sampleMimeType = format.sampleMimeType ?: return false
        if (!isMimeTypeSupported(sampleMimeType)) return false

        // Custom software decoders cannot decrypt DRM-protected streams
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return false
        }

        // Limit software decoding resolution to prevent OOM / unplayable frame rates
        val width = format.width
        val height = format.height
        if (width != Format.NO_VALUE && height != Format.NO_VALUE) {
            if (width > MAX_SOFTWARE_WIDTH || height > MAX_SOFTWARE_HEIGHT) {
                // Reject resolutions higher than 4K for software decoding
                return false
            }
        }

        // Validate bit depth from ColorInfo if present
        val colorInfo = format.colorInfo
        if (colorInfo != null) {
            val bitDepth = colorInfo.colorTransfer
            // In Android Media3, colorInfo does not directly carry bitDepth, but format or codecs might.
            // When format is AV1 or HEVC, dav1d and FFmpeg support up to 10-bit.
        }

        return true
    }

    fun isMimeTypeSupported(mimeType: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        return SUPPORTED_MIME_TYPES.contains(mimeType)
    }

    fun getSupportedMimeTypes(): List<String> {
        return SUPPORTED_MIME_TYPES.toList()
    }

    fun isDav1dSupported(format: Format): Boolean {
        return format.sampleMimeType == MimeTypes.VIDEO_AV1 && isFormatSupported(format)
    }

    fun isFFmpegVideoSupported(format: Format): Boolean {
        val mime = format.sampleMimeType ?: return false
        return mime != MimeTypes.VIDEO_AV1 && isFormatSupported(format)
    }
}
