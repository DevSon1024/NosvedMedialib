package com.devson.nosvedmedia.media3

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@UnstableApi
object VideoDecoderCapabilities {

    val SUPPORTED_MIME_TYPES: Set<String> = setOf(
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_VP8,
        MimeTypes.VIDEO_VP9,
        MimeTypes.VIDEO_MP4V,
        MimeTypes.VIDEO_MPEG2
    )

    fun isFormatSupported(format: Format): Boolean {
        val sampleMimeType = format.sampleMimeType ?: return false
        return isMimeTypeSupported(sampleMimeType)
    }

    fun isMimeTypeSupported(mimeType: String): Boolean {
        return SUPPORTED_MIME_TYPES.contains(mimeType)
    }

    fun getSupportedMimeTypes(): List<String> {
        return SUPPORTED_MIME_TYPES.toList()
    }
}

