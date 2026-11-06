package com.nosved.nosvedmedia.media3

import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.VideoDecoderOutputBuffer
import com.nosved.native_codecs.NativeLib
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

@UnstableApi
class FFmpegVideoDecoder(
    val format: Format,
    val config: VideoDecoderConfig = VideoDecoderConfig(),
    val performanceMonitor: PerformanceMonitor? = null,
    val diagnosticsListener: PlaybackDiagnosticsListener? = null
) : Decoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FFmpegDecoderException> {

    private val nativeLib = NativeLib()
    private var nativeHandle: Long = 0L

    private val lock = Object()
    private val availableInputBuffers = ArrayDeque<DecoderInputBuffer>()
    private val queuedInputBuffers = ArrayDeque<DecoderInputBuffer>()
    private val availableOutputBuffers = ArrayDeque<VideoDecoderOutputBuffer>()
    private val readyOutputBuffers = ArrayDeque<VideoDecoderOutputBuffer>()
    private val bufferSlotMap = Collections.synchronizedMap(IdentityHashMap<VideoDecoderOutputBuffer, Int>())

    private var flushed = false
    private var released = false
    private var eosSent = false
    private var eosDrained = false
    private var nativeNeedsMoreInput = false
    private var decodeException: FFmpegDecoderException? = null

    private val decodeThread: Thread

    init {
        val mimeType = format.sampleMimeType
            ?: throw FFmpegDecoderException("MIME type not specified in format")
        val extraData = getExtraData(mimeType, format.initializationData)
        val width = if (format.width != Format.NO_VALUE) format.width else 0
        val height = if (format.height != Format.NO_VALUE) format.height else 0

        nativeHandle = nativeLib.createVideoDecoder(
            mimeType,
            extraData,
            width,
            height,
            config.threadCount
        )

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

        for (i in 0 until config.numInputBuffers) {
            val buf = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
            if (config.initialInputBufferSize > 0) {
                buf.ensureSpaceForWrite(config.initialInputBufferSize)
            }
            availableInputBuffers.add(buf)
        }

        for (i in 0 until config.numOutputBuffers) {
            val buf = VideoDecoderOutputBuffer { outputBuffer -> releaseOutputBuffer(outputBuffer) }
            availableOutputBuffers.add(buf)
        }

        decodeThread = Thread({ decodeLoop() }, "FFmpegVideoDecoderThread")
        decodeThread.isDaemon = true
        decodeThread.start()
    }

    private var outputStartTimeUs: Long = 0L

    override fun setOutputStartTimeUs(outputStartTimeUs: Long) {
        this.outputStartTimeUs = outputStartTimeUs
    }

    override fun getName(): String = "FFmpegVideoDecoder"

    override fun dequeueInputBuffer(): DecoderInputBuffer? {
        synchronized(lock) {
            decodeException?.let { throw it }
            if (availableInputBuffers.isEmpty()) {
                return null
            }
            val buffer = availableInputBuffers.removeFirst()
            buffer.clear()
            return buffer
        }
    }

    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) {
        synchronized(lock) {
            decodeException?.let { throw it }
            queuedInputBuffers.addLast(inputBuffer)
            lock.notifyAll()
        }
    }

    override fun dequeueOutputBuffer(): VideoDecoderOutputBuffer? {
        synchronized(lock) {
            decodeException?.let { throw it }
            if (readyOutputBuffers.isEmpty()) {
                return null
            }
            return readyOutputBuffers.removeFirst()
        }
    }

    private fun releaseOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        synchronized(lock) {
            val slotIndex = bufferSlotMap.remove(outputBuffer)
            val handle = nativeHandle
            if (slotIndex != null && slotIndex >= 0 && handle != 0L) {
                nativeLib.releaseVideoFrame(handle, slotIndex)
            }
            outputBuffer.clear()
            availableOutputBuffers.addLast(outputBuffer)
            lock.notifyAll()
        }
    }

    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface): Boolean {
        val handle = nativeHandle
        if (handle == 0L) return false
        val slotIndex = bufferSlotMap[outputBuffer] ?: return false
        return nativeLib.renderVideoFrameToSurface(handle, slotIndex, surface)
    }

    override fun flush() {
        synchronized(lock) {
            flushed = true
            lock.notifyAll()
            while (flushed && !released) {
                try {
                    lock.wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            released = true
            lock.notifyAll()
        }
        try {
            decodeThread.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            nativeLib.releaseVideoDecoder(handle)
        }
    }

    private fun hasWorkLocked(): Boolean {
        if (flushed || released) return true
        if (decodeException != null) return false
        if (!eosSent && queuedInputBuffers.isNotEmpty()) return true
        if (availableOutputBuffers.isNotEmpty() && !nativeNeedsMoreInput && !eosDrained) return true
        if (eosSent && !eosDrained && availableOutputBuffers.isNotEmpty()) return true
        return false
    }

    private fun decodeLoop() {
        val frameInfo = IntArray(5)
        while (true) {
            var inputToProcess: DecoderInputBuffer? = null

            synchronized(lock) {
                while (!released && !flushed && !hasWorkLocked()) {
                    try {
                        lock.wait()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }

                if (released) {
                    return
                }

                if (flushed) {
                    val handle = nativeHandle
                    if (handle != 0L) {
                        nativeLib.flushVideoDecoder(handle)
                    }
                    eosSent = false
                    eosDrained = false
                    nativeNeedsMoreInput = false
                    decodeException = null

                    while (queuedInputBuffers.isNotEmpty()) {
                        val buf = queuedInputBuffers.removeFirst()
                        buf.clear()
                        availableInputBuffers.addLast(buf)
                    }
                    while (readyOutputBuffers.isNotEmpty()) {
                        val buf = readyOutputBuffers.removeFirst()
                        val slot = bufferSlotMap.remove(buf)
                        if (slot != null && slot >= 0 && handle != 0L) {
                            nativeLib.releaseVideoFrame(handle, slot)
                        }
                        buf.clear()
                        availableOutputBuffers.addLast(buf)
                    }
                    flushed = false
                    lock.notifyAll()
                    continue
                }

                // 1. Drain decoded frames from native FFmpeg
                while (availableOutputBuffers.isNotEmpty()) {
                    val handle = nativeHandle
                    if (handle == 0L) break

                    val res = nativeLib.receiveVideoFrame(handle, frameInfo)
                    if (res == 0) { // DECODE_OK
                        val slotIndex = frameInfo[0]
                        val width = frameInfo[1]
                        val height = frameInfo[2]
                        val isKey = frameInfo[3] == 1
                        val pixelFormat = frameInfo[4]
                        val pts = nativeLib.getVideoFramePts(handle, slotIndex)

                        val outBuffer = availableOutputBuffers.removeFirst()
                        outBuffer.clear()
                        outBuffer.init(pts, C.VIDEO_OUTPUT_MODE_SURFACE_YUV, null)
                        outBuffer.width = width
                        outBuffer.height = height
                        outBuffer.format = this.format
                        if (isKey) {
                            outBuffer.addFlag(C.BUFFER_FLAG_KEY_FRAME)
                        }
                        bufferSlotMap[outBuffer] = slotIndex
                        readyOutputBuffers.addLast(outBuffer)
                        continue
                    } else if (res == 2) { // DECODE_END_OF_STREAM
                        if (!eosDrained) {
                            eosDrained = true
                            val outBuffer = availableOutputBuffers.removeFirst()
                            outBuffer.clear()
                            outBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                            readyOutputBuffers.addLast(outBuffer)
                        }
                        break
                    } else if (res == 1) { // DECODE_NEED_MORE_INPUT
                        nativeNeedsMoreInput = true
                        break
                    } else if (res == 3) { // DECODE_BUFFER_FULL
                        break
                    } else { // Decode error
                        val errorMsg = nativeLib.getVideoLastErrorMessage(handle)
                        val isFatal = res == -7
                        diagnosticsListener?.onDecodingError(
                            DecodingError(
                                decoderName = "FFmpegVideoDecoder",
                                errorCode = res,
                                errorMessage = "FFmpeg receive frame error ($res): $errorMsg",
                                isFatal = isFatal
                            )
                        )
                        if (isFatal) {
                            decodeException = FFmpegDecoderException(
                                "Fatal FFmpeg decode error: $errorMsg",
                                errorCode = res
                            )
                        }
                        break
                    }
                }

                if (!eosSent && queuedInputBuffers.isNotEmpty()) {
                    inputToProcess = queuedInputBuffers.removeFirst()
                }
            } // end synchronized

            if (inputToProcess != null) {
                val handle = nativeHandle
                if (handle != 0L) {
                    if (inputToProcess.isEndOfStream) {
                        nativeLib.sendVideoEos(handle)
                        synchronized(lock) {
                            eosSent = true
                            nativeNeedsMoreInput = false
                            inputToProcess.clear()
                            availableInputBuffers.addLast(inputToProcess)
                            lock.notifyAll()
                        }
                    } else {
                        val data = inputToProcess.data
                        val offset = data?.position() ?: 0
                        val length = data?.remaining() ?: 0
                        val ptsUs = inputToProcess.timeUs

                        val startTimeNs = System.nanoTime()
                        val sendRes = nativeLib.sendVideoPacket(
                            handle,
                            data,
                            offset,
                            length,
                            ptsUs,
                            ptsUs
                        )
                        val elapsedNs = System.nanoTime() - startTimeNs
                        performanceMonitor?.recordDecodeTime(elapsedNs)

                        synchronized(lock) {
                            if (sendRes == 3) { // DECODE_BUFFER_FULL
                                queuedInputBuffers.addFirst(inputToProcess)
                            } else {
                                nativeNeedsMoreInput = false
                                if (sendRes < 0) {
                                    val errorMsg = nativeLib.getVideoLastErrorMessage(handle)
                                    val isFatal = sendRes == -7
                                    diagnosticsListener?.onDecodingError(
                                        DecodingError(
                                            decoderName = "FFmpegVideoDecoder",
                                            errorCode = sendRes,
                                            errorMessage = "FFmpeg send packet error ($sendRes): $errorMsg",
                                            isFatal = isFatal
                                        )
                                    )
                                    if (isFatal) {
                                        decodeException = FFmpegDecoderException(
                                            "Fatal FFmpeg send packet error: $errorMsg",
                                            errorCode = sendRes
                                        )
                                    }
                                }
                                inputToProcess.clear()
                                availableInputBuffers.addLast(inputToProcess)
                            }
                            lock.notifyAll()
                        }
                    }
                } else {
                    synchronized(lock) {
                        inputToProcess.clear()
                        availableInputBuffers.addLast(inputToProcess)
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getExtraData(mimeType: String, initializationData: List<ByteArray>): ByteArray? {
            if (initializationData.isEmpty()) return null
            return when (mimeType) {
                MimeTypes.VIDEO_H264 -> {
                    if (initializationData.size >= 2) {
                        val sps = initializationData[0]
                        val pps = initializationData[1]
                        val spsHasStartCode = sps.size >= 4 && sps[0] == 0.toByte() && sps[1] == 0.toByte() &&
                                (sps[2] == 1.toByte() || (sps[2] == 0.toByte() && sps[3] == 1.toByte()))
                        if (spsHasStartCode) {
                            val extraData = ByteArray(sps.size + pps.size)
                            System.arraycopy(sps, 0, extraData, 0, sps.size)
                            System.arraycopy(pps, 0, extraData, sps.size, pps.size)
                            extraData
                        } else {
                            val startCode = byteArrayOf(0, 0, 0, 1)
                            val extraData = ByteArray(startCode.size + sps.size + startCode.size + pps.size)
                            System.arraycopy(startCode, 0, extraData, 0, startCode.size)
                            System.arraycopy(sps, 0, extraData, startCode.size, sps.size)
                            val ppsOffset = startCode.size + sps.size
                            System.arraycopy(startCode, 0, extraData, ppsOffset, startCode.size)
                            System.arraycopy(pps, 0, extraData, ppsOffset + startCode.size, pps.size)
                            extraData
                        }
                    } else {
                        initializationData[0]
                    }
                }
                MimeTypes.VIDEO_H265 -> {
                    initializationData[0]
                }
                else -> initializationData[0]
            }
        }
    }
}
