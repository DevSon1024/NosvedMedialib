package com.nosved.nosvedmedia.media3

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executors

@UnstableApi
enum class ThermalMitigationPolicy {
    DISABLED,
    BALANCED,
    COOLING_PRIORITY
}

@UnstableApi
class ThermalThrottlingMonitor(
    private val context: Context,
    private val diagnosticsListener: PlaybackDiagnosticsListener? = null,
    val policy: ThermalMitigationPolicy = ThermalMitigationPolicy.BALANCED
) {
    private var powerManager: PowerManager? = null
    private var thermalListener: Any? = null
    private val executor = Executors.newSingleThreadExecutor()

    var currentStatus: ThermalStatus = ThermalStatus.NONE
        private set

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager = pm
            if (pm != null) {
                try {
                    val initialStatus = pm.currentThermalStatus
                    currentStatus = ThermalStatus.fromApiValue(initialStatus)
                    diagnosticsListener?.onThermalStatusChanged(currentStatus)

                    val listener = PowerManager.OnThermalStatusChangedListener { status ->
                        val newStatus = ThermalStatus.fromApiValue(status)
                        currentStatus = newStatus
                        diagnosticsListener?.onThermalStatusChanged(newStatus)
                    }
                    pm.addThermalStatusListener(executor, listener)
                    thermalListener = listener
                } catch (e: Exception) {
                    // Non-fatal thermal listener initialization
                }
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = powerManager
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener
            if (pm != null && listener != null) {
                try {
                    pm.removeThermalStatusListener(listener)
                } catch (e: Exception) {
                    // Ignore on release
                }
            }
        }
        executor.shutdown()
        powerManager = null
        thermalListener = null
    }

    fun getRecommendedScalingMode(configuredMode: VideoScalingMode): VideoScalingMode {
        if (policy == ThermalMitigationPolicy.DISABLED) return configuredMode

        return when (currentStatus) {
            ThermalStatus.SEVERE,
            ThermalStatus.CRITICAL,
            ThermalStatus.EMERGENCY,
            ThermalStatus.SHUTDOWN -> VideoScalingMode.FAST_BILINEAR
            ThermalStatus.MODERATE -> {
                if (policy == ThermalMitigationPolicy.COOLING_PRIORITY) {
                    VideoScalingMode.FAST_BILINEAR
                } else {
                    configuredMode
                }
            }
            else -> configuredMode
        }
    }
}

