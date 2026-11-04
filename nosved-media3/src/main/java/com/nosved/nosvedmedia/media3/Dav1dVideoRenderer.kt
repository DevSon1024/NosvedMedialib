package com.nosved.nosvedmedia.media3

import android.os.Handler
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.VideoDecoderOutputBuffer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.video.DecoderVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

@UnstableApi
class Dav1dVideoRenderer(
    allowedJoiningTimeMs: Long = 5000L,
    eventHandler: Handler? = null,
    eventListener: VideoRendererEventListener? = null,
    maxDroppedFramesToNotify: Int = 50,
    private val config: VideoDecoderConfig = VideoDecoderConfig(),
    private val selectionListener: DecoderSelectionListener? = null,
    private val diagnosticsListener: PlaybackDiagnosticsListener? = null
) : DecoderVideoRenderer(
    allowedJoiningTimeMs,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {

    private val performanceMonitor = PerformanceMonitor(diagnosticsListener)
    private var activeDecoder: Dav1dVideoDecoder? = null

    override fun getName(): String = "Dav1dVideoRenderer"

    fun getPerformanceMetrics(): DecodingPerformanceMetrics = performanceMonitor.getSnapshot()

    override fun supportsFormat(format: Format): Int {
        if (format.sampleMimeType != MimeTypes.VIDEO_AV1) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        return RendererCapabilities.create(
            C.FORMAT_HANDLED,
            RendererCapabilities.ADAPTIVE_SEAMLESS,
            RendererCapabilities.TUNNELING_NOT_SUPPORTED
        )
    }

    override fun createDecoder(
        format: Format,
        cryptoConfig: CryptoConfig?
    ): Decoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FFmpegDecoderException> {
        if (cryptoConfig != null) {
            throw FFmpegDecoderException("Encrypted content is not supported by dav1d software decoder")
        }
        selectionListener?.onDecoderSelected(getName(), true, format)
        val decoder = Dav1dVideoDecoder(
            format = format,
            config = config,
            performanceMonitor = performanceMonitor,
            diagnosticsListener = diagnosticsListener
        )
        activeDecoder = decoder
        return decoder
    }

    override fun dropOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        performanceMonitor.recordDroppedFrame()
        super.dropOutputBuffer(outputBuffer)
    }

    override fun renderOutputBufferToSurface(
        outputBuffer: VideoDecoderOutputBuffer,
        surface: Surface
    ) {
        val decoder = activeDecoder
        if (decoder != null) {
            decoder.renderToSurface(outputBuffer, surface)
        }
        outputBuffer.release()
    }

    override fun setDecoderOutputMode(outputMode: Int) {
        // Output mode is handled natively by rendering to Surface via ANativeWindow
    }

    override fun onDisabled() {
        super.onDisabled()
        activeDecoder = null
    }
}

