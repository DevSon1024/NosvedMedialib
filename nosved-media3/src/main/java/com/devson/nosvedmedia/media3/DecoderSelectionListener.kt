package com.devson.nosvedmedia.media3

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

@UnstableApi
fun interface DecoderSelectionListener {
    fun onDecoderSelected(rendererName: String, isSoftware: Boolean, format: Format)
}

