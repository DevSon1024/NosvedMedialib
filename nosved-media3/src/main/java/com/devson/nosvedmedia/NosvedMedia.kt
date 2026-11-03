package com.devson.nosvedmedia

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.devson.native_codecs.NativeLib
import com.devson.nosvedmedia.media3.CodecCapabilities
import com.devson.nosvedmedia.media3.DecoderSelectionListener
import com.devson.nosvedmedia.media3.FFmpegRenderersFactory
import com.devson.nosvedmedia.media3.PlaybackDiagnosticsListener
import com.devson.nosvedmedia.media3.SoftwareDecoderPriority
import com.devson.nosvedmedia.media3.VideoDecoderCapabilities
import com.devson.nosvedmedia.media3.VideoDecoderConfig

/**
 * Public facade for the NosvedMedia playback and decoding library.
 * Provides unified entry points for initialization, decoder capabilities,
 * renderer creation, and ExoPlayer construction.
 */
@UnstableApi
object NosvedMedia {

    private val nativeLib: NativeLib by lazy { NativeLib() }

    @Volatile
    private var initialized: Boolean = false

    /**
     * Initializes the NosvedMedia native library components.
     * Safe to call multiple times.
     */
    @Synchronized
    fun initialize(context: Context): Boolean {
        if (initialized) return true
        initialized = nativeLib.init()
        return initialized
    }

    /**
     * Checks if the native decoding subsystem is successfully loaded and initialized.
     */
    fun isInitialized(): Boolean {
        return initialized && NativeLib.isLibraryLoaded && nativeLib.isInitialized()
    }

    /**
     * Returns the compiled FFmpeg version string.
     */
    fun getFFmpegVersion(): String = nativeLib.getFFmpegVersion()

    /**
     * Returns the FFmpeg license string.
     */
    fun getFFmpegLicense(): String = nativeLib.getFFmpegLicense()

    /**
     * Returns the FFmpeg build configuration summary.
     */
    fun getFFmpegConfiguration(): String = nativeLib.getFFmpegConfiguration()

    /**
     * Returns the compiled dav1d AV1 decoder version string.
     */
    fun getDav1dVersion(): String = nativeLib.getDav1dVersion()

    /**
     * Checks if a given MIME type is supported for software decoding.
     */
    fun isCodecSupported(mimeType: String): Boolean {
        return VideoDecoderCapabilities.isMimeTypeSupported(mimeType)
    }

    /**
     * Retrieves an extensive capability report for a given video codec MIME type.
     */
    fun getCodecReport(mimeType: String): CodecCapabilities.CodecReport {
        return CodecCapabilities.getCodecReport(mimeType)
    }

    /**
     * Retrieves capability reports for all supported video formats.
     */
    fun getAllCodecReports(): List<CodecCapabilities.CodecReport> {
        return CodecCapabilities.getAllCodecReports()
    }

    /**
     * Creates an [FFmpegRenderersFactory] configured with NosvedMedia software decoders.
     */
    fun createRenderersFactory(
        context: Context,
        softwareDecoderPriority: SoftwareDecoderPriority = SoftwareDecoderPriority.FALLBACK,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ): FFmpegRenderersFactory {
        initialize(context)
        return FFmpegRenderersFactory(
            context = context,
            softwareDecoderPriority = softwareDecoderPriority,
            decoderConfig = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
    }

    /**
     * Convenience factory method to construct a fully configured [ExoPlayer] instance
     * utilizing NosvedMedia renderers.
     */
    fun createPlayer(
        context: Context,
        softwareDecoderPriority: SoftwareDecoderPriority = SoftwareDecoderPriority.FALLBACK,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ): ExoPlayer {
        val renderersFactory = createRenderersFactory(
            context = context,
            softwareDecoderPriority = softwareDecoderPriority,
            decoderConfig = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .build()
    }
}

