package com.devson.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi

@UnstableApi
data class VideoDecoderConfig(
    val numInputBuffers: Int = 4,
    val numOutputBuffers: Int = 4,
    val initialInputBufferSize: Int = 512 * 1024,
    val maxOutputWidth: Int = 3840,
    val maxOutputHeight: Int = 2160
)

