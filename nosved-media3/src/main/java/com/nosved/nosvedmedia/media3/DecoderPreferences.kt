package com.nosved.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

/**
 * Defines preference mode for codec selection across platform (hardware/MediaCodec)
 * and custom software decoding engines (FFmpeg, dav1d).
 */
@UnstableApi
enum class DecoderPreference(val displayName: String) {
    /**
     * Automatically inspects stream attributes (resolution, bit depth, codec profile)
     * and device capabilities to select the optimal decoding pipeline.
     */
    AUTO("Auto"),

    /**
     * Prioritizes platform MediaCodec hardware decoding.
     * Custom software renderers act as backup if fallback is permitted.
     */
    PLATFORM_PREFERRED("Hardware Preferred"),

    /**
     * Prioritizes compatible custom software decoders (dav1d for AV1, FFmpeg for others).
     * Platform decoders act as backup if fallback is permitted.
     */
    SOFTWARE_PREFERRED("Software Preferred"),

    /**
     * Exclusively uses platform decoders. Custom software decoders are never registered.
     */
    PLATFORM_ONLY("Hardware Only"),

    /**
     * Exclusively uses custom software decoders. Platform decoders are never registered.
     */
    SOFTWARE_ONLY("Software Only");

    companion object {
        @JvmStatic
        fun fromString(value: String?): DecoderPreference {
            if (value == null) return AUTO
            return try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) } ?: AUTO
            }
        }
    }
}

/**
 * Encapsulates independent decoder selection preferences for video and audio pipelines.
 */
@UnstableApi
data class DecoderPreferences(
    val video: DecoderPreference = DecoderPreference.AUTO,
    val audio: DecoderPreference = DecoderPreference.AUTO,
    val allowFallback: Boolean = true
) {
    fun withVideo(video: DecoderPreference): DecoderPreferences = copy(video = video)
    fun withAudio(audio: DecoderPreference): DecoderPreferences = copy(audio = audio)
    fun withAllowFallback(allow: Boolean): DecoderPreferences = copy(allowFallback = allow)

    companion object {
        val DEFAULT = DecoderPreferences()

        val HARDWARE_PREFERRED = DecoderPreferences(
            video = DecoderPreference.PLATFORM_PREFERRED,
            audio = DecoderPreference.PLATFORM_PREFERRED,
            allowFallback = true
        )

        val SOFTWARE_PREFERRED_ALL = DecoderPreferences(
            video = DecoderPreference.SOFTWARE_PREFERRED,
            audio = DecoderPreference.SOFTWARE_PREFERRED,
            allowFallback = true
        )

        val SOFTWARE_ONLY_ALL = DecoderPreferences(
            video = DecoderPreference.SOFTWARE_ONLY,
            audio = DecoderPreference.SOFTWARE_ONLY,
            allowFallback = false
        )

        @JvmStatic
        fun fromLegacyMode(legacyMode: String?): DecoderPreferences {
            if (legacyMode == null) return DEFAULT
            return when (legacyMode.uppercase().trim()) {
                "HW", "HARDWARE" -> DecoderPreferences(
                    video = DecoderPreference.PLATFORM_ONLY,
                    audio = DecoderPreference.PLATFORM_ONLY,
                    allowFallback = false
                )
                "HW_PLUS", "HW+", "HARDWARE_PLUS" -> DecoderPreferences(
                    video = DecoderPreference.PLATFORM_PREFERRED,
                    audio = DecoderPreference.PLATFORM_PREFERRED,
                    allowFallback = true
                )
                "SW", "SOFTWARE" -> DecoderPreferences(
                    video = DecoderPreference.SOFTWARE_ONLY,
                    audio = DecoderPreference.SOFTWARE_ONLY,
                    allowFallback = false
                )
                else -> DEFAULT
            }
        }

        @JvmStatic
        fun fromSoftwareDecoderPriority(
            priority: SoftwareDecoderPriority,
            allowFallback: Boolean = true
        ): DecoderPreferences {
            return when (priority) {
                SoftwareDecoderPriority.PREFER -> DecoderPreferences(
                    video = DecoderPreference.SOFTWARE_PREFERRED,
                    audio = DecoderPreference.SOFTWARE_PREFERRED,
                    allowFallback = allowFallback
                )
                SoftwareDecoderPriority.FALLBACK -> DecoderPreferences(
                    video = DecoderPreference.PLATFORM_PREFERRED,
                    audio = DecoderPreference.PLATFORM_PREFERRED,
                    allowFallback = allowFallback
                )
                SoftwareDecoderPriority.DISABLED -> DecoderPreferences(
                    video = DecoderPreference.PLATFORM_ONLY,
                    audio = DecoderPreference.PLATFORM_PREFERRED, // Keep SW audio fallback for DTS/DDP
                    allowFallback = allowFallback
                )
            }
        }
    }
}

