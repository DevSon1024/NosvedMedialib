package com.nosved.native_codecs

import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class NativeLib {

    external fun stringFromJNI(): String

    fun init(): Boolean {
        if (!isLibraryLoaded) {
            return false
        }
        return try {
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native init call failed", e)
            false
        }
    }

    fun isInitialized(): Boolean {
        if (!isLibraryLoaded) {
            return false
        }
        return try {
            nativeIsInitialized()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native isInitialized call failed", e)
            false
        }
    }

    fun getFFmpegVersion(): String {
        if (!isLibraryLoaded) {
            return "unavailable (native library not loaded)"
        }
        return try {
            nativeGetFFmpegVersion()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get FFmpeg version", e)
            "unknown"
        }
    }

    fun getFFmpegLicense(): String {
        if (!isLibraryLoaded) {
            return "unavailable"
        }
        return try {
            nativeGetFFmpegLicense()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get FFmpeg license", e)
            "unknown"
        }
    }

    fun getFFmpegConfiguration(): String {
        if (!isLibraryLoaded) {
            return ""
        }
        return try {
            nativeGetFFmpegConfiguration()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get FFmpeg configuration", e)
            ""
        }
    }

    fun getErrorDescription(errorCode: Int): String {
        if (!isLibraryLoaded) {
            return "Native library not loaded"
        }
        return try {
            nativeGetErrorDescription(errorCode)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get error description for $errorCode", e)
            "Error ($errorCode)"
        }
    }

    fun getCodecVersion(): Int {
        if (!isLibraryLoaded) return 0
        return try {
            nativeGetCodecVersion()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get codec version", e)
            0
        }
    }

    fun getFormatVersion(): Int {
        if (!isLibraryLoaded) return 0
        return try {
            nativeGetFormatVersion()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get format version", e)
            0
        }
    }

    fun getUtilVersion(): Int {
        if (!isLibraryLoaded) return 0
        return try {
            nativeGetUtilVersion()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get util version", e)
            0
        }
    }

    fun createVideoDecoder(
        mimeType: String,
        extraData: ByteArray?,
        width: Int,
        height: Int,
        threadCount: Int = 0,
        scalingMode: Int = 1,
        enableToneMapping: Boolean = true,
        threadType: Int = 0
    ): Long {
        if (!isLibraryLoaded) return 0L
        return try {
            if (threadCount > 0 || scalingMode != 1 || !enableToneMapping || threadType != 0) {
                nativeCreateVideoDecoderConfigured(
                    mimeType,
                    extraData,
                    width,
                    height,
                    threadCount,
                    threadType,
                    scalingMode,
                    enableToneMapping
                )
            } else {
                nativeCreateVideoDecoder(mimeType, extraData, width, height)
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to create video decoder for $mimeType", e)
            0L
        }
    }

    fun sendVideoPacket(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        ptsUs: Long,
        dtsUs: Long
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeSendVideoPacket(handle, buffer, offset, length, ptsUs, dtsUs)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to send video packet", e)
            -1
        }
    }

    fun sendVideoEos(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeSendVideoEos(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to send video EOS", e)
            -1
        }
    }

    fun receiveVideoFrame(handle: Long, outInfo: IntArray): Int {
        if (!isLibraryLoaded || handle == 0L || outInfo.size < 5) return -1
        return try {
            nativeReceiveVideoFrame(handle, outInfo)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to receive video frame", e)
            -1
        }
    }

    fun getVideoLastErrorMessage(handle: Long): String {
        if (!isLibraryLoaded || handle == 0L) return "Native library not loaded or null handle"
        return try {
            nativeGetVideoLastErrorMessage(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get video last error message", e)
            "Unknown native error"
        }
    }

    fun decodeVideo(handle: Long, bufferIndex: Int, buffer: ByteBuffer?, offset: Int, length: Int, timeUs: Long, isEos: Boolean): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeDecodeVideo(handle, bufferIndex, buffer, offset, length, timeUs, isEos)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to decode video", e)
            -1
        }
    }

    fun getVideoFrameInfo(handle: Long, bufferIndex: Int, outInfo: IntArray): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try {
            nativeGetVideoFrameInfo(handle, bufferIndex, outInfo)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get video frame info", e)
            false
        }
    }

    fun getVideoFramePts(handle: Long, bufferIndex: Int): Long {
        if (!isLibraryLoaded || handle == 0L) return -1L
        return try {
            nativeGetVideoFramePts(handle, bufferIndex)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get video frame pts", e)
            -1L
        }
    }

    fun renderVideoFrameToSurface(handle: Long, bufferIndex: Int, surface: Surface): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try {
            nativeRenderVideoFrameToSurface(handle, bufferIndex, surface)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to render video frame to surface", e)
            false
        }
    }

    fun releaseVideoFrame(handle: Long, bufferIndex: Int) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeReleaseVideoFrame(handle, bufferIndex)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to release video frame", e)
        }
    }

    fun flushVideoDecoder(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeFlushVideoDecoder(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to flush video decoder", e)
        }
    }

    fun releaseVideoDecoder(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeReleaseVideoDecoder(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to release video decoder", e)
        }
    }

    private external fun nativeInit(): Boolean
    private external fun nativeIsInitialized(): Boolean
    private external fun nativeGetFFmpegVersion(): String
    private external fun nativeGetFFmpegLicense(): String
    private external fun nativeGetFFmpegConfiguration(): String
    private external fun nativeGetErrorDescription(errorCode: Int): String
    private external fun nativeGetCodecVersion(): Int
    private external fun nativeGetFormatVersion(): Int
    private external fun nativeGetUtilVersion(): Int

    fun getDav1dVersion(): String {
        if (!isLibraryLoaded) {
            return "unavailable"
        }
        return try {
            nativeGetDav1dVersion()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get dav1d version", e)
            "unknown"
        }
    }

    fun createDav1dDecoder(
        width: Int = 0,
        height: Int = 0,
        nThreads: Int = 0,
        scalingMode: Int = 1,
        enableToneMapping: Boolean = true
    ): Long {
        if (!isLibraryLoaded) return 0L
        return try {
            if (scalingMode != 1 || !enableToneMapping) {
                nativeCreateDav1dDecoderConfigured(width, height, nThreads, scalingMode, enableToneMapping)
            } else {
                nativeCreateDav1dDecoder(width, height, nThreads)
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to create dav1d decoder", e)
            0L
        }
    }

    fun decodeDav1d(
        handle: Long,
        bufferIndex: Int,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        timeUs: Long,
        isEos: Boolean
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeDecodeDav1d(handle, bufferIndex, buffer, offset, length, timeUs, isEos)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "dav1d decode call failed", e)
            -1
        }
    }

    fun getDav1dFrameInfo(handle: Long, bufferIndex: Int, outInfo: IntArray): Boolean {
        if (!isLibraryLoaded || handle == 0L || outInfo.size < 5) return false
        return try {
            nativeGetDav1dFrameInfo(handle, bufferIndex, outInfo)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get dav1d frame info", e)
            false
        }
    }

    fun getDav1dFramePts(handle: Long, bufferIndex: Int): Long {
        if (!isLibraryLoaded || handle == 0L) return -1L
        return try {
            nativeGetDav1dFramePts(handle, bufferIndex)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get dav1d frame pts", e)
            -1L
        }
    }

    fun renderDav1dFrameToSurface(handle: Long, bufferIndex: Int, surface: Surface): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try {
            nativeRenderDav1dFrameToSurface(handle, bufferIndex, surface)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to render dav1d frame to surface", e)
            false
        }
    }

    fun releaseDav1dFrame(handle: Long, bufferIndex: Int) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeReleaseDav1dFrame(handle, bufferIndex)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to release dav1d frame", e)
        }
    }

    fun flushDav1dDecoder(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeFlushDav1dDecoder(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to flush dav1d decoder", e)
        }
    }

    fun releaseDav1dDecoder(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeReleaseDav1dDecoder(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to release dav1d decoder", e)
        }
    }

    // --- Audio Decoder APIs ---

    fun isAudioMimeTypeSupported(mimeType: String): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nativeIsAudioMimeTypeSupported(mimeType)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to check audio mime support for $mimeType", e)
            false
        }
    }

    fun createAudioDecoder(mimeType: String, extraData: ByteArray?, sampleRate: Int, channelCount: Int): Long {
        return createAudioDecoderConfigured(mimeType, extraData, sampleRate, channelCount, 2, 0, 8)
    }

    fun createAudioDecoderConfigured(
        mimeType: String,
        extraData: ByteArray?,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int = 2,
        channelRouting: Int = 0,
        maxOutputChannels: Int = 8
    ): Long {
        if (!isLibraryLoaded) return 0L
        return try {
            nativeCreateAudioDecoderConfigured(
                mimeType,
                extraData,
                sampleRate,
                channelCount,
                pcmEncoding,
                channelRouting,
                maxOutputChannels
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to create audio decoder for $mimeType", e)
            0L
        }
    }

    fun sendAudioPacket(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        ptsUs: Long
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeSendAudioPacket(handle, buffer, offset, length, ptsUs)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to send audio packet", e)
            -1
        }
    }

    fun sendAudioEos(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeSendAudioEos(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to send audio EOS", e)
            -1
        }
    }

    fun receiveAudioPcm(
        handle: Long,
        outBuffer: ByteBuffer,
        outCapacity: Int,
        outPtsUs: LongArray? = null
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeReceiveAudioPcm(handle, outBuffer, outCapacity, outPtsUs)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to receive audio PCM", e)
            -1
        }
    }

    fun getAudioPcmEncoding(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 2
        return try {
            nativeGetAudioPcmEncoding(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get audio PCM encoding", e)
            2
        }
    }

    fun getAudioLastErrorMessage(handle: Long): String {
        if (!isLibraryLoaded || handle == 0L) return ""
        return try {
            nativeGetAudioLastErrorMessage(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get audio last error message", e)
            ""
        }
    }

    fun decodeAudio(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        outBuffer: ByteBuffer,
        outCapacity: Int
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeDecodeAudio(handle, buffer, offset, length, outBuffer, outCapacity)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to decode audio", e)
            -1
        }
    }

    fun getAudioSampleRate(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 0
        return try {
            nativeGetAudioSampleRate(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get audio sample rate", e)
            0
        }
    }

    fun getAudioChannelCount(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 0
        return try {
            nativeGetAudioChannelCount(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get audio channel count", e)
            0
        }
    }

    fun flushAudioDecoder(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeFlushAudioDecoder(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to flush audio decoder", e)
        }
    }

    fun releaseAudioDecoder(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeReleaseAudioDecoder(handle)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to release audio decoder", e)
        }
    }

    private external fun nativeIsAudioMimeTypeSupported(mimeType: String): Boolean
    private external fun nativeCreateAudioDecoder(
        mimeType: String,
        extraData: ByteArray?,
        sampleRate: Int,
        channelCount: Int
    ): Long
    private external fun nativeCreateAudioDecoderConfigured(
        mimeType: String,
        extraData: ByteArray?,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int,
        channelRouting: Int,
        maxOutputChannels: Int
    ): Long
    private external fun nativeSendAudioPacket(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        ptsUs: Long
    ): Int
    private external fun nativeSendAudioEos(handle: Long): Int
    private external fun nativeReceiveAudioPcm(
        handle: Long,
        outBuffer: ByteBuffer,
        outCapacity: Int,
        outPtsUs: LongArray?
    ): Int
    private external fun nativeGetAudioPcmEncoding(handle: Long): Int
    private external fun nativeGetAudioLastErrorMessage(handle: Long): String
    private external fun nativeDecodeAudio(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        outBuffer: ByteBuffer,
        outCapacity: Int
    ): Int
    private external fun nativeGetAudioSampleRate(handle: Long): Int
    private external fun nativeGetAudioChannelCount(handle: Long): Int
    private external fun nativeFlushAudioDecoder(handle: Long)
    private external fun nativeReleaseAudioDecoder(handle: Long)

    private external fun nativeCreateVideoDecoder(
        mimeType: String,
        extraData: ByteArray?,
        width: Int,
        height: Int
    ): Long

    private external fun nativeCreateVideoDecoderConfigured(
        mimeType: String,
        extraData: ByteArray?,
        width: Int,
        height: Int,
        threadCount: Int,
        threadType: Int,
        scalingMode: Int,
        enableToneMapping: Boolean
    ): Long

    private external fun nativeSendVideoPacket(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        ptsUs: Long,
        dtsUs: Long
    ): Int

    private external fun nativeSendVideoEos(handle: Long): Int

    private external fun nativeReceiveVideoFrame(
        handle: Long,
        outInfo: IntArray
    ): Int

    private external fun nativeGetVideoLastErrorMessage(handle: Long): String

    private external fun nativeDecodeVideo(
        handle: Long,
        bufferIndex: Int,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        timeUs: Long,
        isEos: Boolean
    ): Int

    private external fun nativeGetVideoFrameInfo(
        handle: Long,
        bufferIndex: Int,
        outInfo: IntArray
    ): Boolean

    private external fun nativeGetVideoFramePts(
        handle: Long,
        bufferIndex: Int
    ): Long

    private external fun nativeRenderVideoFrameToSurface(
        handle: Long,
        bufferIndex: Int,
        surface: Surface
    ): Boolean

    private external fun nativeReleaseVideoFrame(
        handle: Long,
        bufferIndex: Int
    )

    private external fun nativeFlushVideoDecoder(handle: Long)
    private external fun nativeReleaseVideoDecoder(handle: Long)

    private external fun nativeGetDav1dVersion(): String
    private external fun nativeCreateDav1dDecoder(width: Int, height: Int, nThreads: Int): Long
    private external fun nativeCreateDav1dDecoderConfigured(
        width: Int,
        height: Int,
        nThreads: Int,
        scalingMode: Int,
        enableToneMapping: Boolean
    ): Long
    private external fun nativeDecodeDav1d(
        handle: Long,
        bufferIndex: Int,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        timeUs: Long,
        isEos: Boolean
    ): Int
    private external fun nativeGetDav1dFrameInfo(
        handle: Long,
        bufferIndex: Int,
        outInfo: IntArray
    ): Boolean

    private external fun nativeGetDav1dFramePts(
        handle: Long,
        bufferIndex: Int
    ): Long
    private external fun nativeRenderDav1dFrameToSurface(
        handle: Long,
        bufferIndex: Int,
        surface: Surface
    ): Boolean
    private external fun nativeReleaseDav1dFrame(
        handle: Long,
        bufferIndex: Int
    )
    private external fun nativeFlushDav1dDecoder(handle: Long)
    private external fun nativeReleaseDav1dDecoder(handle: Long)

    companion object {
        private const val TAG = "NosvedNativeLib"

        @Volatile
        var isLibraryLoaded: Boolean = false
            private set

        init {
            loadNativeLibraries()
        }

        private fun loadNativeLibraries() {
            val libraries = listOf(
                "avutil",
                "swresample",
                "swscale",
                "avcodec",
                "avformat",
                "dav1d",
                "native_codecs"
            )

            try {
                for (lib in libraries) {
                    System.loadLibrary(lib)
                }
                isLibraryLoaded = true
                Log.i(TAG, "All NosvedMedia native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                isLibraryLoaded = false
                Log.e(TAG, "Failed to load NosvedMedia native libraries", e)
            } catch (e: SecurityException) {
                isLibraryLoaded = false
                Log.e(TAG, "Security exception loading NosvedMedia native libraries", e)
            }
        }
    }
}
