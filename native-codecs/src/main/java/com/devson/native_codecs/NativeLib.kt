package com.devson.native_codecs

import android.util.Log

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

    private external fun nativeInit(): Boolean
    private external fun nativeIsInitialized(): Boolean
    private external fun nativeGetFFmpegVersion(): String
    private external fun nativeGetFFmpegLicense(): String
    private external fun nativeGetFFmpegConfiguration(): String
    private external fun nativeGetErrorDescription(errorCode: Int): String
    private external fun nativeGetCodecVersion(): Int
    private external fun nativeGetFormatVersion(): Int
    private external fun nativeGetUtilVersion(): Int

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
