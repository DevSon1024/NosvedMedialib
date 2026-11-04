# NosvedMedia

NosvedMedia is an independent, high-performance Android media decoding library designed to provide extended software decoding capabilities for Android applications, distributed as an Android AAR.

It seamlessly integrates with **AndroidX Media3 (ExoPlayer)**, providing software decoding fallback for legacy or high-profile media streams (H.264, HEVC 10-bit, VP8, VP9, MPEG-4, MPEG-2) via an audited FFmpeg engine and ultra-fast AV1 decoding via dav1d.

## Features

- **Media3 Native Architecture:** Seamlessly plugs into `DefaultRenderersFactory` and `ExoPlayer` via `FFmpegRenderersFactory` or the unified `NosvedMedia` facade.
- **Dual Software Engines:**
  - **dav1d 1.5.0:** High-efficiency AV1 decoder with 8-bit and 10-bit ARM NEON assembly optimizations.
  - **FFmpeg 7.1:** Audited LGPL v2.1+ build supporting H.264, HEVC/H.265, VP8, VP9, MPEG-4, and MPEG-2.
- **Surface Direct Rendering:** Native frame conversion directly into `ANativeWindow` buffers, avoiding JVM memory overhead.
- **Zero Crash Tolerance:** Strict JNI error checking, RAII resource management in modern C++17, and thread-safe decoder lifecycles.
- **Full Diagnostics:** Real-time FPS, decode latency, frame drop metrics, and capability detection.
- **Clean Licensing:** Audited LGPL v2.1+ dynamic linking compliance and BSD 2-Clause components.

## Module Structure

- `:native-codecs`: Android library containing C++17 decoding core, JNI bridge, and shared native libraries (`libnative_codecs.so`, `libavcodec.so`, `libdav1d.so`, etc.).
- `:nosved-media3`: Android library containing Media3 renderers, decoder adapters, capability detectors, and the `NosvedMedia` public facade.
- `:app`: Sample demonstration application with interactive decoder switching and live diagnostics.

## Requirements

- **Minimum SDK:** Android API 26 (Android 8.0 Oreo)
- **Target Architecture:** `arm64-v8a`
- **Playback Framework:** AndroidX Media3 1.5.1+

## Quick Start

### 1. Dependency Setup

Add `mavenLocal()` to your repository configuration, then add the dependency:

```kotlin
dependencies {
    implementation("com.nosved.nosvedmedia:nosved-media3:1.0.0")
}
```

### 2. Basic ExoPlayer Integration

```kotlin
import com.nosved.nosvedmedia.NosvedMedia
import com.nosved.nosvedmedia.media3.SoftwareDecoderPriority

// Initialize library
NosvedMedia.initialize(context)

// Create ExoPlayer with NosvedMedia renderers
val player = NosvedMedia.createPlayer(
    context = context,
    softwareDecoderPriority = SoftwareDecoderPriority.FALLBACK
)
```

## Documentation

- [BUILDING.md](BUILDING.md) — Reproducible build instructions for native binaries and AARs.
- [INTEGRATION.md](INTEGRATION.md) — Integration guide for Media3 applications.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — Licensing and attribution notices for FFmpeg and dav1d.
- [CHANGELOG.md](CHANGELOG.md) — Version history and release notes.

## License

NosvedMedia is licensed under the Apache License 2.0. Third-party components are licensed under LGPL v2.1+ (FFmpeg) and BSD 2-Clause (dav1d).
