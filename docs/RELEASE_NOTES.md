# NosvedMedia v1.0.0 Release Notes

**Release Date:** November 6, 2026  
**Repositories:**
- `DevSon1024/NosvedMedialib` (v1.0.0)
- `DevSon1024/Nosved-Player-Lite` (Production Integration)

---

## 1. Overview

NosvedMedia v1.0.0 is an independent, high-performance Android media decoding and playback library distributed as an Android Archive (AAR). Built on modern C++17, Android NDK, and AndroidX Media3 1.5.1, it provides robust software and hardware decoding for an extensive range of video and audio formats with zero native crashes, leak-free memory management, and intelligent per-track decoder recovery.

---

## 2. Key Highlights

### 2.1 Core Native Engine
- **FFmpeg 7.1 Integration:** Built reproducibly under LGPL v2.1+ linking `libavcodec`, `libavformat`, `libavutil`, `libswresample`, and `libswscale`.
- **dav1d 1.5.0 Integration:** High-performance AV1 decoder built with full ARM NEON assembly under BSD 2-Clause license.
- **C++17 RAII Architecture:** Native handles wrapped in opaque lifecycle contexts with zero raw pointer leakage to JVM code.
- **ProGuard / R8 Consumer Rules:** Automatic consumer rules safeguard JNI entry points and public Media3 facades across release builds.

### 2.2 Video Rendering, Colorspace & HDR Pipeline
- **Accurate Colorspace Mapping:** Studio vs. Full range mapping across ITU-R BT.601, BT.709, and BT.2020 via `sws_setColorspaceDetails`.
- **HDR Tone-Mapping:** High-performance LUT-based tone-mapping for SMPTE ST 2084 (PQ) and HLG on SDR displays, preventing washed-out playback.
- **Direct ANativeWindow Rendering:** High-throughput zero-copy surface rendering with configurable scaling modes (`FAST_BILINEAR`, `BILINEAR`, `BICUBIC`).
- **Bounded Buffer Pool:** 16-slot generation-tracked buffer architecture eliminates buffer index collisions and double-free anomalies.

### 2.3 Audio Decoding Engine & Multi-Channel Support
- **Decoupled Packet Handling:** Stateful `sendPacket`, `sendEos`, and `receivePcm` architecture backed by an internal byte queue.
- **Zero-Truncation Resampler Draining:** Comprehensive draining of `swr_get_delay` guarantees zero missing tail samples on EOF.
- **Dynamic PCM Output:** Dynamic negotiation of `ENCODING_PCM_16BIT` and `ENCODING_PCM_FLOAT` with multi-channel (5.1, 7.1) routing.
- **ITU Downmix Protection:** Safe downmixing with ITU-compliant peak normalization (`rematrix_maxval = 1.0`) prevents digital clipping.

### 2.4 Intelligent Fallback & Per-Track Recovery
- **Per-Track Recovery (`DecoderRecoveryManager`):** Video decoding failures recover to platform MediaCodec without interrupting audio playback, and vice versa.
- **Bounded Retry Policy:** Enforces a maximum of 2 retries per track and tracks attempted engines, preventing infinite fallback loops.
- **Complete State Preservation (`PlaybackStateSnapshot`):** Restores exact position, selected audio track, selected subtitle track, playback speed, repeat mode, and MediaSession without full player teardown.
- **Independent Preferences:** Independent settings for Video and Audio (`AUTO`, `PLATFORM_PREFERRED`, `SOFTWARE_PREFERRED`, `PLATFORM_ONLY`, `SOFTWARE_ONLY`).

### 2.5 Runtime Introspection & Diagnostics UI
- **Dynamic Codec Introspection:** Native runtime querying via `av_codec_iterate` and `dav1d_version` replaces hardcoded profile assumptions.
- **Enhanced Diagnostics Overlay:** Real-time stats overlay displays independent requested preferences, active decoders with HW/SW badges, decoding latency, dropped frames, fallback event history, and structured error logs.

---

## 3. Compatibility & Migration

- **Minimum SDK:** Android API 26 (Android 8.0 Oreo).
- **Target SDK:** Android API 35 / 36.
- **Supported ABIs:** `arm64-v8a` (primary), `armeabi-v7a`, `x86_64`.
- **Legacy Migration:** Existing `DecoderMode` settings (`HW`, `HW+`, `SW`) in consumer applications automatically migrate into independent `DecoderPreferences` without user action:
  - `HW` -> Video: `PLATFORM_ONLY`, Audio: `PLATFORM_ONLY`
  - `HW+` -> Video: `PLATFORM_PREFERRED`, Audio: `PLATFORM_PREFERRED`
  - `SW` -> Video: `SOFTWARE_ONLY`, Audio: `SOFTWARE_ONLY`

---

## 4. Verification & Testing Summary

- **Unit Test Coverage:** 100% pass across all unit tests in `:native-codecs` and `:nosved-media3` (33/33 tests passed).
- **Stress Testing:** Passed 1000+ seek/flush/re-init stress cycles with zero native memory leaks.
- **Consumer App Integration:** Verified clean integration and compilation in Nosved Player Lite with zero regression in playback, gesture controls, or stats overlay.

