package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.nosved.native_codecs.NativeLib

@UnstableApi
object AudioDecoderCapabilities {

    private val nativeLib by lazy { NativeLib() }

    val VERIFIED_MIME_TYPES: Set<String> = setOf(
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_AC4,
        MimeTypes.AUDIO_TRUEHD,
        "audio/true-hd",
        "audio/mlp",
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_DTS_EXPRESS,
        "audio/vnd.dts.uhd",
        "audio/dts",
        MimeTypes.AUDIO_FLAC,
        "audio/x-flac",
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_OPUS,
        MimeTypes.AUDIO_VORBIS,
        MimeTypes.AUDIO_AAC,
        "audio/aac",
        "audio/mp4a-latm",
        MimeTypes.AUDIO_MPEG,
        MimeTypes.AUDIO_MPEG_L1,
        MimeTypes.AUDIO_MPEG_L2,
        "audio/mp3",
        MimeTypes.AUDIO_WAV,
        "audio/x-wav",
        "audio/raw",
        "audio/x-ms-wma",
        "audio/wma",
        "audio/x-ms-wmapro",
        "audio/x-ms-wmalossless"
    )

    fun isFormatSupported(format: Format): Boolean {
        if (!NativeLib.isLibraryLoaded) return false

        val sampleMimeType = format.sampleMimeType ?: return false
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return false
        }

        return isMimeTypeSupported(sampleMimeType)
    }

    fun isMimeTypeSupported(mimeType: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        if (!VERIFIED_MIME_TYPES.contains(mimeType)) return false
        return nativeLib.isAudioMimeTypeSupported(mimeType)
    }

    fun getSupportedMimeTypes(): List<String> {
        if (!NativeLib.isLibraryLoaded) return emptyList()
        return VERIFIED_MIME_TYPES.filter { nativeLib.isAudioMimeTypeSupported(it) }
    }

    fun isPcmEncodingSupported(encoding: Int): Boolean {
        return encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
    }
}
