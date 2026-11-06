package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@UnstableApi
object CodecCompatibilityEvaluator {

    enum class EvaluationStage {
        COMPILED,
        ADVERTISED,
        INITIALIZABLE,
        DECODABLE
    }

    data class DecoderCandidate(
        val name: String,
        val isHardware: Boolean,
        val engine: String,
        val highestStagePassed: EvaluationStage,
        val isFullyCompatible: Boolean,
        val rejectionReasons: List<String> = emptyList()
    )

    data class CompatibilityAssessment(
        val format: Format,
        val isSupported: Boolean,
        val recommendedDecoder: DecoderCandidate?,
        val candidateDecoders: List<DecoderCandidate>,
        val rejectionReasons: List<String>,
        val isHardwareAccelerated: Boolean,
        val isSoftwareFallbackAvailable: Boolean,
        val diagnosticSummary: String
    )

    private const val MAX_SOFTWARE_WIDTH = 4096
    private const val MAX_SOFTWARE_HEIGHT = 2304

    fun evaluateFormat(
        format: Format,
        preference: DecoderPreference = DecoderPreference.AUTO
    ): CompatibilityAssessment {
        val mimeType = format.sampleMimeType
        if (mimeType.isNullOrBlank()) {
            return CompatibilityAssessment(
                format = format,
                isSupported = false,
                recommendedDecoder = null,
                candidateDecoders = emptyList(),
                rejectionReasons = listOf("Stream Format has null or empty sampleMimeType"),
                isHardwareAccelerated = false,
                isSoftwareFallbackAvailable = false,
                diagnosticSummary = "Incompatible: Missing sampleMimeType"
            )
        }

        val candidates = mutableListOf<DecoderCandidate>()
        val overallRejections = mutableListOf<String>()

        // 1. Evaluate Platform Hardware / Software Decoders
        val platformDecoders = PlatformCodecInspector.findDecodersForMimeType(mimeType)
        for (pCodec in platformDecoders) {
            val eval = evaluatePlatformDecoder(pCodec, format)
            candidates.add(eval)
        }

        // 2. Evaluate Software Custom Decoders (dav1d for AV1, FFmpeg for others)
        if (mimeType.equals(MimeTypes.VIDEO_AV1, ignoreCase = true)) {
            val dav1dEval = evaluateDav1dDecoder(format)
            candidates.add(dav1dEval)
        } else if (MimeTypes.isVideo(mimeType)) {
            val ffmpegVideoEval = evaluateFFmpegVideoDecoder(format, mimeType)
            if (ffmpegVideoEval != null) {
                candidates.add(ffmpegVideoEval)
            }
        } else if (MimeTypes.isAudio(mimeType)) {
            val ffmpegAudioEval = evaluateFFmpegAudioDecoder(format, mimeType)
            if (ffmpegAudioEval != null) {
                candidates.add(ffmpegAudioEval)
            }
        }

        val compatibleCandidates = candidates.filter { it.isFullyCompatible }
        val recommended = selectRecommendedDecoder(compatibleCandidates, preference)

        val hasHw = compatibleCandidates.any { it.isHardware }
        val hasSw = compatibleCandidates.any { !it.isHardware }

        if (compatibleCandidates.isEmpty()) {
            candidates.forEach { c ->
                c.rejectionReasons.forEach { r ->
                    if (!overallRejections.contains(r)) {
                        overallRejections.add("[${c.name}] $r")
                    }
                }
            }
            if (overallRejections.isEmpty()) {
                overallRejections.add("No compiled or compatible decoder found for $mimeType")
            }
        }

        val summary = if (recommended != null) {
            "Compatible via ${recommended.name} (${if (recommended.isHardware) "Hardware" else "Software"})"
        } else {
            "Incompatible: ${overallRejections.joinToString("; ")}"
        }

        return CompatibilityAssessment(
            format = format,
            isSupported = recommended != null,
            recommendedDecoder = recommended,
            candidateDecoders = candidates,
            rejectionReasons = overallRejections,
            isHardwareAccelerated = hasHw,
            isSoftwareFallbackAvailable = hasSw,
            diagnosticSummary = summary
        )
    }

    private fun evaluatePlatformDecoder(
        codecInfo: PlatformCodecInspector.PlatformCodecInfo,
        format: Format
    ): DecoderCandidate {
        val rejections = mutableListOf<String>()
        var highestStage = EvaluationStage.COMPILED
        val mimeType = format.sampleMimeType ?: ""

        val capability = codecInfo.mimeCapabilities[mimeType]
            ?: codecInfo.mimeCapabilities.entries.firstOrNull {
                it.key.equals(mimeType, ignoreCase = true)
            }?.value

        if (capability == null) {
            rejections.add("Platform decoder does not advertise support for MIME: $mimeType")
            return DecoderCandidate(
                name = codecInfo.name,
                isHardware = codecInfo.isHardwareAccelerated,
                engine = if (codecInfo.isHardwareAccelerated) "Platform MediaCodec HW" else "Platform MediaCodec SW",
                highestStagePassed = highestStage,
                isFullyCompatible = false,
                rejectionReasons = rejections
            )
        }
        highestStage = EvaluationStage.ADVERTISED

        // Check DRM
        if (format.cryptoType != C.CRYPTO_TYPE_NONE && !capability.isSecurePlaybackSupported) {
            rejections.add("DRM encrypted stream requires secure playback which is unsupported by ${codecInfo.name}")
        }

        // Check Resolution limits
        val w = format.width
        val h = format.height
        if (w != Format.NO_VALUE && h != Format.NO_VALUE) {
            if (w < capability.minWidth || w > capability.maxWidth) {
                rejections.add("Width $w exceeds supported range [${capability.minWidth}..${capability.maxWidth}]")
            }
            if (h < capability.minHeight || h > capability.maxHeight) {
                rejections.add("Height $h exceeds supported range [${capability.minHeight}..${capability.maxHeight}]")
            }
        }

        // Check Framerate limits
        val fps = format.frameRate
        if (fps != Format.NO_VALUE.toFloat() && fps > 0f) {
            if (fps > capability.maxFrameRate) {
                rejections.add("Frame rate $fps exceeds decoder max ${capability.maxFrameRate}")
            }
        }

        // Check Bit Depth & 10-bit profile
        val bitDepth = detectBitDepth(format)
        if (bitDepth > 8 && !capability.supportedBitDepths.contains(bitDepth)) {
            rejections.add("$bitDepth-bit content is unsupported by ${codecInfo.name} (supports ${capability.supportedBitDepths})")
        }

        if (rejections.isEmpty()) {
            highestStage = EvaluationStage.INITIALIZABLE
            highestStage = EvaluationStage.DECODABLE
        }

        return DecoderCandidate(
            name = codecInfo.name,
            isHardware = codecInfo.isHardwareAccelerated,
            engine = if (codecInfo.isHardwareAccelerated) "Platform MediaCodec HW" else "Platform MediaCodec SW",
            highestStagePassed = highestStage,
            isFullyCompatible = rejections.isEmpty(),
            rejectionReasons = rejections
        )
    }

    private fun evaluateDav1dDecoder(format: Format): DecoderCandidate {
        val rejections = mutableListOf<String>()
        var highestStage = EvaluationStage.COMPILED

        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            rejections.add("Software dav1d decoder does not support DRM encrypted streams")
        }

        val w = format.width
        val h = format.height
        val caps = Dav1dCodecInspector.getCapabilities()
        if (w != Format.NO_VALUE && h != Format.NO_VALUE) {
            if (w > caps.maxSoftwareWidth || h > caps.maxSoftwareHeight) {
                rejections.add("Resolution ${w}x$h exceeds software 4K limit (${caps.maxSoftwareWidth}x${caps.maxSoftwareHeight})")
            }
        }

        if (!Dav1dCodecInspector.isCompiled()) {
            rejections.add("dav1d native AV1 library is not loaded or not compiled")
        } else {
            highestStage = EvaluationStage.ADVERTISED
            if (rejections.isEmpty()) {
                highestStage = EvaluationStage.INITIALIZABLE
                highestStage = EvaluationStage.DECODABLE
            }
        }

        return DecoderCandidate(
            name = "dav1d",
            isHardware = false,
            engine = "NosvedMedia dav1d",
            highestStagePassed = highestStage,
            isFullyCompatible = rejections.isEmpty(),
            rejectionReasons = rejections
        )
    }

    private fun evaluateFFmpegVideoDecoder(format: Format, mimeType: String): DecoderCandidate? {
        val decoderName = FFmpegCodecInspector.mapMimeTypeToDecoderName(mimeType) ?: return null
        val rejections = mutableListOf<String>()
        var highestStage = EvaluationStage.COMPILED

        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            rejections.add("Software FFmpeg decoder does not support DRM encrypted streams")
        }

        val w = format.width
        val h = format.height
        if (w != Format.NO_VALUE && h != Format.NO_VALUE) {
            if (w > MAX_SOFTWARE_WIDTH || h > MAX_SOFTWARE_HEIGHT) {
                rejections.add("Resolution ${w}x$h exceeds software 4K limit (${MAX_SOFTWARE_WIDTH}x${MAX_SOFTWARE_HEIGHT})")
            }
        }

        val bitDepth = detectBitDepth(format)
        val compiledInfo = FFmpegCodecInspector.getDecoderByName(decoderName)
        if (compiledInfo != null && compiledInfo.bitDepths.isNotEmpty() && !compiledInfo.bitDepths.contains(bitDepth)) {
            rejections.add("$bitDepth-bit video is unsupported by FFmpeg $decoderName (supports ${compiledInfo.bitDepths})")
        }

        if (!FFmpegCodecInspector.isDecoderCompiled(decoderName)) {
            rejections.add("FFmpeg decoder '$decoderName' is not compiled in native build")
        } else {
            highestStage = EvaluationStage.ADVERTISED
            if (rejections.isEmpty()) {
                highestStage = EvaluationStage.INITIALIZABLE
                highestStage = EvaluationStage.DECODABLE
            }
        }

        return DecoderCandidate(
            name = "ffmpeg_$decoderName",
            isHardware = false,
            engine = "NosvedMedia FFmpeg Video",
            highestStagePassed = highestStage,
            isFullyCompatible = rejections.isEmpty(),
            rejectionReasons = rejections
        )
    }

    private fun evaluateFFmpegAudioDecoder(format: Format, mimeType: String): DecoderCandidate? {
        val decoderName = FFmpegCodecInspector.mapMimeTypeToDecoderName(mimeType) ?: return null
        val rejections = mutableListOf<String>()
        var highestStage = EvaluationStage.COMPILED

        if (!FFmpegCodecInspector.isDecoderCompiled(decoderName)) {
            rejections.add("FFmpeg audio decoder '$decoderName' is not compiled in native build")
            return DecoderCandidate(
                name = "ffmpeg_$decoderName",
                isHardware = false,
                engine = "NosvedMedia FFmpeg Audio",
                highestStagePassed = highestStage,
                isFullyCompatible = false,
                rejectionReasons = rejections
            )
        }
        highestStage = EvaluationStage.ADVERTISED

        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            rejections.add("Software FFmpeg audio decoder does not support DRM encrypted streams")
        }

        if (rejections.isEmpty()) {
            highestStage = EvaluationStage.INITIALIZABLE
            highestStage = EvaluationStage.DECODABLE
        }

        return DecoderCandidate(
            name = "ffmpeg_$decoderName",
            isHardware = false,
            engine = "NosvedMedia FFmpeg Audio",
            highestStagePassed = highestStage,
            isFullyCompatible = rejections.isEmpty(),
            rejectionReasons = rejections
        )
    }

    private fun selectRecommendedDecoder(
        candidates: List<DecoderCandidate>,
        preference: DecoderPreference
    ): DecoderCandidate? {
        if (candidates.isEmpty()) return null

        return when (preference) {
            DecoderPreference.PLATFORM_ONLY -> candidates.firstOrNull { it.isHardware }
            DecoderPreference.SOFTWARE_ONLY -> candidates.firstOrNull { !it.isHardware }
            DecoderPreference.PLATFORM_PREFERRED -> {
                candidates.firstOrNull { it.isHardware } ?: candidates.firstOrNull { !it.isHardware }
            }
            DecoderPreference.SOFTWARE_PREFERRED -> {
                candidates.firstOrNull { !it.isHardware } ?: candidates.firstOrNull { it.isHardware }
            }
            DecoderPreference.AUTO -> {
                candidates.firstOrNull { it.isHardware } ?: candidates.firstOrNull { !it.isHardware }
            }
        }
    }

    private fun detectBitDepth(format: Format): Int {
        val colorInfo: ColorInfo? = format.colorInfo
        if (colorInfo != null) {
            if (colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
            ) {
                return 10
            }
        }

        val codecs = format.codecs?.lowercase() ?: ""
        if (codecs.contains("hev1.2") || codecs.contains("hvc1.2") ||
            codecs.contains(".02.") || codecs.contains("main10") ||
            codecs.contains("vp09.02")
        ) {
            return 10
        }

        return 8
    }
}
