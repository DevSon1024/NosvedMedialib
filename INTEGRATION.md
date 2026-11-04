# NosvedMedia Integration Guide

This guide explains how to integrate NosvedMedia into an Android application using AndroidX Media3 (ExoPlayer).

## 1. Repository Configuration

In your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal() // For locally published NosvedMedia AARs
    }
}
```

## 2. Dependency Declaration

In your application module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.nosved.nosvedmedia:nosved-media3:1.0.0")
}
```

Gradle automatically resolves the transitive dependency `com.nosved.nosvedmedia:native-codecs:1.0.0` containing all arm64-v8a native `.so` binaries.

### Native Packaging Configuration

In your app module `build.gradle.kts`, configure `packaging` to handle shared native libraries:

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "**/libavcodec.so",
                "**/libavformat.so",
                "**/libavutil.so",
                "**/libswresample.so",
                "**/libswscale.so",
                "**/libdav1d.so",
                "**/libnative_codecs.so"
            )
        }
    }
}
```

## 3. Initialization

Initialize the library once during application startup or before creating players:

```kotlin
import com.nosved.nosvedmedia.NosvedMedia

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NosvedMedia.initialize(this)
    }
}
```

## 4. Player Creation

### Simple Integration

```kotlin
import com.nosved.nosvedmedia.NosvedMedia
import com.nosved.nosvedmedia.media3.SoftwareDecoderPriority

val player = NosvedMedia.createPlayer(
    context = context,
    softwareDecoderPriority = SoftwareDecoderPriority.FALLBACK
)
```

### Advanced RenderersFactory Integration

For applications extending `DefaultRenderersFactory` (e.g., custom codec selectors or Dolby Vision fallback):

```kotlin
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.Renderer
import com.nosved.nosvedmedia.media3.FFmpegRenderersFactory
import com.nosved.nosvedmedia.media3.SoftwareDecoderPriority

class CustomRenderersFactory(context: Context) : FFmpegRenderersFactory(context) {

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
        val dvFallbackSelector = object : MediaCodecSelector {
            override fun getDecoderInfos(
                mimeType: String,
                requiresSecureDecoder: Boolean,
                requiresTunnelingDecoder: Boolean
            ): MutableList<MediaCodecInfo> {
                val target = if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                    MimeTypes.VIDEO_H265
                } else {
                    mimeType
                }
                return MediaCodecSelector.DEFAULT.getDecoderInfos(target, requiresSecureDecoder, requiresTunnelingDecoder)
            }
        }

        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            dvFallbackSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}
```

## 5. Decoder Modes & Priority

NosvedMedia maps to standard Media3 extension modes:

- `SoftwareDecoderPriority.DISABLED` / `EXTENSION_RENDERER_MODE_OFF`: Hardware decoding only.
- `SoftwareDecoderPriority.FALLBACK` / `EXTENSION_RENDERER_MODE_ON`: Hardware decoding first; falls back to dav1d/FFmpeg software decoders on failure or unsupported format.
- `SoftwareDecoderPriority.PREFER` / `EXTENSION_RENDERER_MODE_PREFER`: Software decoders are placed first in the renderer pipeline.

## 6. Live Diagnostics & Selection Listeners

```kotlin
val renderersFactory = NosvedMedia.createRenderersFactory(
    context = context,
    softwareDecoderPriority = SoftwareDecoderPriority.FALLBACK,
    selectionListener = { rendererName, isSoftware, format ->
        Log.i("Playback", "Selected: $rendererName (SW=$isSoftware) for ${format.sampleMimeType}")
    },
    diagnosticsListener = object : PlaybackDiagnosticsListener {
        override fun onDecoderInitialized(diagnostics: DecoderDiagnostics) {
            Log.i("Playback", "Decoder: ${diagnostics.decoderName} (${diagnostics.width}x${diagnostics.height})")
        }

        override fun onPerformanceMetricsUpdated(metrics: DecodingPerformanceMetrics) {
            Log.d("Playback", "FPS: ${metrics.currentFps}, Avg Decode: ${metrics.averageDecodeTimeMs}ms")
        }

        override fun onDecodingError(error: DecodingError) {
            Log.e("Playback", "Decoding Error: [${error.errorCode}] ${error.errorMessage}")
        }
    }
)
```
