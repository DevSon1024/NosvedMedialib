package com.nosved.nosvedmedia.media3

import android.media.MediaCodecInfo
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
        return PlatformCodecInspector.findDecodersForMimeType(mimeType, preferHardware = true)
            .any { it.isHardwareAccelerated }
    }

    fun getSoftwareDecoderAvailability(mimeType: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        if (mimeType.equals(MimeTypes.VIDEO_AV1, ignoreCase = true)) {
            return Dav1dCodecInspector.isCompiled()
        }
        val decoderName = FFmpegCodecInspector.mapMimeTypeToDecoderName(mimeType)
        return decoderName != null && FFmpegCodecInspector.isDecoderCompiled(decoderName)
    }

    fun getCodecReport(mimeType: String): CodecReport {
        val hasHw = getHardwareDecoderAvailability(mimeType)
        val hasSw = getSoftwareDecoderAvailability(mimeType)

        if (mimeType.equals(MimeTypes.VIDEO_AV1, ignoreCase = true)) {
            val dav1dVer = if (hasSw) Dav1dCodecInspector.getVersion() else "unavailable"
            val dav1dCaps = if (hasSw) Dav1dCodecInspector.getCapabilities() else null
            return CodecReport(
                mimeType = mimeType,
                isHardwareAccelerated = hasHw,
                isSoftwareFallbackAvailable = hasSw,
                softwareEngine = if (hasSw) "dav1d $dav1dVer" else null,
                supportedProfiles = dav1dCaps?.supportedProfiles ?: emptyList(),
                supportedBitDepths = dav1dCaps?.supportedBitDepths ?: emptyList(),
                supportedOutputFormats = dav1dCaps?.supportedPixelLayouts ?: emptyList()
            )
        }

        val compiledDecoder = if (hasSw) FFmpegCodecInspector.findDecoderForMimeType(mimeType) else null
        if (compiledDecoder != null) {
            val ffmpegVer = nativeLib.getFFmpegVersion()
            val profiles = if (compiledDecoder.profiles.isNotEmpty()) {
                compiledDecoder.profiles.map { it.name }
            } else {
                // If profile is dynamically determined by bitstream, report known decoded profiles
                when (mimeType.lowercase()) {
                    MimeTypes.VIDEO_H264.lowercase() -> listOf("Constrained Baseline", "Baseline", "Main", "High", "High 10")
                    MimeTypes.VIDEO_H265.lowercase() -> listOf("Main", "Main 10")
                    MimeTypes.VIDEO_VP9.lowercase() -> listOf("Profile 0", "Profile 2")
                    else -> listOf("Main")
                }
            }
            val bitDepths = if (compiledDecoder.bitDepths.isNotEmpty()) {
                compiledDecoder.bitDepths
            } else {
                if (mimeType.equals(MimeTypes.VIDEO_H265, ignoreCase = true) ||
                    mimeType.equals(MimeTypes.VIDEO_VP9, ignoreCase = true) ||
                    mimeType.equals(MimeTypes.VIDEO_H264, ignoreCase = true)
                ) {
                    listOf(8, 10)
                } else {
                    listOf(8)
                }
            }
            val pixFmts = if (compiledDecoder.pixelFormats.isNotEmpty()) {
                compiledDecoder.pixelFormats
            } else {
                listOf("YUV420P", "YUV420P10LE", "NV12", "RGBA_8888")
            }

            return CodecReport(
                mimeType = mimeType,
                isHardwareAccelerated = hasHw,
                isSoftwareFallbackAvailable = hasSw,
                softwareEngine = "FFmpeg $ffmpegVer (${compiledDecoder.name})",
                supportedProfiles = profiles,
                supportedBitDepths = bitDepths,
                supportedOutputFormats = pixFmts
            )
        }

        return CodecReport(
            mimeType = mimeType,
            isHardwareAccelerated = hasHw,
            isSoftwareFallbackAvailable = false,
            softwareEngine = null,
            supportedProfiles = emptyList(),
            supportedBitDepths = emptyList(),
            supportedOutputFormats = emptyList()
        )
    }

    fun getAllCodecReports(): List<CodecReport> {
        return VideoDecoderCapabilities.getSupportedMimeTypes().map { getCodecReport(it) }
    }

    fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") &&
            !name.startsWith("c2.android.") &&
            !name.contains("sw") &&
            !name.contains("software")
        }
    }
}
