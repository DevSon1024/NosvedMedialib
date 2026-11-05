package com.nosved.nosvedmedia.media3

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import kotlin.math.max
import kotlin.math.min

@UnstableApi
enum class ThreadPolicy {
    AUTO,
    LOW_LATENCY,
    THROUGHPUT,
    POWER_SAVE
}

@UnstableApi
data class ThreadingConfig(
    val threadCount: Int,
    val threadType: Int = 0 // 0 = AUTO, 1 = FRAME, 2 = SLICE, 3 = FRAME|SLICE
)

@UnstableApi
object AdaptiveThreadingPolicy {

    fun determineThreading(
        mimeType: String,
        width: Int,
        height: Int,
        policy: ThreadPolicy = ThreadPolicy.AUTO,
        thermalStatus: ThermalStatus = ThermalStatus.NONE,
        availableCores: Int = Runtime.getRuntime().availableProcessors()
    ): ThreadingConfig {
        val totalPixels = if (width > 0 && height > 0) width * height else 1920 * 1080
        val isUhd = totalPixels >= 3840 * 2160
        val isHd = totalPixels >= 1280 * 720
        val isHevcOrAv1 = mimeType == MimeTypes.VIDEO_H265 || mimeType == MimeTypes.VIDEO_AV1

        // Base threads calculated from resolution and core topology
        val baseThreads = when {
            isUhd -> min(6, max(4, availableCores - 2))
            isHd && isHevcOrAv1 -> min(4, max(2, availableCores / 2))
            isHd -> min(3, max(2, availableCores / 2))
            else -> min(2, max(1, availableCores / 4))
        }

        // Apply policy
        var threads = when (policy) {
            ThreadPolicy.LOW_LATENCY -> min(2, baseThreads)
            ThreadPolicy.THROUGHPUT -> min(availableCores, baseThreads + 1)
            ThreadPolicy.POWER_SAVE -> max(1, baseThreads - 1)
            ThreadPolicy.AUTO -> baseThreads
        }

        // Apply thermal mitigation
        threads = when (thermalStatus) {
            ThermalStatus.NONE, ThermalStatus.LIGHT -> threads
            ThermalStatus.MODERATE -> max(2, threads - 1)
            ThermalStatus.SEVERE -> 2
            ThermalStatus.CRITICAL, ThermalStatus.EMERGENCY, ThermalStatus.SHUTDOWN -> 1
        }

        // Thread type: 1 = Frame, 2 = Slice, 3 = Frame | Slice
        val threadType = when (policy) {
            ThreadPolicy.LOW_LATENCY -> 2 // Slice threading for low latency
            ThreadPolicy.THROUGHPUT -> 1  // Frame threading for high throughput
            else -> if (isHd) 3 else 2
        }

        return ThreadingConfig(threads, threadType)
    }
}

