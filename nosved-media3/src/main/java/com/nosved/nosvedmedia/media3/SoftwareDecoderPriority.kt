package com.nosved.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

@UnstableApi
enum class SoftwareDecoderPriority {
    FALLBACK,
    PREFER,
    DISABLED;

    fun toDecoderPreference(): DecoderPreference = when (this) {
        PREFER -> DecoderPreference.SOFTWARE_PREFERRED
        FALLBACK -> DecoderPreference.PLATFORM_PREFERRED
        DISABLED -> DecoderPreference.PLATFORM_ONLY
    }

    fun toDecoderPreferences(allowFallback: Boolean = true): DecoderPreferences =
        DecoderPreferences.fromSoftwareDecoderPriority(this, allowFallback)
}
