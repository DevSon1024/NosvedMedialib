package com.nosved.nosvedmedia.media3

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities as AndroidCodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@UnstableApi
object PlatformCodecInspector {

    private const val TAG = "PlatformCodecInspector"

    data class ProfileInfo(
        val profileId: Int,
        val name: String
    )

    data class PlatformMimeCapability(
        val mimeType: String,
        val profiles: List<ProfileInfo>,
        val levels: List<Int>,
        val minWidth: Int,
        val maxWidth: Int,
        val minHeight: Int,
        val maxHeight: Int,
        val widthAlignment: Int,
        val heightAlignment: Int,
        val minFrameRate: Double,
        val maxFrameRate: Double,
        val supportedColorFormats: List<Int>,
        val supportedBitDepths: List<Int>,
        val isHdrSupported: Boolean,
        val isSecurePlaybackSupported: Boolean,
        val isTunneledPlaybackSupported: Boolean,
        val performancePoints: List<String>
    )

    data class PlatformCodecInfo(
        val name: String,
        val isEncoder: Boolean,
        val isHardwareAccelerated: Boolean,
        val isSoftwareOnly: Boolean,
        val isVendor: Boolean,
        val supportedMimeTypes: List<String>,
        val mimeCapabilities: Map<String, PlatformMimeCapability>
    )

    @Volatile
    private var cachedCodecs: List<PlatformCodecInfo>? = null

    fun getPlatformDecoders(onlyHardware: Boolean = false): List<PlatformCodecInfo> {
        val all = getOrInspectPlatformCodecs().filter { !it.isEncoder }
        return if (onlyHardware) {
            all.filter { it.isHardwareAccelerated }
        } else {
            all
        }
    }

    fun findDecodersForMimeType(
        mimeType: String,
        preferHardware: Boolean = true
    ): List<PlatformCodecInfo> {
        val decoders = getPlatformDecoders().filter { codec ->
            codec.supportedMimeTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
        return if (preferHardware) {
            decoders.sortedByDescending { it.isHardwareAccelerated }
        } else {
            decoders
        }
    }

    fun isFormatSupported(format: Format): Boolean {
        val mimeType = format.sampleMimeType ?: return false
        val decoders = findDecodersForMimeType(mimeType)
        if (decoders.isEmpty()) return false

        val width = format.width
        val height = format.height
        val frameRate = format.frameRate

        for (decoder in decoders) {
            val capability = decoder.mimeCapabilities[mimeType]
                ?: decoder.mimeCapabilities.entries.firstOrNull {
                    it.key.equals(mimeType, ignoreCase = true)
                }?.value
                ?: continue

            if (width != Format.NO_VALUE && height != Format.NO_VALUE) {
                if (width < capability.minWidth || width > capability.maxWidth ||
                    height < capability.minHeight || height > capability.maxHeight
                ) {
                    continue
                }
            }

            if (frameRate != Format.NO_VALUE.toFloat() && frameRate > 0f) {
                if (frameRate > capability.maxFrameRate) {
                    continue
                }
            }

            return true
        }

        return false
    }

    @Synchronized
    fun getOrInspectPlatformCodecs(): List<PlatformCodecInfo> {
        val cached = cachedCodecs
        if (cached != null) return cached

        val result = mutableListOf<PlatformCodecInfo>()
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val infos = codecList.codecInfos ?: emptyArray()
            for (info in infos) {
                val parsed = inspectCodec(info)
                if (parsed != null) {
                    result.add(parsed)
                }
            }
        } catch (e: Throwable) {
            try {
                Log.e(TAG, "Failed to inspect platform codecs via MediaCodecList: ${e.message}")
            } catch (_: Throwable) {}
        }

        cachedCodecs = result
        return result
    }

    fun clearCache() {
        cachedCodecs = null
    }

    private fun inspectCodec(info: MediaCodecInfo): PlatformCodecInfo? {
        return try {
            val name = info.name
            val isEncoder = info.isEncoder
            val isHw = determineHardwareAccelerated(info)
            val isSwOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isSoftwareOnly
            } else {
                !isHw
            }
            val isVendor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isVendor
            } else {
                isHw
            }

            val types = info.supportedTypes.toList()
            val mimeCaps = mutableMapOf<String, PlatformMimeCapability>()

            for (type in types) {
                try {
                    val caps = info.getCapabilitiesForType(type)
                    val mimeCap = inspectMimeCapability(type, caps)
                    mimeCaps[type] = mimeCap
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get capabilities for $name ($type): ${e.message}")
                }
            }

            PlatformCodecInfo(
                name = name,
                isEncoder = isEncoder,
                isHardwareAccelerated = isHw,
                isSoftwareOnly = isSwOnly,
                isVendor = isVendor,
                supportedMimeTypes = types,
                mimeCapabilities = mimeCaps
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting codec: ${info.name}", e)
            null
        }
    }

    private fun inspectMimeCapability(
        mimeType: String,
        caps: AndroidCodecCapabilities
    ): PlatformMimeCapability {
        val profileList = mutableListOf<ProfileInfo>()
        val levelList = mutableListOf<Int>()
        val profileLevels = caps.profileLevels ?: emptyArray()

        for (pl in profileLevels) {
            levelList.add(pl.level)
            val profileName = resolveProfileName(mimeType, pl.profile)
            profileList.add(ProfileInfo(pl.profile, profileName))
        }

        var minW = 1
        var maxW = 4096
        var minH = 1
        var maxH = 4096
        var wAlign = 1
        var hAlign = 1
        var minFps = 0.0
        var maxFps = 60.0

        val videoCaps = caps.videoCapabilities
        if (videoCaps != null) {
            try {
                minW = videoCaps.supportedWidths?.lower ?: 1
                maxW = videoCaps.supportedWidths?.upper ?: 4096
                minH = videoCaps.supportedHeights?.lower ?: 1
                maxH = videoCaps.supportedHeights?.upper ?: 4096
                wAlign = videoCaps.widthAlignment
                hAlign = videoCaps.heightAlignment
                minFps = videoCaps.supportedFrameRates?.lower?.toDouble() ?: 0.0
                maxFps = videoCaps.supportedFrameRates?.upper?.toDouble() ?: 60.0
            } catch (e: Exception) {
                Log.w(TAG, "Error querying videoCapabilities for $mimeType: ${e.message}")
            }
        }

        val colorFormats = caps.colorFormats?.toList() ?: emptyList()
        val bitDepths = mutableSetOf(8)

        var hasHdr = false
        if (mimeType.equals(MimeTypes.VIDEO_H265, ignoreCase = true)) {
            val hasMain10 = profileLevels.any {
                it.profile == CodecProfileLevel.HEVCProfileMain10 ||
                it.profile == CodecProfileLevel.HEVCProfileMain10HDR10 ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus)
            }
            if (hasMain10) {
                bitDepths.add(10)
                hasHdr = true
            }
        } else if (mimeType.equals(MimeTypes.VIDEO_VP9, ignoreCase = true)) {
            val hasVp9Profile2 = profileLevels.any {
                it.profile == CodecProfileLevel.VP9Profile2 ||
                it.profile == CodecProfileLevel.VP9Profile2HDR ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.profile == CodecProfileLevel.VP9Profile2HDR10Plus)
            }
            if (hasVp9Profile2) {
                bitDepths.add(10)
                hasHdr = true
            }
        } else if (mimeType.equals(MimeTypes.VIDEO_AV1, ignoreCase = true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasAv1Profile10 = profileLevels.any {
                    it.profile == CodecProfileLevel.AV1ProfileMain10 ||
                    it.profile == CodecProfileLevel.AV1ProfileMain10HDR10 ||
                    it.profile == CodecProfileLevel.AV1ProfileMain10HDR10Plus
                }
                if (hasAv1Profile10) {
                    bitDepths.add(10)
                    hasHdr = true
                }
            }
        }

        // Color format check for 10-bit RGBA or YUV flexible
        if (colorFormats.contains(AndroidCodecCapabilities.COLOR_Format32bitABGR2101010)) {
            bitDepths.add(10)
        }

        val isSecure = try {
            caps.isFeatureSupported(AndroidCodecCapabilities.FEATURE_SecurePlayback)
        } catch (_: Exception) {
            false
        }

        val isTunneled = try {
            caps.isFeatureSupported(AndroidCodecCapabilities.FEATURE_TunneledPlayback)
        } catch (_: Exception) {
            false
        }

        val perfPoints = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && videoCaps != null) {
            try {
                val pPoints = videoCaps.supportedPerformancePoints
                if (pPoints != null) {
                    for (pp in pPoints) {
                        perfPoints.add(pp.toString())
                    }
                }
            } catch (_: Exception) {
                // Performance points may not be exposed on all vendors
            }
        }

        return PlatformMimeCapability(
            mimeType = mimeType,
            profiles = profileList,
            levels = levelList,
            minWidth = minW,
            maxWidth = maxW,
            minHeight = minH,
            maxHeight = maxH,
            widthAlignment = wAlign,
            heightAlignment = hAlign,
            minFrameRate = minFps,
            maxFrameRate = maxFps,
            supportedColorFormats = colorFormats,
            supportedBitDepths = bitDepths.sorted(),
            isHdrSupported = hasHdr,
            isSecurePlaybackSupported = isSecure,
            isTunneledPlaybackSupported = isTunneled,
            performancePoints = perfPoints
        )
    }

    private fun determineHardwareAccelerated(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        val name = info.name.lowercase()
        if (name.startsWith("omx.google.") ||
            name.startsWith("c2.android.") ||
            name.startsWith("omx.ffmpeg.") ||
            name.contains(".sw.") ||
            name.contains("software")
        ) {
            return false
        }
        return name.startsWith("omx.qcom.") ||
               name.startsWith("omx.exynos.") ||
               name.startsWith("omx.mtk.") ||
               name.startsWith("omx.sec.") ||
               name.startsWith("omx.nvidia.") ||
               name.startsWith("c2.qti.") ||
               name.startsWith("c2.exynos.") ||
               name.startsWith("c2.mtk.")
    }

    private fun resolveProfileName(mimeType: String, profile: Int): String {
        return when (mimeType.lowercase()) {
            MimeTypes.VIDEO_H264.lowercase() -> when (profile) {
                CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                CodecProfileLevel.AVCProfileMain -> "Main"
                CodecProfileLevel.AVCProfileExtended -> "Extended"
                CodecProfileLevel.AVCProfileHigh -> "High"
                CodecProfileLevel.AVCProfileHigh10 -> "High 10"
                CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
                CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
                CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
                CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
                else -> "Profile ($profile)"
            }
            MimeTypes.VIDEO_H265.lowercase() -> when (profile) {
                CodecProfileLevel.HEVCProfileMain -> "Main"
                CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
                CodecProfileLevel.HEVCProfileMainStill -> "Main Still Picture"
                CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR10"
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus) {
                    "Main 10 HDR10+"
                } else {
                    "Profile ($profile)"
                }
            }
            MimeTypes.VIDEO_VP9.lowercase() -> when (profile) {
                CodecProfileLevel.VP9Profile0 -> "Profile 0 (8-bit 4:2:0)"
                CodecProfileLevel.VP9Profile1 -> "Profile 1 (8-bit 4:4:4)"
                CodecProfileLevel.VP9Profile2 -> "Profile 2 (10-bit 4:2:0)"
                CodecProfileLevel.VP9Profile3 -> "Profile 3 (10-bit 4:4:4)"
                CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && profile == CodecProfileLevel.VP9Profile2HDR10Plus) {
                    "Profile 2 HDR10+"
                } else {
                    "Profile ($profile)"
                }
            }
            MimeTypes.VIDEO_AV1.lowercase() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    when (profile) {
                        CodecProfileLevel.AV1ProfileMain8 -> "Main 8"
                        CodecProfileLevel.AV1ProfileMain10 -> "Main 10"
                        CodecProfileLevel.AV1ProfileMain10HDR10 -> "Main 10 HDR10"
                        CodecProfileLevel.AV1ProfileMain10HDR10Plus -> "Main 10 HDR10+"
                        else -> "Profile ($profile)"
                    }
                } else {
                    "Profile ($profile)"
                }
            }
            MimeTypes.AUDIO_AAC.lowercase() -> when (profile) {
                CodecProfileLevel.AACObjectLC -> "AAC LC"
                CodecProfileLevel.AACObjectHE -> "HE-AAC"
                CodecProfileLevel.AACObjectHE_PS -> "HE-AAC v2 (PS)"
                CodecProfileLevel.AACObjectLD -> "AAC LD"
                CodecProfileLevel.AACObjectELD -> "AAC ELD"
                CodecProfileLevel.AACObjectMain -> "AAC Main"
                else -> "Profile ($profile)"
            }
            else -> "Profile ($profile)"
        }
    }
}
