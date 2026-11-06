package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoColorManagementTest {

    @Test
    fun videoScalingMode_nativeValuesAreAccurate() {
        assertEquals(0, VideoScalingMode.FAST_BILINEAR.nativeValue)
        assertEquals(1, VideoScalingMode.BILINEAR.nativeValue)
        assertEquals(2, VideoScalingMode.BICUBIC.nativeValue)
    }

    @Test
    fun videoDecoderConfig_scalingModeAndToneMappingDefaults() {
        val config = VideoDecoderConfig()
        assertEquals(VideoScalingMode.BILINEAR, config.scalingMode)
        assertTrue(config.enableHdrToneMapping)

        val customConfig = VideoDecoderConfig(
            scalingMode = VideoScalingMode.BICUBIC,
            enableHdrToneMapping = false
        )
        assertEquals(VideoScalingMode.BICUBIC, customConfig.scalingMode)
        assertFalse(customConfig.enableHdrToneMapping)
    }

    @Test
    fun deviceHdrCapabilities_detectsHdrFormats() {
        val sdrFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT709)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_SDR)
                    .build()
            )
            .build()

        val hdr10Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build()
            )
            .build()

        val hlgFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_HLG)
                    .build()
            )
            .build()

        val noColorInfoFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .build()

        val dummyCapabilities = DeviceHdrCapabilities(
            isHdrSupported = true,
            isWideColorGamut = true,
            supportsHdr10 = true,
            supportsHlg = true,
            supportsHdr10Plus = false,
            supportsDolbyVision = false,
            maxLuminance = 1000f,
            maxAverageLuminance = 400f,
            minLuminance = 0.005f
        )

        assertFalse(dummyCapabilities.isFormatHdr(sdrFormat))
        assertFalse(dummyCapabilities.isFormatHdr(noColorInfoFormat))
        assertTrue(dummyCapabilities.isFormatHdr(hdr10Format))
        assertTrue(dummyCapabilities.isFormatHdr(hlgFormat))
    }

    @Test
    fun deviceHdrCapabilities_evaluatesToneMappingRequirement() {
        val hdr10Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build()
            )
            .build()

        val sdrCapabilities = DeviceHdrCapabilities(
            isHdrSupported = false,
            isWideColorGamut = false,
            supportsHdr10 = false,
            supportsHlg = false,
            supportsHdr10Plus = false,
            supportsDolbyVision = false,
            maxLuminance = 250f,
            maxAverageLuminance = 250f,
            minLuminance = 0.1f
        )

        val hdr10Capabilities = DeviceHdrCapabilities(
            isHdrSupported = true,
            isWideColorGamut = true,
            supportsHdr10 = true,
            supportsHlg = false,
            supportsHdr10Plus = false,
            supportsDolbyVision = false,
            maxLuminance = 1000f,
            maxAverageLuminance = 500f,
            minLuminance = 0.01f
        )

        // On SDR device, HDR10 requires tone mapping
        assertTrue(sdrCapabilities.needsToneMapping(hdr10Format))

        // On HDR10-capable device, HDR10 does NOT need tone mapping
        assertFalse(hdr10Capabilities.needsToneMapping(hdr10Format))
    }
}

