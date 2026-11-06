package com.nosved.nosvedmedia.media3

import android.content.Context
import android.os.Handler
import androidx.media3.common.MimeTypes
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
    private var preferences: DecoderPreferences = DecoderPreferences.DEFAULT,
    private var decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
    private var selectionListener: DecoderSelectionListener? = null,
    private var diagnosticsListener: PlaybackDiagnosticsListener? = null
) : DefaultRenderersFactory(context) {

    private var preferencesExplicit: Boolean = false
    private var thermalMonitor: ThermalThrottlingMonitor? = null

    fun getThermalStatus(): ThermalStatus = thermalMonitor?.currentStatus ?: ThermalStatus.NONE

    constructor(
        context: Context,
        softwareDecoderPriority: SoftwareDecoderPriority,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ) : this(
        context = context,
        preferences = DecoderPreferences.fromSoftwareDecoderPriority(softwareDecoderPriority),
        decoderConfig = decoderConfig,
        selectionListener = selectionListener,
        diagnosticsListener = diagnosticsListener
    ) {
        if (softwareDecoderPriority != SoftwareDecoderPriority.FALLBACK) {
            preferencesExplicit = true
        }
    }

    constructor(context: Context) : this(
        context = context,
        preferences = DecoderPreferences.DEFAULT,
        decoderConfig = VideoDecoderConfig(),
        selectionListener = null,
        diagnosticsListener = null
    )

    fun setDecoderPreferences(preferences: DecoderPreferences): FFmpegRenderersFactory {
        this.preferences = preferences
        this.preferencesExplicit = true
        return this
    }

    fun getDecoderPreferences(): DecoderPreferences = preferences

    fun setSoftwareDecoderPriority(priority: SoftwareDecoderPriority): FFmpegRenderersFactory {
        this.preferences = DecoderPreferences.fromSoftwareDecoderPriority(priority)
        this.preferencesExplicit = true
        return this
    }

    fun setVideoPreference(preference: DecoderPreference): FFmpegRenderersFactory {
        this.preferences = preferences.copy(video = preference)
        this.preferencesExplicit = true
        return this
    }

    fun setAudioPreference(preference: DecoderPreference): FFmpegRenderersFactory {
        this.preferences = preferences.copy(audio = preference)
        this.preferencesExplicit = true
        return this
    }

    fun setAllowFallback(allowFallback: Boolean): FFmpegRenderersFactory {
        this.preferences = preferences.copy(allowFallback = allowFallback)
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
        if (thermalMonitor == null) {
            val monitor = ThermalThrottlingMonitor(context, diagnosticsListener, decoderConfig.thermalMitigationPolicy)
            monitor.start()
            thermalMonitor = monitor
        }

        val effectiveConfig = if (thermalMonitor != null) {
            val recScaling = thermalMonitor?.getRecommendedScalingMode(decoderConfig.scalingMode) ?: decoderConfig.scalingMode
            decoderConfig.copy(scalingMode = recScaling)
        } else {
            decoderConfig
        }

        val dav1dRenderer = Dav1dVideoRenderer(
            allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
            eventHandler = eventHandler,
            eventListener = eventListener,
            config = effectiveConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
        val ffmpegRenderer = FFmpegVideoRenderer(
            allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
            eventHandler = eventHandler,
            eventListener = eventListener,
            config = effectiveConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )

        val effectiveVideoPref = if (preferencesExplicit) {
            preferences.video
        } else {
            when (extensionRendererMode) {
                EXTENSION_RENDERER_MODE_OFF -> DecoderPreference.PLATFORM_ONLY
                EXTENSION_RENDERER_MODE_PREFER -> DecoderPreference.SOFTWARE_PREFERRED
                else -> DecoderPreference.AUTO
            }
        }

        val allowFallback = preferences.allowFallback || enableDecoderFallback

        when (effectiveVideoPref) {
            DecoderPreference.SOFTWARE_ONLY -> {
                // Strictly custom software decoders; platform MediaCodec is never registered
                out.add(dav1dRenderer)
                out.add(ffmpegRenderer)
            }
            DecoderPreference.SOFTWARE_PREFERRED -> {
                out.add(dav1dRenderer)
                out.add(ffmpegRenderer)
                if (allowFallback) {
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
            DecoderPreference.PLATFORM_ONLY -> {
                // Strictly platform decoders; software decoders are never registered
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
            DecoderPreference.PLATFORM_PREFERRED -> {
                val hasHwAv1 = CodecCapabilities.getHardwareDecoderAvailability(MimeTypes.VIDEO_AV1)
                if (!hasHwAv1 && allowFallback) {
                    out.add(dav1dRenderer)
                }
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
                if (allowFallback) {
                    if (hasHwAv1) {
                        out.add(dav1dRenderer)
                    }
                    out.add(ffmpegRenderer)
                }
            }
            DecoderPreference.AUTO -> {
                val hasHwAv1 = CodecCapabilities.getHardwareDecoderAvailability(MimeTypes.VIDEO_AV1)
                if (!hasHwAv1) {
                    // Prioritize dav1d for AV1 when device lacks hardware AV1 support
                    out.add(dav1dRenderer)
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
                    out.add(ffmpegRenderer)
                } else {
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
            audioSink = audioSink,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )

        val effectiveAudioPref = if (preferencesExplicit) {
            preferences.audio
        } else {
            when (extensionRendererMode) {
                EXTENSION_RENDERER_MODE_PREFER -> DecoderPreference.SOFTWARE_PREFERRED
                else -> DecoderPreference.PLATFORM_PREFERRED
            }
        }

        val allowFallback = preferences.allowFallback || enableDecoderFallback

        when (effectiveAudioPref) {
            DecoderPreference.SOFTWARE_ONLY -> {
                // Strictly custom software decoders; platform audio renderers are never registered
                out.add(ffmpegAudioRenderer)
            }
            DecoderPreference.SOFTWARE_PREFERRED -> {
                out.add(ffmpegAudioRenderer)
                if (allowFallback) {
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
            DecoderPreference.PLATFORM_ONLY -> {
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
            DecoderPreference.PLATFORM_PREFERRED, DecoderPreference.AUTO -> {
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
                // Always provide FFmpeg audio fallback unless PLATFORM_ONLY is explicitly chosen
                if (allowFallback) {
                    out.add(ffmpegAudioRenderer)
                }
            }
        }
    }
}
