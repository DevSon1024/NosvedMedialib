package com.devson.native_codecs

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

    fun createVideoDecoder(mimeType: String, extraData: ByteArray?, width: Int, height: Int): Long {
        if (!isLibraryLoaded) return 0L
        return try {
            nativeCreateVideoDecoder(mimeType, extraData, width, height)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to create video decoder for $mimeType", e)
            0L
        }
    }

    fun decodeVideo(handle: Long, buffer: ByteBuffer?, offset: Int, length: Int, timeUs: Long, isEos: Boolean): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeDecodeVideo(handle, buffer, offset, length, timeUs, isEos)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to decode video", e)
            -1
        }
    }

    fun getVideoFrameInfo(handle: Long, outInfo: IntArray): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try {
            nativeGetVideoFrameInfo(handle, outInfo)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get video frame info", e)
            false
        }
    }

    fun renderVideoFrameToSurface(handle: Long, surface: Surface): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try {
            nativeRenderVideoFrameToSurface(handle, surface)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to render video frame to surface", e)
            false
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

    fun createDav1dDecoder(width: Int = 0, height: Int = 0, nThreads: Int = 0): Long {
        if (!isLibraryLoaded) return 0L
        return try {
            nativeCreateDav1dDecoder(width, height, nThreads)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to create dav1d decoder", e)
            0L
        }
    }

    fun decodeDav1d(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        timeUs: Long,
        isEos: Boolean
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try {
            nativeDecodeDav1d(handle, buffer, offset, length, timeUs, isEos)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "dav1d decode call failed", e)
            -1
        }
    }

    fun getDav1dFrameInfo(handle: Long, outInfo: IntArray): Boolean {
        if (!isLibraryLoaded || handle == 0L || outInfo.size < 5) return false
        return try {
            nativeGetDav1dFrameInfo(handle, outInfo)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to get dav1d frame info", e)
            false
        }
    }

    fun renderDav1dFrameToSurface(handle: Long, surface: Surface): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try {
            nativeRenderDav1dFrameToSurface(handle, surface)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to render dav1d frame to surface", e)
            false
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

    private external fun nativeCreateVideoDecoder(
        mimeType: String,
        extraData: ByteArray?,
        width: Int,
        height: Int
    ): Long

    private external fun nativeDecodeVideo(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        timeUs: Long,
        isEos: Boolean
    ): Int

    private external fun nativeGetVideoFrameInfo(
        handle: Long,
        outInfo: IntArray
    ): Boolean

    private external fun nativeRenderVideoFrameToSurface(
        handle: Long,
        surface: Surface
    ): Boolean

    private external fun nativeFlushVideoDecoder(handle: Long)
    private external fun nativeReleaseVideoDecoder(handle: Long)

    private external fun nativeGetDav1dVersion(): String
    private external fun nativeCreateDav1dDecoder(width: Int, height: Int, nThreads: Int): Long
    private external fun nativeDecodeDav1d(
        handle: Long,
        buffer: ByteBuffer?,
        offset: Int,
        length: Int,
        timeUs: Long,
        isEos: Boolean
    ): Int
    private external fun nativeGetDav1dFrameInfo(handle: Long, outInfo: IntArray): Boolean
    private external fun nativeRenderDav1dFrameToSurface(handle: Long, surface: Surface): Boolean
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
