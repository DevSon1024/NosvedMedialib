package com.nosved.nosvedmedia.media3

import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.nosved.native_codecs.NativeLib
import org.json.JSONArray
import org.json.JSONObject

@UnstableApi
object FFmpegCodecInspector {

    private const val TAG = "FFmpegCodecInspector"

    data class ProfileEntry(
        val id: Int,
        val name: String
    )

    data class CompiledDecoder(
        val name: String,
        val longName: String,
        val id: Int,
        val type: String, // "video" or "audio"
        val profiles: List<ProfileEntry>,
        val pixelFormats: List<String>,
        val bitDepths: List<Int>,
        val sampleFormats: List<String>,
        val supportedSampleRates: List<Int>,
        val isInitializable: Boolean
    )

    private val nativeLib by lazy { NativeLib() }

    @Volatile
    private var cachedDecoders: List<CompiledDecoder>? = null

    fun isDecoderCompiled(codecName: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        return nativeLib.isDecoderCompiled(codecName)
    }

    fun testDecoderInitialization(codecName: String): Boolean {
        if (!NativeLib.isLibraryLoaded) return false
        return nativeLib.testDecoderInitialization(codecName)
    }

    fun getCompiledDecoders(mediaType: Int = 0): List<CompiledDecoder> {
        val all = getOrInspectDecoders()
        return when (mediaType) {
            1 -> all.filter { it.type == "video" }
            2 -> all.filter { it.type == "audio" }
            else -> all
        }
    }

    fun getDecoderByName(codecName: String): CompiledDecoder? {
        return getOrInspectDecoders().firstOrNull { it.name.equals(codecName, ignoreCase = true) }
    }

    fun findDecoderForMimeType(mimeType: String): CompiledDecoder? {
        val codecName = mapMimeTypeToDecoderName(mimeType) ?: return null
        return getDecoderByName(codecName)
    }

    fun mapMimeTypeToDecoderName(mimeType: String): String? {
        return when (mimeType.lowercase()) {
            MimeTypes.VIDEO_H264.lowercase() -> "h264"
            MimeTypes.VIDEO_H265.lowercase() -> "hevc"
            MimeTypes.VIDEO_VP8.lowercase() -> "vp8"
            MimeTypes.VIDEO_VP9.lowercase() -> "vp9"
            MimeTypes.VIDEO_MP4V.lowercase() -> "mpeg4"
            MimeTypes.VIDEO_MPEG2.lowercase() -> "mpeg2video"
            "video/wvc1" -> "vc1"
            MimeTypes.AUDIO_AAC.lowercase(), "audio/mp4a-latm" -> "aac"
            MimeTypes.AUDIO_AC3.lowercase() -> "ac3"
            MimeTypes.AUDIO_E_AC3.lowercase(), MimeTypes.AUDIO_E_AC3_JOC.lowercase() -> "eac3"
            MimeTypes.AUDIO_TRUEHD.lowercase(), "audio/true-hd", "audio/mlp" -> "truehd"
            MimeTypes.AUDIO_DTS.lowercase(), MimeTypes.AUDIO_DTS_HD.lowercase(),
            MimeTypes.AUDIO_DTS_EXPRESS.lowercase(), "audio/vnd.dts", "audio/vnd.dts.hd" -> "dca"
            MimeTypes.AUDIO_OPUS.lowercase() -> "opus"
            MimeTypes.AUDIO_FLAC.lowercase(), "audio/x-flac" -> "flac"
            MimeTypes.AUDIO_VORBIS.lowercase() -> "vorbis"
            MimeTypes.AUDIO_ALAC.lowercase() -> "alac"
            MimeTypes.AUDIO_MPEG.lowercase(), MimeTypes.AUDIO_MPEG_L1.lowercase(),
            MimeTypes.AUDIO_MPEG_L2.lowercase(), "audio/mp3" -> "mp3"
            MimeTypes.AUDIO_WAV.lowercase(), "audio/x-wav", "audio/raw" -> "pcm_s16le"
            "audio/x-ms-wma", "audio/wma" -> "wmav2"
            "audio/x-ms-wmapro" -> "wmapro"
            "audio/x-ms-wmalossless" -> "wmalossless"
            else -> null
        }
    }

    @Synchronized
    fun getOrInspectDecoders(): List<CompiledDecoder> {
        val cached = cachedDecoders
        if (cached != null) return cached

        if (!NativeLib.isLibraryLoaded) {
            return emptyList()
        }

        val jsonStr = nativeLib.getCompiledDecodersJson(0)
        val list = parseDecodersJson(jsonStr)
        cachedDecoders = list
        return list
    }

    fun clearCache() {
        cachedDecoders = null
    }

    internal fun parseDecodersJson(jsonStr: String): List<CompiledDecoder> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()

        return try {
            val jsonArray = JSONArray(jsonStr)
            if (jsonArray.length() == 0 && jsonStr.contains("\"name\"")) {
                return fallbackParseJson(jsonStr)
            }
            val result = mutableListOf<CompiledDecoder>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                val longName = obj.optString("long_name", "")
                val id = obj.optInt("id", 0)
                val type = obj.optString("type", "")

                val profiles = mutableListOf<ProfileEntry>()
                val profilesArr = obj.optJSONArray("profiles")
                if (profilesArr != null) {
                    for (p in 0 until profilesArr.length()) {
                        val pObj = profilesArr.optJSONObject(p) ?: continue
                        profiles.add(ProfileEntry(pObj.optInt("id", 0), pObj.optString("name", "")))
                    }
                }

                val pixFmts = mutableListOf<String>()
                val pixFmtArr = obj.optJSONArray("pixel_formats")
                if (pixFmtArr != null) {
                    for (f in 0 until pixFmtArr.length()) {
                        pixFmts.add(pixFmtArr.optString(f))
                    }
                }

                val bitDepths = mutableListOf<Int>()
                val bitDepthsArr = obj.optJSONArray("bit_depths")
                if (bitDepthsArr != null) {
                    for (d in 0 until bitDepthsArr.length()) {
                        bitDepths.add(bitDepthsArr.optInt(d))
                    }
                }

                val sampleFmts = mutableListOf<String>()
                val sampleFmtsArr = obj.optJSONArray("sample_formats")
                if (sampleFmtsArr != null) {
                    for (s in 0 until sampleFmtsArr.length()) {
                        sampleFmts.add(sampleFmtsArr.optString(s))
                    }
                }

                val sampleRates = mutableListOf<Int>()
                val sampleRatesArr = obj.optJSONArray("supported_sample_rates")
                if (sampleRatesArr != null) {
                    for (r in 0 until sampleRatesArr.length()) {
                        sampleRates.add(sampleRatesArr.optInt(r))
                    }
                }

                result.add(
                    CompiledDecoder(
                        name = name,
                        longName = longName,
                        id = id,
                        type = type,
                        profiles = profiles,
                        pixelFormats = pixFmts,
                        bitDepths = bitDepths,
                        sampleFormats = sampleFmts,
                        supportedSampleRates = sampleRates,
                        isInitializable = true
                    )
                )
            }
            result
        } catch (e: Throwable) {
            try {
                Log.w(TAG, "Failed to parse decoders JSON via org.json, using fallback: ${e.message}")
            } catch (_: Throwable) {}
            fallbackParseJson(jsonStr)
        }
    }

    private fun fallbackParseJson(json: String): List<CompiledDecoder> {
        val result = mutableListOf<CompiledDecoder>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return result

        var depth = 0
        var startIdx = -1
        var inString = false
        var escape = false

        for (i in trimmed.indices) {
            val c = trimmed[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            if (c == '{') {
                if (depth == 0) {
                    startIdx = i
                }
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && startIdx != -1) {
                    val block = trimmed.substring(startIdx, i + 1)
                    parseDecoderBlock(block)?.let { result.add(it) }
                    startIdx = -1
                }
            }
        }
        return result
    }

    private fun parseDecoderBlock(block: String): CompiledDecoder? {
        val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: return null
        val longName = Regex(""""long_name"\s*:\s*"([^"]*)"""").find(block)?.groupValues?.get(1) ?: ""
        val id = Regex(""""id"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val type = Regex(""""type"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "video"

        // Profiles
        val profiles = mutableListOf<ProfileEntry>()
        val profileBlock = Regex(""""profiles"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: ""
        val profileMatches = Regex("""\{"id"\s*:\s*(\d+)\s*,\s*"name"\s*:\s*"([^"]+)"""").findAll(profileBlock)
        for (pm in profileMatches) {
            val pId = pm.groupValues[1].toIntOrNull() ?: 0
            val pName = pm.groupValues[2]
            profiles.add(ProfileEntry(pId, pName))
        }

        // Pixel Formats
        val pixFmts = mutableListOf<String>()
        val pixBlock = Regex(""""pixel_formats"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: ""
        for (fm in Regex(""""([^"]+)"""").findAll(pixBlock)) {
            pixFmts.add(fm.groupValues[1])
        }

        // Bit Depths
        val bitDepths = mutableListOf<Int>()
        val depthBlock = Regex(""""bit_depths"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: ""
        for (dm in Regex("""\d+""").findAll(depthBlock)) {
            dm.value.toIntOrNull()?.let { bitDepths.add(it) }
        }

        // Sample Formats
        val sampleFmts = mutableListOf<String>()
        val sampleBlock = Regex(""""sample_formats"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: ""
        for (sm in Regex(""""([^"]+)"""").findAll(sampleBlock)) {
            sampleFmts.add(sm.groupValues[1])
        }

        // Sample Rates
        val sampleRates = mutableListOf<Int>()
        val rateBlock = Regex(""""supported_sample_rates"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: ""
        for (rm in Regex("""\d+""").findAll(rateBlock)) {
            rm.value.toIntOrNull()?.let { sampleRates.add(it) }
        }

        return CompiledDecoder(
            name = name,
            longName = longName,
            id = id,
            type = type,
            profiles = profiles,
            pixelFormats = pixFmts,
            bitDepths = if (type == "video" && bitDepths.isEmpty()) listOf(8) else bitDepths,
            sampleFormats = sampleFmts,
            supportedSampleRates = sampleRates,
            isInitializable = true
        )
    }
}
