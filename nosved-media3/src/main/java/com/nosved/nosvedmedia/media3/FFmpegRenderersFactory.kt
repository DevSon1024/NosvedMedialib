package com.nosved.nosvedmedia.media3

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

@UnstableApi
open class FFmpegRenderersFactory(
    context: Context,
    private var softwareDecoderPriority: SoftwareDecoderPriority = SoftwareDecoderPriority.FALLBACK,
    private var decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
    private var selectionListener: DecoderSelectionListener? = null,
    private var diagnosticsListener: PlaybackDiagnosticsListener? = null
) : DefaultRenderersFactory(context) {

    private var priorityExplicit: Boolean = false

    init {
        if (softwareDecoderPriority != SoftwareDecoderPriority.FALLBACK) {
            priorityExplicit = true
        }
    }

    fun setSoftwareDecoderPriority(priority: SoftwareDecoderPriority): FFmpegRenderersFactory {
        this.softwareDecoderPriority = priority
        this.priorityExplicit = true
        return this
    }

    fun setDecoderConfig(config: VideoDecoderConfig): FFmpegRenderersFactory {
        this.decoderConfig = config
        return this
    }

    fun setSelectionListener(listener: DecoderSelectionListener?): FFmpegRenderersFactory {
        this.selectionListener = listener
        return this
    }

    fun setDiagnosticsListener(listener: PlaybackDiagnosticsListener?): FFmpegRenderersFactory {
        this.diagnosticsListener = listener
        return this
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        val dav1dRenderer = Dav1dVideoRenderer(
            allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
            eventHandler = eventHandler,
            eventListener = eventListener,
            config = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
        val ffmpegRenderer = FFmpegVideoRenderer(
            allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
            eventHandler = eventHandler,
            eventListener = eventListener,
            config = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )

        val effectivePriority = if (priorityExplicit) {
            softwareDecoderPriority
        } else {
            when (extensionRendererMode) {
                EXTENSION_RENDERER_MODE_OFF -> SoftwareDecoderPriority.DISABLED
                EXTENSION_RENDERER_MODE_PREFER -> SoftwareDecoderPriority.PREFER
                else -> SoftwareDecoderPriority.FALLBACK
            }
        }

        when (effectivePriority) {
            SoftwareDecoderPriority.PREFER -> {
                out.add(dav1dRenderer)
                out.add(ffmpegRenderer)
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    allowedVideoJoiningTimeMs,
                    out
                )
            }
            SoftwareDecoderPriority.FALLBACK -> {
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    allowedVideoJoiningTimeMs,
                    out
                )
                out.add(dav1dRenderer)
                out.add(ffmpegRenderer)
            }
            SoftwareDecoderPriority.DISABLED -> {
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    allowedVideoJoiningTimeMs,
                    out
                )
            }
        }
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        val ffmpegAudioRenderer = FFmpegAudioRenderer(
            eventHandler = eventHandler,
            eventListener = eventListener,
            audioSink = audioSink
        )

        val effectivePriority = if (priorityExplicit) {
            softwareDecoderPriority
        } else {
            when (extensionRendererMode) {
                EXTENSION_RENDERER_MODE_PREFER -> SoftwareDecoderPriority.PREFER
                else -> SoftwareDecoderPriority.FALLBACK
            }
        }

        when (effectivePriority) {
            SoftwareDecoderPriority.PREFER -> {
                out.add(ffmpegAudioRenderer)
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
            SoftwareDecoderPriority.FALLBACK -> {
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
                out.add(ffmpegAudioRenderer)
            }
            SoftwareDecoderPriority.DISABLED -> {
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }
    }
}

