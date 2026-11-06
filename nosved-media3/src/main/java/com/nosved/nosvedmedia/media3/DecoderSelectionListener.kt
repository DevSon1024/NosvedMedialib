package com.nosved.nosvedmedia.media3

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

@UnstableApi
fun interface DecoderSelectionListener {
    /**
     * Invoked when a renderer candidate is selected by Media3 track selector.
     */
    fun onDecoderSelected(rendererName: String, isSoftware: Boolean, format: Format)

    /**
     * Invoked only when the decoder has successfully decoded and rendered its first output.
     */
    fun onDecoderActivated(trackType: TrackType, rendererName: String, decoderEngine: String, format: Format) {}

    /**
     * Invoked when a renderer or its decoder is released or disabled.
     */
    fun onDecoderReleased(trackType: TrackType, rendererName: String) {}
}
