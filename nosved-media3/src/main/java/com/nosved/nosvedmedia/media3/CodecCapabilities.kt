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

    private val nativeLib = NativeLib()

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
                val dav1dVer = nativeLib.getDav1dVersion()
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "dav1d $dav1dVer" else null,
                    supportedProfiles = listOf("Main (Profile 0)", "High (Profile 1)", "Professional (Profile 2)"),
                    supportedBitDepths = listOf(8, 10),
                    supportedOutputFormats = listOf("YUV420P", "YUV420P10LE", "RGBA_8888")
                )
            }
            MimeTypes.VIDEO_H264 -> {
                val ffmpegVer = nativeLib.getFFmpegVersion()
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = listOf("Baseline", "Main", "Extended", "High", "High 10"),
                    supportedBitDepths = listOf(8, 10),
                    supportedOutputFormats = listOf("YUV420P", "RGBA_8888")
                )
            }
            MimeTypes.VIDEO_H265 -> {
                val ffmpegVer = nativeLib.getFFmpegVersion()
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = listOf("Main", "Main 10", "Main Still Picture"),
                    supportedBitDepths = listOf(8, 10),
                    supportedOutputFormats = listOf("YUV420P", "YUV420P10LE", "RGBA_8888")
                )
            }
            MimeTypes.VIDEO_VP9 -> {
                val ffmpegVer = nativeLib.getFFmpegVersion()
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = listOf("Profile 0", "Profile 2"),
                    supportedBitDepths = listOf(8, 10),
                    supportedOutputFormats = listOf("YUV420P", "RGBA_8888")
                )
            }
            MimeTypes.VIDEO_VP8, MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_MPEG2 -> {
                val ffmpegVer = nativeLib.getFFmpegVersion()
                CodecReport(
                    mimeType = mimeType,
                    isHardwareAccelerated = hasHw,
                    isSoftwareFallbackAvailable = hasSw,
                    softwareEngine = if (hasSw) "FFmpeg $ffmpegVer" else null,
                    supportedProfiles = listOf("Main"),
                    supportedBitDepths = listOf(8),
                    supportedOutputFormats = listOf("YUV420P", "RGBA_8888")
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

