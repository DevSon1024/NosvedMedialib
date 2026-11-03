# Changelog

All notable changes to the NosvedMedia project are documented in this file.

## [1.0.0] - 2026-11-03

### Release Highlights
- Initial production release of NosvedMedia as a standalone, distributable Android AAR suite (`native-codecs:1.0.0` and `nosved-media3:1.0.0`).
- Full integration with AndroidX Media3 (ExoPlayer 1.5.1+).
- Support for arm64-v8a target architecture on Android API 26+.

### Codec Capabilities
- **AV1 Software Decoding:** High-performance dav1d 1.5.0 decoder with ARM64 NEON assembly optimizations supporting 8-bit and 10-bit color depths.
- **FFmpeg 7.1 Software Decoding:** Audited LGPL v2.1+ build supporting H.264 (AVC), H.265 (HEVC), VP8, VP9, MPEG-4, and MPEG-2 streams.
- **Direct Surface Rendering:** Hardware-accelerated ANativeWindow buffer lock/unlock pipeline avoiding JVM heap copies.

### Public API & Architecture
- **NosvedMedia Facade:** High-level Kotlin facade providing centralized initialization, capability discovery, and player builder methods.
- **FFmpegRenderersFactory:** Open renderer factory supporting priority modes (`FALLBACK`, `PREFER`, `DISABLED`) and customizable MediaCodecSelectors.
- **Diagnostic System:** Real-time decoder reporting, playback FPS tracking, decode latency profiling, and error propagation.
- **Resource Management:** Native C++17 RAII wrappers eliminating memory leaks across rapid seeking and lifecycle destruction.

### Integration
- Successfully integrated into Nosved Player Lite (`feature/nosvedmedia-integration`), replacing nextlib without breaking Dolby Vision fallbacks, track selection, or user interface controls.

