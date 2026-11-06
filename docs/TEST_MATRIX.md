# NOSVEDMEDIA CODEC TEST MATRIX & VERIFICATION BASELINE

**Target:** `DevSon1024/NosvedMedialib`  
**Phase:** Phase 0 Baseline Audit  
**Date:** 2026-11-05  

---

## 1. Video Codec Test Matrix

| Codec | Profile / Level | Bit Depths | Audio/Video Containers | HW MediaCodec Support | Nosved SW Decoder | SW Engine | Baseline Status | Known Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **H.264 (AVC)** | Baseline, Main, High (up to 5.2) | 8-bit | MP4, MKV, TS, FLV | Universal | `FFmpegVideoDecoder` | FFmpeg 7.1 (`h264`) | Passed unit tests; needs NAL Annex-B conversion | MP4/MKV length-prefixed NALs require Annex-B start codes; B-frame PTS jitter |
| **H.264 (AVC)** | High 10 | 10-bit | MKV, MP4 | Rare (Hi10P HW rare on mobile) | `FFmpegVideoDecoder` | FFmpeg 7.1 (`h264`) | Supported in native build | Output downscaled to 8-bit RGBA; BT.601 color matrix used |
| **H.265 (HEVC)** | Main | 8-bit | MP4, MKV, TS | Universal on API 26+ | `FFmpegVideoDecoder` | FFmpeg 7.1 (`hevc`) | Passed unit tests | Requires Annex-B conversion if stream is HVCC |
| **H.265 (HEVC)** | Main 10 (HDR10) | 10-bit | MP4, MKV, TS | Common on mid/flagship | `FFmpegVideoDecoder` | FFmpeg 7.1 (`hevc`) | Supported in native build | Truncates 10-bit to 8-bit RGBA; lacks HDR10 static/dynamic metadata transfer |
| **AV1** | Main (Profile 0) | 8-bit, 10-bit | MP4, MKV, WebM | Pixel 6+, Tensor, Dimensity, Snapdragon 8 Gen 2+ | `Dav1dVideoDecoder` | dav1d 1.5.0 (NEON SIMD) | Passed unit tests (20/20) | Draining on EOS without terminal flag hangs player; 10-bit truncated to 8-bit |
| **VP9** | Profile 0 | 8-bit | WebM, MKV, MP4 | Universal on API 26+ | `FFmpegVideoDecoder` | FFmpeg 7.1 (`vp9`) | Passed unit tests | BT.601 color matrix fallback |
| **VP9** | Profile 2 (HDR) | 10-bit | WebM, MKV | Common | `FFmpegVideoDecoder` | FFmpeg 7.1 (`vp9`) | Supported in native build | Truncates to 8-bit RGBA |
| **VP8** | Default | 8-bit | WebM, MKV | Universal | `FFmpegVideoDecoder` | FFmpeg 7.1 (`vp8`) | Passed unit tests | Limited to 8-bit SDR |
| **MPEG-4** | Advanced Simple (ASP) | 8-bit | MP4, AVI, MKV | Deprecated/Varies | `FFmpegVideoDecoder` | FFmpeg 7.1 (`mpeg4`) | Passed unit tests | None |
| **MPEG-2** | Main | 8-bit | TS, MPG, MKV | Rare / Software only on Android | `FFmpegVideoDecoder` | FFmpeg 7.1 (`mpeg2video`)| Passed unit tests | Interlaced content requires deinterlacing |
| **VC-1** | Advanced Profile | 8-bit | WMV, MKV, TS | Missing on most Android | `FFmpegVideoDecoder` | FFmpeg 7.1 (`vc1`) | Supported in native build | None |

---

## 2. Audio Codec Test Matrix

| Codec | Profiles / Specifications | Channels | Sample Rates | HW MediaCodec Support | Nosved SW Decoder | SW Engine | Baseline Status | Known Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **E-AC-3 (DDP)** | Dolby Digital Plus / Atmos JOC | 1.0 to 7.1 | Up to 48 kHz | Varies (often passthrough only) | `FFmpegAudioDecoder` | FFmpeg 7.1 (`eac3`) | Verified stereo playback | Unconditionally downmixes multi-channel to stereo |
| **AC-3 (DD)** | Dolby Digital 5.1 | 1.0 to 5.1 | 32, 44.1, 48 kHz | Varies | `FFmpegAudioDecoder` | FFmpeg 7.1 (`ac3`) | Verified stereo playback | Unconditionally downmixes to stereo |
| **TrueHD** | Dolby TrueHD / Atmos | Up to 7.1 | 48, 96, 192 kHz | Passthrough only (Shield/TV) | `FFmpegAudioDecoder` | FFmpeg 7.1 (`truehd`)| Verified in native build | Downmixes 7.1 to stereo; high CPU on 192 kHz |
| **DTS** | DTS Digital Surround | 1.0 to 5.1 | 44.1, 48 kHz | Rare on mobile | `FFmpegAudioDecoder` | FFmpeg 7.1 (`dts`) | Verified stereo playback | Downmixes 5.1 to stereo |
| **DTS-HD** | DTS-HD Master Audio / HRA | Up to 7.1 | Up to 192 kHz | Rare on mobile | `FFmpegAudioDecoder` | FFmpeg 7.1 (`dts`) | Verified in native build | Downmixes 7.1 to stereo |
| **AAC** | LC, HE-AAC v1/v2, LD | 1.0 to 7.1 | 8 to 96 kHz | Universal | `FFmpegAudioDecoder` | FFmpeg 7.1 (`aac`) | Verified | Downmixes multi-channel to stereo |
| **FLAC** | 16-bit, 24-bit Lossless | 1.0 to 7.1 | Up to 192 kHz | Universal (API 26+) | `FFmpegAudioDecoder` | FFmpeg 7.1 (`flac`) | Verified | Downmixes to stereo 16-bit PCM |
| **ALAC** | Apple Lossless 16/24-bit | 1.0 to 5.1 | Up to 192 kHz | Limited | `FFmpegAudioDecoder` | FFmpeg 7.1 (`alac`) | Verified in native build | Downmixes to stereo |
| **Opus** | Standard | 1.0 to 5.1 | 48 kHz | Universal (API 26+) | `FFmpegAudioDecoder` | FFmpeg 7.1 (`opus`) | Verified | Downmixes to stereo |
| **Vorbis** | Standard | 1.0 to 5.1 | Up to 96 kHz | Universal | `FFmpegAudioDecoder` | FFmpeg 7.1 (`vorbis`)| Verified | Downmixes to stereo |
| **MP3** | Layer 1, 2, 3 | 1.0, 2.0 | 32 to 48 kHz | Universal | `FFmpegAudioDecoder` | FFmpeg 7.1 (`mp3`) | Verified | None |
| **WMA** | WMA Standard, Pro | 1.0 to 5.1 | Up to 96 kHz | Rare on Android | `FFmpegAudioDecoder` | FFmpeg 7.1 (`wmav2`)| Verified in native build | Downmixes to stereo |
| **AC-4** | Dolby AC-4 | 1.0 to 7.1 | 48 kHz | Emerging | None / Experimental | None | Claimed in capabilities | FFmpeg 7.1 lacks stable AC-4 decoder |

---

## 3. ABI Architecture Compatibility

| Android ABI | NDK Toolchain | Assembly Optimizations | Build Status | Shared Libraries Packaged |
| :--- | :--- | :--- | :--- | :--- |
| **arm64-v8a** | Clang (API 26+) | Full 64-bit NEON & Crypto SIMD | **PASSED** | `libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`, `libdav1d.so`, `libnative_codecs.so` |
| **armeabi-v7a** | Clang (API 26+) | 32-bit NEON & VFPv3-D32 | **PASSED** | All 7 native libraries built and bundled |
| **x86_64** | Clang (API 26+) | SSE4.2, AVX, AVX2 SIMD | **PASSED** | All 7 native libraries built and bundled |

---

## 4. Current Test Suite Status

* **Unit Tests (`nosved-media3:testDebugUnitTest` & `native-codecs:testDebugUnitTest`):**
  - Status: **PASSED** (Execution: 26s, 32 tasks).
* **Instrumented Tests (`nosved-media3:connectedAndroidTest`):**
  - Suites: `FFmpegVideoDecoderTest`, `Dav1dDecoderTest`, `ReliabilityAndStressTest`.
  - Tests verify native library loading, decoder allocation, multi-threaded stress seeks, and memory recycling on connected `arm64-v8a` hardware.
* **Build Verification:**
  - `:native-codecs:assembleDebug`: **PASSED**
  - `:nosved-media3:assembleDebug`: **PASSED**
  - `:app:assembleDebug`: Blocked by `compileSdk = 35` mismatch against `compileSdk = 36` library metadata (scheduled for Phase 1 fix).

