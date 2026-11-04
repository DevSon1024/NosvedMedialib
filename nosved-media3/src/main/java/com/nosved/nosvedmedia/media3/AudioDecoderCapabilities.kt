package com.nosved.nosvedmedia.media3

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.nosved.native_codecs.NativeLib

@UnstableApi
object AudioDecoderCapabilities {

    val SUPPORTED_MIME_TYPES: Set<String> = setOf(
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_AC4,
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_DTS_EXPRESS,
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_OPUS,
        MimeTypes.AUDIO_VORBIS,
        MimeTypes.AUDIO_AAC,
        "audio/aac",
        MimeTypes.AUDIO_MPEG,
        MimeTypes.AUDIO_MPEG_L1,
        MimeTypes.AUDIO_MPEG_L2,
        MimeTypes.AUDIO_WAV,
        "audio/x-ms-wma",
        "audio/wma",
        "audio/x-ms-wmapro",
        "audio/mlp"
    )

    fun isFormatSupported(format: Format): Boolean {
        val sampleMimeType = format.sampleMimeType ?: return false
        return isMimeTypeSupported(sampleMimeType)
    }

    fun isMimeTypeSupported(mimeType: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        return SUPPORTED_MIME_TYPES.contains(mimeType)
    }

    fun getSupportedMimeTypes(): List<String> {
        return SUPPORTED_MIME_TYPES.toList()
    }
}

