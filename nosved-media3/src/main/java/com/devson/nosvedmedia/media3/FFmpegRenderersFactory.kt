package com.devson.nosvedmedia.media3

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

@UnstableApi
class FFmpegRenderersFactory(
    context: Context,
    private val softwareDecoderPriority: SoftwareDecoderPriority = SoftwareDecoderPriority.FALLBACK,
    private val decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
    private val selectionListener: DecoderSelectionListener? = null
) : DefaultRenderersFactory(context) {

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
            config = decoderConfig
        )
        val ffmpegRenderer = FFmpegVideoRenderer(
            allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
            eventHandler = eventHandler,
            eventListener = eventListener,
            config = decoderConfig
        )

        when (softwareDecoderPriority) {
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
}

