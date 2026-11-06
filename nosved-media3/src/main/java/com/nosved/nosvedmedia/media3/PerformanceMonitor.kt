package com.nosved.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi
import kotlin.math.max
import kotlin.math.min

@UnstableApi
class PerformanceMonitor(
    private val listener: PlaybackDiagnosticsListener? = null,
    private val sampleWindowFrames: Int = 30
) {
    private val lock = Any()
    private var totalFramesDecoded = 0L
    private var totalFramesDropped = 0L
    private var totalDecodeTimeNs = 0L
    private var minDecodeTimeNs = Long.MAX_VALUE
    private var maxDecodeTimeNs = 0L

    private val windowTimestamps = LongArray(sampleWindowFrames)
    private var windowIndex = 0
    private var windowCount = 0

    private var audioLatencyMs = 0.0
    private var currentThermalStatus = ThermalStatus.NONE
    private var activeThreadCount = 0
    private var lastMetricsEmitTimeNs = 0L

    fun recordDecodeTime(durationNs: Long) {
        var currentMetrics: DecodingPerformanceMetrics? = null
        val now = System.nanoTime()
        synchronized(lock) {
            totalFramesDecoded++
            totalDecodeTimeNs += durationNs
            minDecodeTimeNs = min(minDecodeTimeNs, durationNs)
            maxDecodeTimeNs = max(maxDecodeTimeNs, durationNs)

            windowTimestamps[windowIndex] = now
            windowIndex = (windowIndex + 1) % sampleWindowFrames
            if (windowCount < sampleWindowFrames) {
                windowCount++
            }

            // Throttle telemetry notifications to at most 2 Hz (every 500ms) or first frame
            if (now - lastMetricsEmitTimeNs >= 500_000_000L || lastMetricsEmitTimeNs == 0L) {
                lastMetricsEmitTimeNs = now
                val fps = if (windowCount > 1) {
                    val oldestIdx = if (windowCount < sampleWindowFrames) 0 else windowIndex
                    val elapsedNs = now - windowTimestamps[oldestIdx]
                    if (elapsedNs > 0) {
                        ((windowCount - 1) * 1_000_000_000.0) / elapsedNs
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }

                val avgMs = if (totalFramesDecoded > 0) {
                    (totalDecodeTimeNs / 1_000_000.0) / totalFramesDecoded
                } else {
                    0.0
                }

                val minMs = if (minDecodeTimeNs != Long.MAX_VALUE) minDecodeTimeNs / 1_000_000.0 else 0.0
                val maxMs = maxDecodeTimeNs / 1_000_000.0
                val memMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0)

                currentMetrics = DecodingPerformanceMetrics(
                    totalFramesDecoded = totalFramesDecoded,
                    totalFramesDropped = totalFramesDropped,
                    totalDecodeTimeMs = totalDecodeTimeNs / 1_000_000.0,
                    averageDecodeTimeMs = avgMs,
                    maxDecodeTimeMs = maxMs,
                    minDecodeTimeMs = minMs,
                    currentFps = fps,
                    memoryUsageMb = memMb,
                    audioProcessingLatencyMs = audioLatencyMs,
                    thermalStatus = currentThermalStatus,
                    activeThreadCount = activeThreadCount
                )
            }
        }

        currentMetrics?.let { listener?.onPerformanceMetricsUpdated(it) }
    }

    fun recordDroppedFrame() {
        synchronized(lock) {
            totalFramesDropped++
        }
    }

    fun recordAudioLatency(latencyMs: Double) {
        synchronized(lock) {
            audioLatencyMs = latencyMs
        }
    }

    fun updateThermalStatus(status: ThermalStatus) {
        synchronized(lock) {
            currentThermalStatus = status
        }
    }

    fun setActiveThreadCount(count: Int) {
        synchronized(lock) {
            activeThreadCount = count
        }
    }

    fun getSnapshot(): DecodingPerformanceMetrics {
        synchronized(lock) {
            val avgMs = if (totalFramesDecoded > 0) {
                (totalDecodeTimeNs / 1_000_000.0) / totalFramesDecoded
            } else {
                0.0
            }
            val minMs = if (minDecodeTimeNs != Long.MAX_VALUE) minDecodeTimeNs / 1_000_000.0 else 0.0
            val maxMs = maxDecodeTimeNs / 1_000_000.0
            val memMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0)

            return DecodingPerformanceMetrics(
                totalFramesDecoded = totalFramesDecoded,
                totalFramesDropped = totalFramesDropped,
                totalDecodeTimeMs = totalDecodeTimeNs / 1_000_000.0,
                averageDecodeTimeMs = avgMs,
                maxDecodeTimeMs = maxMs,
                minDecodeTimeMs = minMs,
                currentFps = 0.0,
                memoryUsageMb = memMb,
                audioProcessingLatencyMs = audioLatencyMs,
                thermalStatus = currentThermalStatus,
                activeThreadCount = activeThreadCount
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            totalFramesDecoded = 0L
            totalFramesDropped = 0L
            totalDecodeTimeNs = 0L
            minDecodeTimeNs = Long.MAX_VALUE
            maxDecodeTimeNs = 0L
            windowIndex = 0
            windowCount = 0
            audioLatencyMs = 0.0
        }
    }
}
