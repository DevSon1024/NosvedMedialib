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
