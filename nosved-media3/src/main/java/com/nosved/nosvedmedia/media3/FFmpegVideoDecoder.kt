package com.nosved.nosvedmedia.media3

import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.VideoDecoderOutputBuffer
import com.nosved.native_codecs.NativeLib
import java.nio.ByteBuffer
import java.util.Collections
import java.util.IdentityHashMap

@UnstableApi
class FFmpegVideoDecoder(
    val format: Format,
    val config: VideoDecoderConfig = VideoDecoderConfig(),
    val performanceMonitor: PerformanceMonitor? = null,
    val diagnosticsListener: PlaybackDiagnosticsListener? = null
) : SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FFmpegDecoderException>(
    Array(config.numInputBuffers) { DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT) },
    Array(config.numOutputBuffers) { VideoDecoderOutputBuffer { } }
) {

    private val nativeLib = NativeLib()
    private var nativeHandle: Long = 0L
    private val frameInfo = IntArray(4)
    private val bufferIndices = Collections.synchronizedMap(IdentityHashMap<VideoDecoderOutputBuffer, Int>())
    private var bufferCounter = 0

    private fun getBufferId(buffer: VideoDecoderOutputBuffer): Int {
        return bufferIndices.getOrPut(buffer) {
            (bufferCounter++) % config.numOutputBuffers
        }
    }

    init {
        val mimeType = format.sampleMimeType ?: throw FFmpegDecoderException("MIME type not specified in format")
        val extraData = format.initializationData.let { initData ->
            if (initData.isNotEmpty()) {
                var total = 0
                for (chunk in initData) {
                    total += chunk.size
                }
                val combined = ByteArray(total)
                var off = 0
                for (chunk in initData) {
                    System.arraycopy(chunk, 0, combined, off, chunk.size)
                    off += chunk.size
                }
                combined
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

    override fun releaseOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        val handle = nativeHandle
        if (handle != 0L) {
            val bufferId = getBufferId(outputBuffer)
            nativeLib.releaseVideoFrame(handle, bufferId)
        }
        super.releaseOutputBuffer(outputBuffer)
    }

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

        val bufferId = getBufferId(outputBuffer)

        if (inputBuffer.isEndOfStream) {
            nativeLib.decodeVideo(nativeHandle, bufferId, null, 0, 0, inputBuffer.timeUs, true)
            outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
            return null
        }

        val data: ByteBuffer? = inputBuffer.data
        val length = data?.remaining() ?: 0
        val offset = data?.position() ?: 0

        val startTimeNs = System.nanoTime()
        val decodeResult = nativeLib.decodeVideo(
            nativeHandle,
            bufferId,
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
                        errorMessage = "FFmpeg decode non-fatal packet skip: $errorDesc",
                        isFatal = false
                    )
                )
                outputBuffer.shouldBeSkipped = true
                return null
            }
            decodeResult == 0 -> {
                if (nativeLib.getVideoFrameInfo(nativeHandle, bufferId, frameInfo)) {
                    val frameWidth = frameInfo[0]
                    val frameHeight = frameInfo[1]
                    val framePts = nativeLib.getVideoFramePts(nativeHandle, bufferId)
                    val ptsToUse = if (framePts >= 0L) framePts else inputBuffer.timeUs
                    outputBuffer.init(ptsToUse, C.VIDEO_OUTPUT_MODE_SURFACE_YUV, null)
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
        val bufferId = getBufferId(outputBuffer)
        return nativeLib.renderVideoFrameToSurface(nativeHandle, bufferId, surface)
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
