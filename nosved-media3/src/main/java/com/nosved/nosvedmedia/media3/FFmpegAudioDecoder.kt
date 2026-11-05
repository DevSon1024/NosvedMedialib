package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import com.nosved.native_codecs.NativeLib
import java.nio.ByteBuffer
import java.util.ArrayDeque

@UnstableApi
data class AudioDecoderConfig(
    val numInputBuffers: Int = 16,
    val numOutputBuffers: Int = 16,
    val initialInputBufferSize: Int = 64 * 1024,
    val outputBufferSize: Int = 32 * 1024,
    val pcmEncoding: Int = C.ENCODING_PCM_16BIT,
    val channelRouting: Int = CHANNEL_ROUTING_AUTO,
    val maxOutputChannels: Int = 8
) {
    companion object {
        const val CHANNEL_ROUTING_AUTO = 0
        const val CHANNEL_ROUTING_FORCE_STEREO = 1
        const val CHANNEL_ROUTING_FORCE_MONO = 2
    }
}

@UnstableApi
class FFmpegAudioDecoder(
    val format: Format,
    val config: AudioDecoderConfig = AudioDecoderConfig(),
    val diagnosticsListener: PlaybackDiagnosticsListener? = null,
    val performanceMonitor: PerformanceMonitor? = null
) : Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FFmpegDecoderException> {

    constructor(
        format: Format,
        numInputBuffers: Int = 16,
        numOutputBuffers: Int = 16,
        initialInputBufferSize: Int = 64 * 1024
    ) : this(
        format,
        AudioDecoderConfig(
            numInputBuffers = numInputBuffers,
            numOutputBuffers = numOutputBuffers,
            initialInputBufferSize = initialInputBufferSize
        )
    )

    private val nativeLib = NativeLib()
    private var nativeHandle: Long = 0L

    private val lock = Object()
    private val availableInputBuffers = ArrayDeque<DecoderInputBuffer>()
    private val queuedInputBuffers = ArrayDeque<DecoderInputBuffer>()
    private val availableOutputBuffers = ArrayDeque<SimpleDecoderOutputBuffer>()
    private val readyOutputBuffers = ArrayDeque<SimpleDecoderOutputBuffer>()

    private var flushed = false
    private var released = false
    private var decodeException: FFmpegDecoderException? = null
    private var outputStartTimeUs: Long = 0L

    val sampleRate: Int
    val channelCount: Int
    val encoding: Int

    private val decodeThread: Thread

    init {
        val mimeType = format.sampleMimeType
            ?: throw FFmpegDecoderException("Audio MIME type not specified")
        val extraData = getExtraData(mimeType, format.initializationData)

        val sr = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 0
        val ch = if (format.channelCount != Format.NO_VALUE) format.channelCount else 0
        val pcmEncodingCode = if (config.pcmEncoding == C.ENCODING_PCM_FLOAT) 4 else 2

        nativeHandle = nativeLib.createAudioDecoderConfigured(
            mimeType,
            extraData,
            sr,
            ch,
            pcmEncodingCode,
            config.channelRouting,
            config.maxOutputChannels
        )

        if (nativeHandle == 0L) {
            val lastError = nativeLib.getAudioLastErrorMessage(0L)
            val message = if (lastError.isNotEmpty()) {
                "Failed to initialize native FFmpeg audio decoder for $mimeType: $lastError"
            } else {
                "Failed to initialize native FFmpeg audio decoder for $mimeType"
            }
            val error = DecodingError(
                decoderName = "FFmpegAudioDecoder",
                errorCode = -1,
                errorMessage = message,
                isFatal = true
            )
            diagnosticsListener?.onDecodingError(error)
            throw FFmpegDecoderException(message)
        }

        val nativeSr = nativeLib.getAudioSampleRate(nativeHandle)
        sampleRate = if (nativeSr > 0) nativeSr else if (sr > 0) sr else 48000

        val nativeCh = nativeLib.getAudioChannelCount(nativeHandle)
        channelCount = if (nativeCh > 0) nativeCh else if (ch > 0) ch else 2

        val nativeEncoding = nativeLib.getAudioPcmEncoding(nativeHandle)
        encoding = if (nativeEncoding == 4) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT

        diagnosticsListener?.onDecoderInitialized(
            DecoderDiagnostics(
                decoderName = "FFmpegAudioDecoder",
                mimeType = mimeType,
                width = 0,
                height = 0,
                bitDepth = if (encoding == C.ENCODING_PCM_FLOAT) 32 else 16,
                isHardware = false
            )
        )

        for (i in 0 until config.numInputBuffers) {
            val buf = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
            if (config.initialInputBufferSize > 0) {
                buf.ensureSpaceForWrite(config.initialInputBufferSize)
            }
            availableInputBuffers.add(buf)
        }

        for (i in 0 until config.numOutputBuffers) {
            val buf = SimpleDecoderOutputBuffer { outputBuffer -> releaseOutputBuffer(outputBuffer) }
            availableOutputBuffers.add(buf)
        }

        decodeThread = Thread({ decodeLoop() }, "FFmpegAudioDecoderThread")
        decodeThread.isDaemon = true
        decodeThread.start()
    }

    override fun getName(): String = "FFmpegAudioDecoder"

    override fun setOutputStartTimeUs(outputStartTimeUs: Long) {
        synchronized(lock) {
            this.outputStartTimeUs = outputStartTimeUs
        }
    }

    override fun dequeueInputBuffer(): DecoderInputBuffer? {
        synchronized(lock) {
            val exception = decodeException
            if (exception != null) {
                throw exception
            }
            return availableInputBuffers.pollFirst()
        }
    }

    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) {
        synchronized(lock) {
            val exception = decodeException
            if (exception != null) {
                throw exception
            }
            queuedInputBuffers.add(inputBuffer)
            lock.notifyAll()
        }
    }

    override fun dequeueOutputBuffer(): SimpleDecoderOutputBuffer? {
        synchronized(lock) {
            val exception = decodeException
            if (exception != null) {
                throw exception
            }
            return readyOutputBuffers.pollFirst()
        }
    }

    private fun releaseOutputBuffer(outputBuffer: SimpleDecoderOutputBuffer) {
        synchronized(lock) {
            outputBuffer.clear()
            availableOutputBuffers.add(outputBuffer)
            lock.notifyAll()
        }
    }

    override fun flush() {
        synchronized(lock) {
            flushed = true
            val handle = nativeHandle
            if (handle != 0L) {
                nativeLib.flushAudioDecoder(handle)
            }
            while (queuedInputBuffers.isNotEmpty()) {
                val buf = queuedInputBuffers.removeFirst()
                buf.clear()
                availableInputBuffers.add(buf)
            }
            while (readyOutputBuffers.isNotEmpty()) {
                val buf = readyOutputBuffers.removeFirst()
                buf.clear()
                availableOutputBuffers.add(buf)
            }
            outputStartTimeUs = 0L
            lock.notifyAll()
        }
    }

    override fun release() {
        synchronized(lock) {
            released = true
            lock.notifyAll()
        }
        try {
            decodeThread.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        synchronized(lock) {
            val handle = nativeHandle
            if (handle != 0L) {
                nativeHandle = 0L
                nativeLib.releaseAudioDecoder(handle)
            }
            availableInputBuffers.clear()
            queuedInputBuffers.clear()
            availableOutputBuffers.clear()
            readyOutputBuffers.clear()
        }
    }

    private fun decodeLoop() {
        val ptsHolder = LongArray(1)

        while (true) {
            var inputBuffer: DecoderInputBuffer? = null

            synchronized(lock) {
                while (!released && !flushed && queuedInputBuffers.isEmpty()) {
                    try {
                        lock.wait()
                    } catch (e: InterruptedException) {
                        return
                    }
                }

                if (released) return
                if (flushed) {
                    flushed = false
                    return@synchronized
                }

                inputBuffer = queuedInputBuffers.pollFirst()
            }

            if (inputBuffer == null) {
                continue
            }

            val handle = nativeHandle
            if (handle == 0L) {
                synchronized(lock) {
                    decodeException = FFmpegDecoderException("Native audio decoder handle is null")
                    lock.notifyAll()
                }
                return
            }

            val buffer = inputBuffer
            if (buffer.isEndOfStream) {
                // Drain EOS
                nativeLib.sendAudioEos(handle)

                // Drain all cached and resampled frames
                var draining = true
                while (draining) {
                    var outBuf: SimpleDecoderOutputBuffer? = null
                    synchronized(lock) {
                        while (!released && !flushed && availableOutputBuffers.isEmpty()) {
                            try {
                                lock.wait()
                            } catch (e: InterruptedException) {
                                return
                            }
                        }
                        if (released || flushed) return@synchronized
                        outBuf = availableOutputBuffers.pollFirst()
                    }

                    if (outBuf == null) {
                        break
                    }

                    val targetBuf = outBuf
                    val byteBuffer = targetBuf.init(0L, config.outputBufferSize)
                    ptsHolder[0] = -1L

                    val bytesRead = nativeLib.receiveAudioPcm(
                        handle,
                        byteBuffer,
                        config.outputBufferSize,
                        ptsHolder
                    )

                    if (bytesRead > 0) {
                        byteBuffer.position(0)
                        byteBuffer.limit(bytesRead)
                        targetBuf.timeUs = if (ptsHolder[0] >= 0) ptsHolder[0] else 0L

                        synchronized(lock) {
                            if (targetBuf.timeUs < outputStartTimeUs) {
                                targetBuf.shouldBeSkipped = true
                            }
                            readyOutputBuffers.add(targetBuf)
                            lock.notifyAll()
                        }
                    } else {
                        // Resampler and codec are completely drained
                        synchronized(lock) {
                            targetBuf.clear()
                            availableOutputBuffers.add(targetBuf)
                            lock.notifyAll()
                        }
                        draining = false
                    }
                }

                // Final EOS marker buffer
                var eosBuf: SimpleDecoderOutputBuffer? = null
                synchronized(lock) {
                    while (!released && !flushed && availableOutputBuffers.isEmpty()) {
                        try {
                            lock.wait()
                        } catch (e: InterruptedException) {
                            return
                        }
                    }
                    if (released || flushed) return@synchronized
                    eosBuf = availableOutputBuffers.pollFirst()
                }

                if (eosBuf != null) {
                    val finalBuf = eosBuf
                    finalBuf.init(0L, 0)
                    finalBuf.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                    synchronized(lock) {
                        readyOutputBuffers.add(finalBuf)
                        buffer.clear()
                        availableInputBuffers.add(buffer)
                        lock.notifyAll()
                    }
                }
            } else {
                // Normal packet decode
                val startTimeNs = System.nanoTime()
                val inputData: ByteBuffer? = buffer.data
                val length = inputData?.remaining() ?: 0
                val offset = inputData?.position() ?: 0

                val sendRet = nativeLib.sendAudioPacket(
                    handle,
                    inputData,
                    offset,
                    length,
                    buffer.timeUs
                )

                if (sendRet < 0) {
                    val lastError = nativeLib.getAudioLastErrorMessage(handle)
                    diagnosticsListener?.onDecodingError(
                        DecodingError(
                            decoderName = "FFmpegAudioDecoder",
                            errorCode = sendRet,
                            errorMessage = "Non-fatal audio send error: $lastError",
                            isFatal = false
                        )
                    )
                }

                var reading = true
                var hasProducedOutput = false

                while (reading) {
                    var outBuf: SimpleDecoderOutputBuffer? = null
                    synchronized(lock) {
                        while (!released && !flushed && availableOutputBuffers.isEmpty()) {
                            try {
                                lock.wait()
                            } catch (e: InterruptedException) {
                                return
                            }
                        }
                        if (released || flushed) return@synchronized
                        outBuf = availableOutputBuffers.pollFirst()
                    }

                    if (outBuf == null) {
                        break
                    }

                    val targetBuf = outBuf
                    val byteBuffer = targetBuf.init(buffer.timeUs, config.outputBufferSize)
                    ptsHolder[0] = -1L

                    val bytesRead = nativeLib.receiveAudioPcm(
                        handle,
                        byteBuffer,
                        config.outputBufferSize,
                        ptsHolder
                    )

                    if (bytesRead > 0) {
                        byteBuffer.position(0)
                        byteBuffer.limit(bytesRead)
                        targetBuf.timeUs = if (ptsHolder[0] >= 0) ptsHolder[0] else buffer.timeUs
                        hasProducedOutput = true

                        synchronized(lock) {
                            if (targetBuf.timeUs < outputStartTimeUs) {
                                targetBuf.shouldBeSkipped = true
                            }
                            readyOutputBuffers.add(targetBuf)
                            lock.notifyAll()
                        }
                    } else {
                        synchronized(lock) {
                            targetBuf.clear()
                            availableOutputBuffers.add(targetBuf)
                            lock.notifyAll()
                        }
                        reading = false
                    }
                }

                val durationMs = (System.nanoTime() - startTimeNs) / 1_000_000.0
                performanceMonitor?.recordAudioLatency(durationMs)

                synchronized(lock) {
                    buffer.clear()
                    availableInputBuffers.add(buffer)
                    lock.notifyAll()
                }
            }
        }
    }

    companion object {
        fun getExtraData(mimeType: String, initializationData: List<ByteArray>): ByteArray? {
            if (initializationData.isEmpty()) return null

            return when (mimeType) {
                MimeTypes.AUDIO_VORBIS -> {
                    if (initializationData.size >= 2) {
                        val header0 = initializationData[0]
                        val header1 = initializationData[1]
                        val header0Size = header0.size
                        val header1Size = header1.size

                        val totalSize = 2 + header0Size + 2 + 2 + header1Size
                        val out = ByteArray(totalSize)
                        var pos = 0

                        out[pos++] = (header0Size ushr 8).toByte()
                        out[pos++] = (header0Size and 0xFF).toByte()
                        System.arraycopy(header0, 0, out, pos, header0Size)
                        pos += header0Size

                        // Empty comment header
                        out[pos++] = 0
                        out[pos++] = 0

                        out[pos++] = (header1Size ushr 8).toByte()
                        out[pos++] = (header1Size and 0xFF).toByte()
                        System.arraycopy(header1, 0, out, pos, header1Size)

                        out
                    } else {
                        initializationData[0]
                    }
                }
                MimeTypes.AUDIO_OPUS,
                MimeTypes.AUDIO_AAC,
                "audio/mp4a-latm",
                "audio/aac",
                MimeTypes.AUDIO_FLAC,
                "audio/x-flac",
                MimeTypes.AUDIO_ALAC -> {
                    initializationData[0]
                }
                else -> {
                    if (initializationData.size == 1) {
                        initializationData[0]
                    } else {
                        var total = 0
                        for (chunk in initializationData) {
                            total += chunk.size
                        }
                        val combined = ByteArray(total)
                        var off = 0
                        for (chunk in initializationData) {
                            System.arraycopy(chunk, 0, combined, off, chunk.size)
                            off += chunk.size
                        }
                        combined
                    }
                }
            }
        }
    }
}
