package com.nosved.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

@UnstableApi
data class VideoDecoderConfig(
    val numInputBuffers: Int = 12,
    val numOutputBuffers: Int = 12,
    val initialInputBufferSize: Int = 1024 * 1024,
    val maxOutputWidth: Int = 3840,
    val maxOutputHeight: Int = 2160,
    val threadCount: Int = 0
)

