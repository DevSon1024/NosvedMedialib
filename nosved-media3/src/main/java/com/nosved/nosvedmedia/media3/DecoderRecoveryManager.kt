package com.nosved.nosvedmedia.media3

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Defines a bounded retry and cooldown policy to prevent infinite fallback loops
 * and rapid decoder switching.
 */
@UnstableApi
data class BoundedRetryPolicy(
    val maxRetriesPerTrack: Int = 2,
    val retryCooldownMs: Long = 1000L
)

/**
 * Captures playback session state prior to decoder recovery or preference transition,
 * allowing non-destructive restoration of playback continuity.
 */
@UnstableApi
data class PlaybackStateSnapshot(
    val positionMs: Long = 0L,
    val playWhenReady: Boolean = false,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val repeatMode: Int = 0,
    val shuffleModeEnabled: Boolean = false,
    val selectedAudioTrackIndex: Int = -1,
    val selectedSubtitleTrackIndex: Int = -1
)

/**
 * Encapsulates an individual per-track decoder fallback transition event.
 */
@UnstableApi
data class FallbackEvent(
    val trackType: TrackType,
    val failedEngine: String,
    val targetEngine: String?,
    val reason: String,
    val category: DecodingCategory,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val description: String
        get() = "${trackType.name}: $failedEngine -> ${targetEngine ?: "None"} (${category.name}: $reason)"
}

/**
 * Intelligent decoder recovery controller for per-track failover and bounded safety.
 *
 * Enforces per-track isolation (e.g. video failure does not disrupt audio),
 * bounded retry limits, loop detection, and playback continuity restoration.
 */
@UnstableApi
class DecoderRecoveryManager(
    val policy: BoundedRetryPolicy = BoundedRetryPolicy()
) {
    private val attemptedEngines = mutableMapOf<TrackType, MutableSet<String>>()
    private val retryCounts = mutableMapOf<TrackType, Int>()
    private val lastFailureTimestamp = mutableMapOf<TrackType, Long>()
    private val _fallbackHistory = mutableListOf<FallbackEvent>()

    val fallbackHistory: List<FallbackEvent>
        @Synchronized get() = _fallbackHistory.toList()

    /**
     * Resets recovery state across all tracks for newly loaded media items.
     */
    @Synchronized
    fun reset() {
        attemptedEngines.clear()
        retryCounts.clear()
        lastFailureTimestamp.clear()
        _fallbackHistory.clear()
    }

    /**
     * Determines whether recovery is permissible for the given track and failing engine.
     * Prevents infinite loops, duplicate failures, and rapid cycling.
     */
    @Synchronized
    fun canRecover(trackType: TrackType, failedEngine: String): Boolean {
        val currentRetries = retryCounts[trackType] ?: 0
        if (currentRetries >= policy.maxRetriesPerTrack) {
            return false
        }

        val attempted = attemptedEngines[trackType] ?: emptySet()
        val normalizedEngine = normalizeEngineName(failedEngine)
        if (attempted.contains(normalizedEngine)) {
            return false
        }

        val now = System.currentTimeMillis()
        val lastTime = lastFailureTimestamp[trackType] ?: 0L
        if (now - lastTime < policy.retryCooldownMs && currentRetries > 0) {
            return false
        }

        return true
    }

    /**
     * Resolves the next candidate [DecoderPreferences] for per-track recovery.
     * Returns null if no viable or unattempted fallback target exists.
     */
    @Synchronized
    fun resolveFallbackPreferences(
        trackType: TrackType,
        failedEngine: String,
        currentPreferences: DecoderPreferences,
        format: Format? = null
    ): DecoderPreferences? {
        if (!canRecover(trackType, failedEngine)) {
            return null
        }

        val isSwEngine = isSoftwareEngine(failedEngine)

        return when (trackType) {
            TrackType.VIDEO -> {
                val nextVideoPref = if (isSwEngine) {
                    // SW Video failure (FFmpeg / dav1d) -> fallback to Platform Hardware
                    val candidate = "MediaCodec"
                    if (isEngineAttempted(TrackType.VIDEO, candidate)) return null
                    DecoderPreference.PLATFORM_PREFERRED
                } else {
                    // HW Video failure (MediaCodec) -> fallback to Software (dav1d / FFmpeg)
                    val mime = format?.sampleMimeType
                    val candidate = if (mime == MimeTypes.VIDEO_AV1) "dav1d" else "FFmpeg"
                    if (isEngineAttempted(TrackType.VIDEO, candidate)) return null
                    DecoderPreference.SOFTWARE_PREFERRED
                }

                // If preferences wouldn't actually change, ensure allowFallback is set
                if (currentPreferences.video == nextVideoPref && currentPreferences.allowFallback) {
                    null
                } else {
                    currentPreferences.copy(video = nextVideoPref, allowFallback = true)
                }
            }
            TrackType.AUDIO -> {
                val nextAudioPref = if (isSwEngine) {
                    // SW Audio failure (FFmpeg) -> fallback to Platform Audio
                    val candidate = "MediaCodecAudio"
                    if (isEngineAttempted(TrackType.AUDIO, candidate)) return null
                    DecoderPreference.PLATFORM_PREFERRED
                } else {
                    // HW Audio failure -> fallback to FFmpeg Audio
                    val candidate = "FFmpegAudio"
                    if (isEngineAttempted(TrackType.AUDIO, candidate)) return null
                    DecoderPreference.SOFTWARE_PREFERRED
                }

                if (currentPreferences.audio == nextAudioPref && currentPreferences.allowFallback) {
                    null
                } else {
                    currentPreferences.copy(audio = nextAudioPref, allowFallback = true)
                }
            }
        }
    }

    /**
     * Records an executed fallback event, advancing the retry counter and tracking the failed engine.
     */
    @Synchronized
    fun recordFallback(event: FallbackEvent) {
        val track = event.trackType
        val normalized = normalizeEngineName(event.failedEngine)

        val set = attemptedEngines.getOrPut(track) { mutableSetOf() }
        set.add(normalized)

        val count = retryCounts[track] ?: 0
        retryCounts[track] = count + 1
        lastFailureTimestamp[track] = System.currentTimeMillis()

        _fallbackHistory.add(event)
    }

    @Synchronized
    fun getRetryCount(trackType: TrackType): Int = retryCounts[trackType] ?: 0

    @Synchronized
    fun isEngineAttempted(trackType: TrackType, engineName: String): Boolean {
        val set = attemptedEngines[trackType] ?: return false
        val normalized = normalizeEngineName(engineName)
        return set.contains(normalized)
    }

    @Synchronized
    fun getFallbackHistoryDescriptions(): List<String> {
        return _fallbackHistory.map { it.description }
    }

    private fun normalizeEngineName(engine: String): String {
        val lower = engine.lowercase()
        return when {
            lower.contains("dav1d") -> "dav1d"
            lower.contains("ffmpeg") && lower.contains("audio") -> "FFmpegAudio"
            lower.contains("ffmpeg") -> "FFmpeg"
            lower.contains("mediacodec") || lower.contains("c2.") || lower.contains("omx.") -> "MediaCodec"
            else -> engine.trim()
        }
    }

    private fun isSoftwareEngine(engine: String): Boolean {
        val lower = engine.lowercase()
        return lower.contains("ffmpeg") ||
                lower.contains("dav1d") ||
                lower.contains(".sw.") ||
                lower.contains("google") ||
                lower.contains("software")
    }
}

