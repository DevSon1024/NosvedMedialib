package com.devson.nosvedmedia.media3

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

    fun recordDecodeTime(durationNs: Long) {
        val currentMetrics: DecodingPerformanceMetrics
        synchronized(lock) {
            totalFramesDecoded++
            totalDecodeTimeNs += durationNs
            minDecodeTimeNs = min(minDecodeTimeNs, durationNs)
            maxDecodeTimeNs = max(maxDecodeTimeNs, durationNs)

            val now = System.nanoTime()
            windowTimestamps[windowIndex] = now
            windowIndex = (windowIndex + 1) % sampleWindowFrames
            if (windowCount < sampleWindowFrames) {
                windowCount++
            }

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
                memoryUsageMb = memMb
            )
        }

        listener?.onPerformanceMetricsUpdated(currentMetrics)
    }

    fun recordDroppedFrame() {
        synchronized(lock) {
            totalFramesDropped++
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
                memoryUsageMb = memMb
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
        }
    }
}

