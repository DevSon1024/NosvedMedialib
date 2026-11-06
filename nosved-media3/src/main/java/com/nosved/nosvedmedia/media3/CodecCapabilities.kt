package com.nosved.nosvedmedia.media3

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.nosved.native_codecs.NativeLib

@UnstableApi
object CodecCapabilities {

    data class CodecReport(
        val mimeType: String,
        val isHardwareAccelerated: Boolean,
        val isSoftwareFallbackAvailable: Boolean,
        val softwareEngine: String?,
        val supportedProfiles: List<String>,
        val supportedBitDepths: List<Int>,
        val supportedOutputFormats: List<String>
    )

    private val nativeLib by lazy { NativeLib() }

    fun getHardwareDecoderAvailability(mimeType: String): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder &&
                info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
                isHardwareCodec(info)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getSoftwareDecoderAvailability(mimeType: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        return VideoDecoderCapabilities.isMimeTypeSupported(mimeType)
    }

    fun getCodecReport(mimeType: String): CodecReport {
        val hasHw = getHardwareDecoderAvailability(mimeType)
        val hasSw = getSoftwareDecoderAvailability(mimeType)

        return when (mimeType) {
            MimeTypes.VIDEO_AV1 -> {
                val dav1dVer = if (hasSw) nativeLib.getDav1dVersion() else "unavailable"
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "dav1d $dav1dVer" else null,
                    supportedProfiles = if (hasSw) listOf("Main (Profile 0)") else emptyList(),
                    supportedBitDepths = if (hasSw) listOf(8, 10) else emptyList(),
                    supportedOutputFormats = if (hasSw) listOf("YUV420P", "YUV420P10LE", "RGBA_8888") else emptyList()
                )
            }
            MimeTypes.VIDEO_H264 -> {
                val ffmpegVer = if (hasSw) nativeLib.getFFmpegVersion() else "unavailable"
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = if (hasSw) listOf("Baseline", "Main", "High", "High 10") else emptyList(),
                    supportedBitDepths = if (hasSw) listOf(8, 10) else emptyList(),
                    supportedOutputFormats = if (hasSw) listOf("YUV420P", "RGBA_8888") else emptyList()
                )
            }
            MimeTypes.VIDEO_H265 -> {
                val ffmpegVer = if (hasSw) nativeLib.getFFmpegVersion() else "unavailable"
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = if (hasSw) listOf("Main", "Main 10") else emptyList(),
                    supportedBitDepths = if (hasSw) listOf(8, 10) else emptyList(),
                    supportedOutputFormats = if (hasSw) listOf("YUV420P", "YUV420P10LE", "RGBA_8888") else emptyList()
                )
            }
            MimeTypes.VIDEO_VP9 -> {
                val ffmpegVer = if (hasSw) nativeLib.getFFmpegVersion() else "unavailable"
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = if (hasSw) listOf("Profile 0", "Profile 2") else emptyList(),
                    supportedBitDepths = if (hasSw) listOf(8, 10) else emptyList(),
                    supportedOutputFormats = if (hasSw) listOf("YUV420P", "RGBA_8888") else emptyList()
                )
            }
            MimeTypes.VIDEO_VP8, MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_MPEG2 -> {
                val ffmpegVer = if (hasSw) nativeLib.getFFmpegVersion() else "unavailable"
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = if (hasSw) listOf("Main") else emptyList(),
                    supportedBitDepths = if (hasSw) listOf(8) else emptyList(),
                    supportedOutputFormats = if (hasSw) listOf("YUV420P", "RGBA_8888") else emptyList()
                )
            }
            else -> {
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = false,
                    softwareEngine = null,
                    supportedProfiles = emptyList(),
                    supportedBitDepths = emptyList(),
                    supportedOutputFormats = emptyList()
                )
            }
        }
    }

    fun getAllCodecReports(): List<CodecReport> {
        return VideoDecoderCapabilities.getSupportedMimeTypes().map { getCodecReport(it) }
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") &&
            !name.startsWith("c2.android.") &&
            !name.contains("sw")
        }
    }
}
