package com.devson.nosvedmedia.media3

import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException

@UnstableApi
class FFmpegDecoderException(
    message: String,
    val errorCode: Int = 0,
    cause: Throwable? = null
) : DecoderException(message, cause)

