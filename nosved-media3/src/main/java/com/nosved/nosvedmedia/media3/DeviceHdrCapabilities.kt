package com.nosved.nosvedmedia.media3

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

@UnstableApi
data class DeviceHdrCapabilities(
    val isHdrSupported: Boolean,
    val isWideColorGamut: Boolean,
    val supportsHdr10: Boolean,
    val supportsHlg: Boolean,
    val supportsHdr10Plus: Boolean,
    val supportsDolbyVision: Boolean,
    val maxLuminance: Float,
    val maxAverageLuminance: Float,
    val minLuminance: Float
) {

    fun isFormatHdr(format: Format): Boolean {
        val colorInfo = format.colorInfo ?: return false

        return colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG ||
                colorInfo.colorSpace == C.COLOR_SPACE_BT2020
    }

    fun needsToneMapping(format: Format): Boolean {
        if (!isFormatHdr(format)) return false
        // If device display does not support HDR, tone mapping is required
        if (!isHdrSupported) return true

        val colorInfo = format.colorInfo ?: return false
        return when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> !supportsHdr10
            C.COLOR_TRANSFER_HLG -> !supportsHlg
            else -> false
        }
    }

    companion object {
        @JvmStatic
        fun create(context: Context): DeviceHdrCapabilities {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display: Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

            if (display == null) {
                return DeviceHdrCapabilities(
                    isHdrSupported = false,
                    isWideColorGamut = false,
                    supportsHdr10 = false,
                    supportsHlg = false,
                    supportsHdr10Plus = false,
                    supportsDolbyVision = false,
                    maxLuminance = 0f,
                    maxAverageLuminance = 0f,
                    minLuminance = 0f
                )
            }

            val isWideColorGamut = display.isWideColorGamut
            val hdrCapabilities: Display.HdrCapabilities? = display.hdrCapabilities

            if (hdrCapabilities == null) {
                return DeviceHdrCapabilities(
                    isHdrSupported = false,
                    isWideColorGamut = isWideColorGamut,
                    supportsHdr10 = false,
                    supportsHlg = false,
                    supportsHdr10Plus = false,
                    supportsDolbyVision = false,
                    maxLuminance = 0f,
                    maxAverageLuminance = 0f,
                    minLuminance = 0f
                )
            }

            val supportedTypes: IntArray = hdrCapabilities.supportedHdrTypes
            var hasHdr10 = false
            var hasHlg = false
            var hasHdr10Plus = false
            var hasDolbyVision = false

            for (type in supportedTypes) {
                when (type) {
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> hasHdr10 = true
                    Display.HdrCapabilities.HDR_TYPE_HLG -> hasHlg = true
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> hasDolbyVision = true
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            type == Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS
                        ) {
                            hasHdr10Plus = true
                        }
                    }
                }
            }

            val isHdrSupported = supportedTypes.isNotEmpty()

            return DeviceHdrCapabilities(
                isHdrSupported = isHdrSupported,
                isWideColorGamut = isWideColorGamut,
                supportsHdr10 = hasHdr10,
                supportsHlg = hasHlg,
                supportsHdr10Plus = hasHdr10Plus,
                supportsDolbyVision = hasDolbyVision,
                maxLuminance = hdrCapabilities.desiredMaxLuminance,
                maxAverageLuminance = hdrCapabilities.desiredMaxAverageLuminance,
                minLuminance = hdrCapabilities.desiredMinLuminance
            )
        }
    }
}

