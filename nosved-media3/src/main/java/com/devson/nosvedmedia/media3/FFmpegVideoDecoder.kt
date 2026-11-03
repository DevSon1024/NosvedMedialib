package com.devson.nosvedmedia.media3

import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.VideoDecoderOutputBuffer
import com.devson.native_codecs.NativeLib
import java.nio.ByteBuffer

@UnstableApi
class FFmpegVideoDecoder(
    val format: Format,
    val config: VideoDecoderConfig = VideoDecoderConfig(),
    val performanceMonitor: PerformanceMonitor? = null,
    val diagnosticsListener: PlaybackDiagnosticsListener? = null
) : SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FFmpegDecoderException>(
    Array(config.numInputBuffers) { DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT) },
    Array(config.numOutputBuffers) { VideoDecoderOutputBuffer { /* outputBuffer -> releaseOutputBuffer(outputBuffer) */ } }
) {

    private val nativeLib = NativeLib()
    private var nativeHandle: Long = 0L
    private val frameInfo = IntArray(4)

    init {
        val mimeType = format.sampleMimeType ?: throw FFmpegDecoderException("MIME type not specified in format")
        val extraData = format.initializationData.let { initData ->
            if (initData.isNotEmpty() && initData[0].isNotEmpty()) {
                initData[0]
            } else {
                null
            }
        }
        val width = if (format.width != Format.NO_VALUE) format.width else 0
        val height = if (format.height != Format.NO_VALUE) format.height else 0

        nativeHandle = nativeLib.createVideoDecoder(mimeType, extraData, width, height)
        if (nativeHandle == 0L) {
            val error = DecodingError(
                decoderName = "FFmpegVideoDecoder",
                errorCode = -1,
                errorMessage = "Failed to initialize native FFmpeg video decoder for $mimeType",
                isFatal = true
            )
            diagnosticsListener?.onDecodingError(error)
            throw FFmpegDecoderException("Failed to initialize native FFmpeg video decoder for $mimeType")
        }

        diagnosticsListener?.onDecoderInitialized(
            DecoderDiagnostics(
                decoderName = "FFmpegVideoDecoder",
                mimeType = mimeType,
                width = width,
                height = height,
                bitDepth = 8,
                isHardware = false
            )
        )

        setInitialInputBufferSize(config.initialInputBufferSize)
    }

    override fun getName(): String = "FFmpegVideoDecoder"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): VideoDecoderOutputBuffer =
        VideoDecoderOutputBuffer { outputBuffer -> releaseOutputBuffer(outputBuffer) }

    override fun createUnexpectedDecodeException(error: Throwable): FFmpegDecoderException =
        FFmpegDecoderException("Unexpected FFmpeg decode error", cause = error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: VideoDecoderOutputBuffer,
        reset: Boolean
    ): FFmpegDecoderException? {
        if (nativeHandle == 0L) {
            return FFmpegDecoderException("Decoder is already released")
        }

        if (reset) {
            nativeLib.flushVideoDecoder(nativeHandle)
        }

        if (inputBuffer.isEndOfStream) {
            nativeLib.decodeVideo(nativeHandle, null, 0, 0, inputBuffer.timeUs, true)
            outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
            return null
        }

        val data: ByteBuffer? = inputBuffer.data
        val length = data?.remaining() ?: 0
        val offset = data?.position() ?: 0

        val startTimeNs = System.nanoTime()
        val decodeResult = nativeLib.decodeVideo(
            nativeHandle,
            data,
            offset,
            length,
            inputBuffer.timeUs,
            false
        )
        val elapsedNs = System.nanoTime() - startTimeNs
        performanceMonitor?.recordDecodeTime(elapsedNs)

        when {
            decodeResult < 0 -> {
                val errorDesc = nativeLib.getErrorDescription(decodeResult)
                diagnosticsListener?.onDecodingError(
                    DecodingError(
                        decoderName = "FFmpegVideoDecoder",
                        errorCode = decodeResult,
                        errorMessage = "FFmpeg decode failed: $errorDesc",
                        isFatal = false
                    )
                )
                return FFmpegDecoderException("FFmpeg decode failed: $errorDesc", errorCode = decodeResult)
            }
            decodeResult == 0 -> {
                if (nativeLib.getVideoFrameInfo(nativeHandle, frameInfo)) {
                    val frameWidth = frameInfo[0]
                    val frameHeight = frameInfo[1]
                    outputBuffer.init(inputBuffer.timeUs, C.VIDEO_OUTPUT_MODE_SURFACE_YUV, null)
                    outputBuffer.width = frameWidth
                    outputBuffer.height = frameHeight
                }
            }
            decodeResult == 1 -> {
                outputBuffer.shouldBeSkipped = true
            }
            decodeResult == 2 -> {
                outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
            }
        }

        return null
    }

    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface): Boolean {
        if (nativeHandle == 0L) return false
        return nativeLib.renderVideoFrameToSurface(nativeHandle, surface)
    }

    override fun release() {
        super.release()
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            nativeLib.releaseVideoDecoder(handle)
        }
    }
}
