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
    audioSink: AudioSink,
    private val selectionListener: DecoderSelectionListener? = null,
    private val diagnosticsListener: PlaybackDiagnosticsListener? = null
) : DecoderAudioRenderer<FFmpegAudioDecoder>(eventHandler, eventListener, audioSink) {

    constructor(
        context: Context,
        eventHandler: Handler? = null,
        eventListener: AudioRendererEventListener? = null,
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ) : this(
        eventHandler,
        eventListener,
        DefaultAudioSink.Builder(context).build(),
        selectionListener,
        diagnosticsListener
    )

    constructor(
        eventHandler: Handler? = null,
        eventListener: AudioRendererEventListener? = null,
        audioSink: AudioSink
    ) : this(eventHandler, eventListener, audioSink, null, null)

    private var currentFormat: Format? = null
    private var hasRenderedFirstSample = false
    private val performanceMonitor = PerformanceMonitor(diagnosticsListener)

    override fun getName(): String = "FFmpegAudioRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        val sampleMimeType = format.sampleMimeType ?: return C.FORMAT_UNSUPPORTED_TYPE
        if (!AudioDecoderCapabilities.isFormatSupported(format)) {
            return C.FORMAT_UNSUPPORTED_TYPE
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_DRM
        }

        val channelCount = if (format.channelCount != Format.NO_VALUE) format.channelCount else 2
        val sampleRate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 48000

        val canSinkHandle = sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()
        ) || sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
        ) || sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(2)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()
        ) || sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(2)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
        )

        return if (canSinkHandle) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE
    }

    override fun createDecoder(
        format: Format,
        cryptoConfig: CryptoConfig?
    ): FFmpegAudioDecoder {
        if (cryptoConfig != null) {
            val error = StructuredPlaybackError(
                category = DecodingCategory.UNSUPPORTED_FORMAT,
                trackType = TrackType.AUDIO,
                decoderEngine = "FFmpeg Audio Decoder",
                errorMessage = "Encrypted audio is not supported by FFmpeg software decoder",
                isFatal = true
            )
            diagnosticsListener?.onStructuredError(error)
            throw FFmpegDecoderException("Encrypted audio is not supported by FFmpeg software decoder")
        }

        currentFormat = format
        hasRenderedFirstSample = false

        selectionListener?.onDecoderSelected(getName(), true, format)
        diagnosticsListener?.onDecoderStateChanged(
            TrackDecoderStatus(
                trackType = TrackType.AUDIO,
                state = DecoderState.INITIALIZING,
                selectedRenderer = getName(),
                actualDecoderEngine = "FFmpeg Audio Decoder",
                isHardware = false,
                format = format,
                mimeType = format.sampleMimeType,
                sampleRate = format.sampleRate,
                channelCount = format.channelCount,
                initializedTimestampMs = System.currentTimeMillis()
            )
        )

        val channelCount = if (format.channelCount != Format.NO_VALUE) format.channelCount else 2
        val sampleRate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 48000

        val multiFloatSupported = channelCount > 2 && sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
        )

        val multi16Supported = channelCount > 2 && sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()
        )

        val stereoFloatSupported = sinkSupportsFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(2)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
        )

        val (pcmEncoding, channelRouting, maxChannels) = when {
            multiFloatSupported -> Triple(C.ENCODING_PCM_FLOAT, AudioDecoderConfig.CHANNEL_ROUTING_AUTO, channelCount)
            multi16Supported -> Triple(C.ENCODING_PCM_16BIT, AudioDecoderConfig.CHANNEL_ROUTING_AUTO, channelCount)
            stereoFloatSupported && channelCount <= 2 -> Triple(C.ENCODING_PCM_FLOAT, AudioDecoderConfig.CHANNEL_ROUTING_AUTO, 2)
            channelCount > 2 -> Triple(C.ENCODING_PCM_16BIT, AudioDecoderConfig.CHANNEL_ROUTING_FORCE_STEREO, 2)
            else -> Triple(C.ENCODING_PCM_16BIT, AudioDecoderConfig.CHANNEL_ROUTING_AUTO, 2)
        }

        val config = AudioDecoderConfig(
            pcmEncoding = pcmEncoding,
            channelRouting = channelRouting,
            maxOutputChannels = maxChannels
        )

        val decoder = FFmpegAudioDecoder(format, config, diagnosticsListener, performanceMonitor)

        diagnosticsListener?.onDecoderStateChanged(
            TrackDecoderStatus(
                trackType = TrackType.AUDIO,
                state = DecoderState.INITIALIZED,
                selectedRenderer = getName(),
                actualDecoderEngine = "FFmpeg Audio Decoder",
                isHardware = false,
                format = format,
                mimeType = format.sampleMimeType,
                sampleRate = decoder.sampleRate,
                channelCount = decoder.channelCount,
                initializedTimestampMs = System.currentTimeMillis()
            )
        )

        return decoder
    }

    override fun getOutputFormat(decoder: FFmpegAudioDecoder): Format {
        if (!hasRenderedFirstSample) {
            hasRenderedFirstSample = true
            val fmt = currentFormat
            if (fmt != null) {
                selectionListener?.onDecoderActivated(TrackType.AUDIO, getName(), "FFmpeg Audio Decoder", fmt)
            }
            diagnosticsListener?.onFirstFrameRendered(TrackType.AUDIO, "FFmpeg Audio Decoder", 0L)
            diagnosticsListener?.onDecoderStateChanged(
                TrackDecoderStatus(
                    trackType = TrackType.AUDIO,
                    state = DecoderState.PLAYING,
                    selectedRenderer = getName(),
                    actualDecoderEngine = "FFmpeg Audio Decoder",
                    isHardware = false,
                    format = fmt,
                    mimeType = fmt?.sampleMimeType,
                    sampleRate = decoder.sampleRate,
                    channelCount = decoder.channelCount,
                    firstFrameTimestampMs = System.currentTimeMillis()
                )
            )
        }

        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(decoder.channelCount)
            .setSampleRate(decoder.sampleRate)
            .setPcmEncoding(decoder.encoding)
            .build()
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, streamChanged: Boolean) {
        super.onPositionReset(positionUs, joining, streamChanged)
        hasRenderedFirstSample = false
    }

    override fun onDisabled() {
        super.onDisabled()
        hasRenderedFirstSample = false
        selectionListener?.onDecoderReleased(TrackType.AUDIO, getName())
        diagnosticsListener?.onDecoderStateChanged(
            TrackDecoderStatus(
                trackType = TrackType.AUDIO,
                state = DecoderState.RELEASED,
                selectedRenderer = getName(),
                actualDecoderEngine = "FFmpeg Audio Decoder",
                isHardware = false
            )
        )
    }
}
