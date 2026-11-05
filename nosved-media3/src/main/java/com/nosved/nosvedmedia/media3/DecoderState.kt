package com.nosved.nosvedmedia.media3

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

@UnstableApi
enum class DecoderState {
    IDLE,
    REQUESTED,
    CANDIDATE_SELECTED,
    INITIALIZING,
    INITIALIZED,
    FIRST_FRAME_DECODED,
    PLAYING,
    FALLBACK_PENDING,
    FAILED,
    RELEASED
}

@UnstableApi
enum class TrackType {
    VIDEO,
    AUDIO
}

@UnstableApi
enum class DecodingCategory {
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_PROFILE,
    DECODER_INITIALIZATION_FAILED,
    DECODING_FAILED,
    INVALID_CODEC_CONFIGURATION,
    SURFACE_RENDERING_FAILED,
    AUDIO_OUTPUT_FAILED,
    MEMORY_ALLOCATION_FAILED,
    SOURCE_ERROR
}

@UnstableApi
data class TrackDecoderStatus(
    val trackType: TrackType,
    val state: DecoderState = DecoderState.IDLE,
    val requestedPreference: DecoderPreference = DecoderPreference.AUTO,
    val selectedRenderer: String? = null,
    val actualDecoderEngine: String? = null,
    val isHardware: Boolean = false,
    val format: Format? = null,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val bitDepth: Int = 8,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val initializedTimestampMs: Long = 0L,
    val firstFrameTimestampMs: Long = 0L,
    val fallbackReason: String? = null,
    val lastError: StructuredPlaybackError? = null
) {
    val isActivelyDecoding: Boolean
        get() = state == DecoderState.FIRST_FRAME_DECODED || state == DecoderState.PLAYING
}

@UnstableApi
data class StructuredPlaybackError(
    val category: DecodingCategory,
    val trackType: TrackType,
    val decoderEngine: String,
    val errorCode: Int = -1,
    val errorMessage: String,
    val isFatal: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

