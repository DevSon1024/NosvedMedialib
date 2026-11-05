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
    private var currentFormat: Format? = null
    private var hasRenderedFirstFrame = false

    override fun getName(): String = "Dav1dVideoRenderer"

    fun getPerformanceMetrics(): DecodingPerformanceMetrics = performanceMonitor.getSnapshot()

    override fun supportsFormat(format: Format): Int {
        if (!VideoDecoderCapabilities.isDav1dSupported(format)) {
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
            val error = StructuredPlaybackError(
                category = DecodingCategory.UNSUPPORTED_FORMAT,
                trackType = TrackType.VIDEO,
                decoderEngine = "dav1d AV1 Decoder",
                errorMessage = "Encrypted DRM content is not supported by dav1d software decoder",
                isFatal = true
            )
            diagnosticsListener?.onStructuredError(error)
            throw FFmpegDecoderException("Encrypted content is not supported by dav1d software decoder")
        }

        currentFormat = format
        hasRenderedFirstFrame = false

        selectionListener?.onDecoderSelected(getName(), true, format)
        diagnosticsListener?.onDecoderStateChanged(
            TrackDecoderStatus(
                trackType = TrackType.VIDEO,
                state = DecoderState.INITIALIZING,
                selectedRenderer = getName(),
                actualDecoderEngine = "dav1d AV1 Decoder",
                isHardware = false,
                format = format,
                mimeType = format.sampleMimeType,
                width = format.width,
                height = format.height,
                initializedTimestampMs = System.currentTimeMillis()
            )
        )

        val decoder = Dav1dVideoDecoder(
            format = format,
            config = config,
            performanceMonitor = performanceMonitor,
            diagnosticsListener = diagnosticsListener
        )
        activeDecoder = decoder

        diagnosticsListener?.onDecoderStateChanged(
            TrackDecoderStatus(
                trackType = TrackType.VIDEO,
                state = DecoderState.INITIALIZED,
                selectedRenderer = getName(),
                actualDecoderEngine = "dav1d AV1 Decoder",
                isHardware = false,
                format = format,
                mimeType = format.sampleMimeType,
                width = format.width,
                height = format.height,
                initializedTimestampMs = System.currentTimeMillis()
            )
        )

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
        var rendered = false
        if (decoder != null) {
            rendered = decoder.renderToSurface(outputBuffer, surface)
        }

        if (rendered && !hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            val fmt = currentFormat
            if (fmt != null) {
                selectionListener?.onDecoderActivated(TrackType.VIDEO, getName(), "dav1d AV1 Decoder", fmt)
            }
            diagnosticsListener?.onFirstFrameRendered(TrackType.VIDEO, "dav1d AV1 Decoder", outputBuffer.timeUs)
            diagnosticsListener?.onDecoderStateChanged(
                TrackDecoderStatus(
                    trackType = TrackType.VIDEO,
                    state = DecoderState.PLAYING,
                    selectedRenderer = getName(),
                    actualDecoderEngine = "dav1d AV1 Decoder",
                    isHardware = false,
                    format = fmt,
                    mimeType = fmt?.sampleMimeType,
                    width = outputBuffer.width,
                    height = outputBuffer.height,
                    firstFrameTimestampMs = System.currentTimeMillis()
                )
            )
        }

        outputBuffer.release()
    }

    override fun setDecoderOutputMode(outputMode: Int) {
        // Handled natively by direct ANativeWindow surface rendering
    }

    override fun onReset() {
        super.onReset()
        hasRenderedFirstFrame = false
    }

    override fun onDisabled() {
        super.onDisabled()
        activeDecoder = null
        hasRenderedFirstFrame = false
        selectionListener?.onDecoderReleased(TrackType.VIDEO, getName())
        diagnosticsListener?.onDecoderStateChanged(
            TrackDecoderStatus(
                trackType = TrackType.VIDEO,
                state = DecoderState.RELEASED,
                selectedRenderer = getName(),
                actualDecoderEngine = "dav1d AV1 Decoder",
                isHardware = false
            )
        )
    }
}
