package com.nosved.nosvedmedia.media3

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.nosved.native_codecs.NativeLib
import org.json.JSONObject

@UnstableApi
object Dav1dCodecInspector {

    private const val TAG = "Dav1dCodecInspector"

    data class Dav1dCapabilities(
        val engine: String = "dav1d",
        val version: String = "unknown",
        val supportedProfiles: List<String> = listOf("Main (Profile 0)", "High (Profile 1)", "Professional (Profile 2)"),
        val supportedBitDepths: List<Int> = listOf(8, 10),
        val supportedPixelLayouts: List<String> = listOf("I400 (Monochrome)", "I420 (4:2:0)", "I422 (4:2:2)", "I444 (4:4:4)"),
        val maxSoftwareWidth: Int = 4096,
        val maxSoftwareHeight: Int = 2304,
        val supports10BitHdr: Boolean = true,
        val supportsFilmGrain: Boolean = true,
        val supportsMultithreading: Boolean = true
    )

    private val nativeLib by lazy { NativeLib() }

    @Volatile
    private var cachedCapabilities: Dav1dCapabilities? = null

    fun isCompiled(): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        val version = getVersion()
        return version.isNotBlank() && !version.startsWith("unavailable") && version != "unknown"
    }

    fun getVersion(): String {
        if (!NativeLib.isLibraryLoaded) return "unavailable"
        return nativeLib.getDav1dVersion()
    }

    fun isFormatSupported(format: Format): Boolean {
        if (!isCompiled()) return false
        if (format.sampleMimeType != MimeTypes.VIDEO_AV1) return false
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) return false

        val caps = getCapabilities()
        val width = format.width
        val height = format.height
        if (width != Format.NO_VALUE && height != Format.NO_VALUE) {
            if (width > caps.maxSoftwareWidth || height > caps.maxSoftwareHeight) {
                return false
            }
        }

        return true
    }

    @Synchronized
    fun getCapabilities(): Dav1dCapabilities {
        val cached = cachedCapabilities
        if (cached != null) return cached

        if (!NativeLib.isLibraryLoaded) {
            return Dav1dCapabilities(version = "unavailable")
        }

        val jsonStr = nativeLib.getDav1dCapabilitiesJson()
        val caps = try {
            val obj = JSONObject(jsonStr)
            Dav1dCapabilities(
                engine = obj.optString("engine", "dav1d"),
                version = obj.optString("version", nativeLib.getDav1dVersion()),
                supportedProfiles = listOf("Main (Profile 0)", "High (Profile 1)", "Professional (Profile 2)"),
                supportedBitDepths = listOf(8, 10),
                supportedPixelLayouts = listOf("I400 (Monochrome)", "I420 (4:2:0)", "I422 (4:2:2)", "I444 (4:4:4)"),
                maxSoftwareWidth = obj.optInt("max_software_width", 4096),
                maxSoftwareHeight = obj.optInt("max_software_height", 2304),
                supports10BitHdr = obj.optBoolean("supports_10bit_hdr", true),
                supportsFilmGrain = obj.optBoolean("supports_film_grain", true),
                supportsMultithreading = obj.optBoolean("supports_multithreading", true)
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to parse dav1d JSON, using default: ${e.message}")
            Dav1dCapabilities(version = nativeLib.getDav1dVersion())
        }

        cachedCapabilities = caps
        return caps
    }

    fun clearCache() {
        cachedCapabilities = null
    }
}

