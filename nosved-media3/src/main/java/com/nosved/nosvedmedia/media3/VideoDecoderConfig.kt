package com.nosved.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

@UnstableApi
enum class VideoScalingMode(val nativeValue: Int) {
    FAST_BILINEAR(0),
    BILINEAR(1),
    BICUBIC(2)
}

@UnstableApi
data class VideoDecoderConfig(
    val numInputBuffers: Int = 12,
    val numOutputBuffers: Int = 12,
    val initialInputBufferSize: Int = 1024 * 1024,
    val maxOutputWidth: Int = 3840,
    val maxOutputHeight: Int = 2160,
    val threadCount: Int = 0,
    val threadPolicy: ThreadPolicy = ThreadPolicy.AUTO,
    val threadType: Int = 0, // 0 = AUTO, 1 = FRAME, 2 = SLICE, 3 = FRAME|SLICE
    val scalingMode: VideoScalingMode = VideoScalingMode.BILINEAR,
    val enableHdrToneMapping: Boolean = true,
    val thermalMitigationPolicy: ThermalMitigationPolicy = ThermalMitigationPolicy.BALANCED
)
