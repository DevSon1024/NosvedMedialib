package com.nosved.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

@UnstableApi
enum class ThermalStatus(val apiValue: Int) {
    NONE(0),
    LIGHT(1),
    MODERATE(2),
    SEVERE(3),
    CRITICAL(4),
    EMERGENCY(5),
    SHUTDOWN(6);

    companion object {
        fun fromApiValue(value: Int): ThermalStatus {
            return entries.firstOrNull { it.apiValue == value } ?: NONE
        }
    }
}

@UnstableApi
data class DecoderDiagnostics(
    val decoderName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bitDepth: Int = 8,
    val isHardware: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

@UnstableApi
data class DecodingPerformanceMetrics(
    val totalFramesDecoded: Long = 0L,
    val totalFramesDropped: Long = 0L,
    val totalDecodeTimeMs: Double = 0.0,
    val averageDecodeTimeMs: Double = 0.0,
    val maxDecodeTimeMs: Double = 0.0,
    val minDecodeTimeMs: Double = 0.0,
    val currentFps: Double = 0.0,
    val memoryUsageMb: Double = 0.0,
    val audioProcessingLatencyMs: Double = 0.0,
    val thermalStatus: ThermalStatus = ThermalStatus.NONE,
    val activeThreadCount: Int = 0
)

@UnstableApi
data class DecodingError(
    val decoderName: String,
    val errorCode: Int,
    val errorMessage: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isFatal: Boolean = false
)

@UnstableApi
interface PlaybackDiagnosticsListener {
    fun onDecoderInitialized(diagnostics: DecoderDiagnostics) {}
    fun onPerformanceMetricsUpdated(metrics: DecodingPerformanceMetrics) {}
    fun onDecodingError(error: DecodingError) {}

    // Lifecycle and state callbacks
    fun onDecoderStateChanged(status: TrackDecoderStatus) {}
    fun onFirstFrameRendered(trackType: TrackType, decoderEngine: String, ptsUs: Long) {}
    fun onStructuredError(error: StructuredPlaybackError) {}
    fun onThermalStatusChanged(status: ThermalStatus) {}
}
