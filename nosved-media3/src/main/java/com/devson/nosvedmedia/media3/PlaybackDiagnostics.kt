package com.devson.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

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
    val memoryUsageMb: Double = 0.0
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
}

