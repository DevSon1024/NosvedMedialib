package com.nosved.nosvedmedia.media3

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
class FFmpegAudioRenderer(
    eventHandler: Handler? = null,
    eventListener: AudioRendererEventListener? = null,
    audioSink: AudioSink
) : DecoderAudioRenderer<FFmpegAudioDecoder>(eventHandler, eventListener, audioSink) {

    constructor(
        context: Context,
        eventHandler: Handler? = null,
        eventListener: AudioRendererEventListener? = null
    ) : this(eventHandler, eventListener, DefaultAudioSink.Builder(context).build())

    override fun getName(): String = "FFmpegAudioRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        val sampleMimeType = format.sampleMimeType ?: return C.FORMAT_UNSUPPORTED_TYPE
        if (!AudioDecoderCapabilities.isMimeTypeSupported(sampleMimeType)) {
            return C.FORMAT_UNSUPPORTED_TYPE
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_DRM
        }
        val rawFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(if (format.channelCount != Format.NO_VALUE) format.channelCount else 2)
            .setSampleRate(if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()
        if (!sinkSupportsFormat(rawFormat)) {
            val stereoRawFormat = rawFormat.buildUpon().setChannelCount(2).build()
            if (!sinkSupportsFormat(stereoRawFormat)) {
                return C.FORMAT_UNSUPPORTED_SUBTYPE
            }
        }
        return C.FORMAT_HANDLED
    }

    override fun createDecoder(
        format: Format,
        cryptoConfig: CryptoConfig?
    ): FFmpegAudioDecoder {
        if (cryptoConfig != null) {
            throw FFmpegDecoderException("Encrypted audio is not supported by FFmpeg software decoder")
        }
        return FFmpegAudioDecoder(format)
    }

    override fun getOutputFormat(decoder: FFmpegAudioDecoder): Format {
        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(decoder.channelCount)
            .setSampleRate(decoder.sampleRate)
            .setPcmEncoding(decoder.encoding)
            .build()
    }
}
