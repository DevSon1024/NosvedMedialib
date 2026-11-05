package com.nosved.nosvedmedia

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nosved.native_codecs.NativeLib
import com.nosved.nosvedmedia.media3.CodecCapabilities
import com.nosved.nosvedmedia.media3.DecoderPreferences
import com.nosved.nosvedmedia.media3.DecoderSelectionListener
import com.nosved.nosvedmedia.media3.FFmpegRenderersFactory
import com.nosved.nosvedmedia.media3.PlaybackDiagnosticsListener
import com.nosved.nosvedmedia.media3.SoftwareDecoderPriority
import com.nosved.nosvedmedia.media3.VideoDecoderCapabilities
import com.nosved.nosvedmedia.media3.VideoDecoderConfig

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
     * Creates an [FFmpegRenderersFactory] configured with independent [DecoderPreferences].
     */
    fun createRenderersFactory(
        context: Context,
        preferences: DecoderPreferences = DecoderPreferences.DEFAULT,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ): FFmpegRenderersFactory {
        initialize(context)
        return FFmpegRenderersFactory(
            context = context,
            preferences = preferences,
            decoderConfig = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
    }

    /**
     * Legacy overload: creates an [FFmpegRenderersFactory] configured with [SoftwareDecoderPriority].
     */
    fun createRenderersFactory(
        context: Context,
        softwareDecoderPriority: SoftwareDecoderPriority,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ): FFmpegRenderersFactory {
        return createRenderersFactory(
            context = context,
            preferences = DecoderPreferences.fromSoftwareDecoderPriority(softwareDecoderPriority),
            decoderConfig = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
    }

    /**
     * Convenience factory method to construct a fully configured [ExoPlayer] instance
     * utilizing NosvedMedia renderers and [DecoderPreferences].
     */
    fun createPlayer(
        context: Context,
        preferences: DecoderPreferences = DecoderPreferences.DEFAULT,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ): ExoPlayer {
        val renderersFactory = createRenderersFactory(
            context = context,
            preferences = preferences,
            decoderConfig = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .build()
    }

    /**
     * Legacy overload: constructs [ExoPlayer] instance with [SoftwareDecoderPriority].
     */
    fun createPlayer(
        context: Context,
        softwareDecoderPriority: SoftwareDecoderPriority,
        decoderConfig: VideoDecoderConfig = VideoDecoderConfig(),
        selectionListener: DecoderSelectionListener? = null,
        diagnosticsListener: PlaybackDiagnosticsListener? = null
    ): ExoPlayer {
        return createPlayer(
            context = context,
            preferences = DecoderPreferences.fromSoftwareDecoderPriority(softwareDecoderPriority),
            decoderConfig = decoderConfig,
            selectionListener = selectionListener,
            diagnosticsListener = diagnosticsListener
        )
    }
}
