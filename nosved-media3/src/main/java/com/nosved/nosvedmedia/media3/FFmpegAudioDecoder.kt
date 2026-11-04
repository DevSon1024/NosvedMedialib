package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import com.nosved.native_codecs.NativeLib
import java.nio.ByteBuffer

@UnstableApi
class FFmpegAudioDecoder(
    val format: Format,
    numInputBuffers: Int = 16,
    numOutputBuffers: Int = 16,
    initialInputBufferSize: Int = 32 * 1024
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FFmpegDecoderException>(
    Array(numInputBuffers) { DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT) },
    Array(numOutputBuffers) { SimpleDecoderOutputBuffer { } }
) {

    private val nativeLib = NativeLib()
    private var nativeHandle: Long = 0L
    val sampleRate: Int
    val channelCount: Int
    val encoding: Int = C.ENCODING_PCM_16BIT

    init {
        val mimeType = format.sampleMimeType ?: throw FFmpegDecoderException("Audio MIME type not specified")
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

        val sr = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 0
        val ch = if (format.channelCount != Format.NO_VALUE) format.channelCount else 0

        nativeHandle = nativeLib.createAudioDecoder(mimeType, extraData, sr, ch)
        if (nativeHandle == 0L) {
            throw FFmpegDecoderException("Failed to initialize native FFmpeg audio decoder for $mimeType")
        }

        val nativeSr = nativeLib.getAudioSampleRate(nativeHandle)
        sampleRate = if (nativeSr > 0) nativeSr else if (sr > 0) sr else 48000

        val nativeCh = nativeLib.getAudioChannelCount(nativeHandle)
        channelCount = if (nativeCh > 0) nativeCh else if (ch > 0) ch.coerceAtMost(2) else 2

        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "FFmpegAudioDecoder"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer =
        SimpleDecoderOutputBuffer { outputBuffer -> releaseOutputBuffer(outputBuffer) }

    override fun createUnexpectedDecodeException(error: Throwable): FFmpegDecoderException =
        FFmpegDecoderException("Unexpected FFmpeg audio decode error", cause = error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean
    ): FFmpegDecoderException? {
        if (nativeHandle == 0L) {
            return FFmpegDecoderException("Audio decoder is already released")
        }

        if (reset) {
            nativeLib.flushAudioDecoder(nativeHandle)
        }

        if (inputBuffer.isEndOfStream) {
            outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
            return null
        }

        val inputData: ByteBuffer? = inputBuffer.data
        val length = inputData?.remaining() ?: 0
        val offset = inputData?.position() ?: 0

        // 65536 bytes capacity is enough for ~16384 stereo 16-bit PCM samples per call
        val maxOutputBytes = 65536
        val outByteBuffer = outputBuffer.init(inputBuffer.timeUs, maxOutputBytes)

        val bytesDecoded = nativeLib.decodeAudio(
            nativeHandle,
            inputData,
            offset,
            length,
            outByteBuffer,
            maxOutputBytes
        )

        if (bytesDecoded <= 0) {
            // Gracefully skip corrupted/unparseable or empty packet rather than aborting stream playback
            outputBuffer.shouldBeSkipped = true
            return null
        }

        outByteBuffer.position(0)
        outByteBuffer.limit(bytesDecoded)
        return null
    }

    override fun release() {
        super.release()
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            nativeLib.releaseAudioDecoder(handle)
        }
    }
}
