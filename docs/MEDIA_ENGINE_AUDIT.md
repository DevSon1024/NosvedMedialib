# MEDIA ENGINE ARCHITECTURE AUDIT

**Target:** `DevSon1024/NosvedMedialib`  
**Branch:** `audit/media-engine-baseline`  
**Date:** 2026-11-05  

---

## 1. Executive Summary

A comprehensive source-level audit of the NosvedMedia media engine was conducted across Kotlin wrappers, Media3 renderers/decoders, JNI bridge methods, and C++17 native decoding pipelines (FFmpeg 7.1 and dav1d 1.5.0).

While the native engine achieves baseline decoding for several formats, critical architectural defects were discovered in **buffer indexing collision**, **premature active decoder reporting**, **end-of-stream (EOS) tail frame truncation**, **lack of MP4/MKV length-to-Annex-B conversion**, **color space conversion errors (BT.601 fallback on HD/UHD content)**, and **hardcoded stereo audio downmixing**.

---

## 2. Component-by-Component Source Audit

### 2.1 Video Decoding Pipeline

#### `FFmpegVideoDecoder.kt` & `ffmpeg_video_decoder.cpp`
* **Buffer Registry Collision:**
  - In `FFmpegVideoDecoder.kt` (lines 33-37), an `IdentityHashMap` assigns `bufferId = (bufferCounter++) % config.numOutputBuffers`.
  - In `ffmpeg_video_decoder.h`, `MAX_OUTPUT_BUFFERS` is hardcoded to 16.
  - In `ffmpeg_video_decoder.cpp` (lines 99, 166, 201), buffer indices wrap modulo 16 (`std::abs(bufferIndex) % MAX_OUTPUT_BUFFERS`).
  - *Defect:* If `VideoDecoderConfig.numOutputBuffers` exceeds 16, or if buffers are recycled out of order, distinct `VideoDecoderOutputBuffer` instances resolve to the same native `outputFrames_[idx]` slot, overwriting active frames before they are rendered to the surface.
* **Missing Annex-B Conversion for MP4/MKV NALUs:**
  - `ffmpeg_video_decoder.cpp` (lines 106-114) copies packet payload directly (`std::memcpy(packet_->data, packetData, packetSize)`).
  - In MP4/MKV containers, H.264 and HEVC packets use 4-byte length prefixes (AVCC/HVCC) rather than Annex-B start codes (`00 00 00 01`).
  - When SPS/PPS extradata is supplied in Annex-B or when bitstream filtering is omitted, `avcodec_send_packet` fails to parse packets with NAL lengths, leading to skipped frames or decoder errors.
* **Premature EOS Truncation:**
  - In `FFmpegVideoDecoder.kt` (lines 106-110), when `inputBuffer.isEndOfStream` is received, the decoder marks `outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)` immediately and returns.
  - Any cached or reordered B-frames still buffered in FFmpeg's internal pipeline (`avcodec_receive_frame`) or `pendingFrames_` queue are never output, cutting off the last 2 to 4 frames of video.
* **PTS Fallback Out-of-Order in B-frame Sequences:**
  - In `ffmpeg_video_decoder.cpp` (line 178), if an output frame's PTS is unset (`AV_NOPTS_VALUE`), it falls back to `ptsUs` (the timestamp of the packet currently passed to `decode()`).
  - Because B-frames are decoded out of display order, assigning the current packet's timestamp to a delayed frame inverts presentation timestamps and causes severe playback judder.

#### `Dav1dVideoDecoder.kt` & `dav1d_video_decoder.cpp`
* **EOS Hang in `Dav1dVideoDecoder.kt`:**
  - In `Dav1dVideoDecoder.kt` (lines 110-120), when draining frames during EOS:
    If a picture is drained (`eosResult == 0`), the output buffer is returned without `BUFFER_FLAG_END_OF_STREAM`.
    Because `SimpleDecoder` expects the stream to end once an EOS input buffer is consumed, failing to attach `BUFFER_FLAG_END_OF_STREAM` on the terminal call prevents Media3 from transitioning to `Player.STATE_ENDED`.
* **Surface Buffer Reallocation Cache Invalidation:**
  - `cachedWidth_`, `cachedHeight_`, and `cachedFormat_` are cached in the decoder instance. If the user rotates the device or triggers a surface change without resizing the video stream, `ANativeWindow_setBuffersGeometry` is not re-invoked on the new `ANativeWindow`, leading to distorted aspect ratio or blank frames.

---

### 2.2 Audio Decoding Pipeline

#### `FFmpegAudioDecoder.kt` & `ffmpeg_audio_decoder.cpp`
* **Hardcoded Stereo Downmix:**
  - In `ffmpeg_audio_decoder.cpp` (lines 80-85 and 125-133), `outChannelCount_` is forced to `(inChannels == 1) ? 1 : 2`.
  - Surround sound streams (E-AC-3 5.1, TrueHD 7.1, DTS 5.1/7.1) are unconditionally downmixed to stereo 2.0. Multi-channel audio output over HDMI or spatial audio is completely blocked.
* **Resampler Buffer Trapping & Phase Offset:**
  - Resampled audio packets are written to a fixed 64 KB capacity buffer. When input packets decode to more samples than fit, or when `SwrContext` buffers internal samples across packets, no delay compensation (`swr_get_delay`) is performed. This introduces a progressive phase drift between audio and video timestamps.
* **Audio Flush Residuals:**
  - In `flush()` (lines 234-244), `swr_close` and `swr_init` are called, but buffered samples inside `SwrContext` are discarded without draining, causing audible popping/clicking during seeking.

---

### 2.3 Media3 Integration & Capabilities

#### `FFmpegRenderersFactory.kt`
* **Coupled Priority Control:**
  - A single `softwareDecoderPriority` property controls both `buildVideoRenderers` and `buildAudioRenderers`.
  - Setting `SoftwareDecoderPriority.PREFER` forces audio to software even if hardware AAC/MP3 is available.
  - Setting `SoftwareDecoderPriority.DISABLED` disables software audio fallback, causing silent playback for unsupported codecs (DTS, E-AC-3) on devices without hardware decoders.
* **Renderer Ordering in `FALLBACK` Mode:**
  - In `SoftwareDecoderPriority.FALLBACK`, `MediaCodecVideoRenderer` is added at index 0. Because MediaCodec handles all standard codecs (using Google software codecs `c2.android.*` as fallback), `dav1d` and `FFmpeg` renderers are never reached for video.

#### `VideoDecoderCapabilities.kt` & `AudioDecoderCapabilities.kt`
* **Superficial Capability Checking:**
  - Capabilities only check string sets of MIME types. There is zero validation of profile, level, max width, max height, bit depth, or frame rate.
  - Unsupported claims: `audio/ac4`, `audio/mlp`, and `audio/x-ms-wmapro` are claimed as supported even if underlying FFmpeg build configuration lacks functional decoders for them.

#### `CodecCapabilities.kt`
* **Hardcoded Codec Reports:**
  - Hardcoded profile and bit-depth strings are returned rather than querying native FFmpeg or dav1d capabilities at runtime.

---

## 3. Surface Rendering, HDR, and Color Management Audit

1. **Incorrect Color Space Conversion (BT.601 Fallback):**
   - Both `ffmpeg_video_decoder.cpp` (line 231) and `dav1d_video_decoder.cpp` (line 177) instantiate `sws_getCachedContext` without setting color space details via `sws_setColorspaceDetails`.
   - By default, `libswscale` applies BT.601 coefficients.
   - For HD (720p/1080p, BT.709) and UHD/HDR (4K, BT.2020), this causes noticeable color distortion (green tint shift and altered skin tones).
2. **10-bit Video Truncation to 8-bit RGBA:**
   - Both decoders configure `ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888)`.
   - 10-bit video streams (AV1 Profile 2, HEVC Main 10) are truncated to 8-bit RGBA, causing visible color banding on HDR-capable screens and wasting native 10-bit decoding capability.
3. **Hardcoded Scaler Quality (`SWS_FAST_BILINEAR`):**
   - Uses low-precision scaling, resulting in softer or aliased output on high-resolution displays.

---

## 4. JNI & Native Resource Management Audit

1. **Per-Frame `ANativeWindow` Acquisition Overhead:**
   - In `native_bridge.cpp` (lines 232 and 373), `ANativeWindow_fromSurface` and `ANativeWindow_release` are invoked on every frame render.
   - While leak-free, acquiring and releasing JNI window references at 60 fps adds lock contention and binder transactions.
2. **Missing JNI Exception Handling:**
   - Boundary methods in `native_bridge.cpp` lack `env->ExceptionCheck()` after array operations and buffer lookups.

---

## 5. Summary of Identified Defects

| Defect ID | Severity | File | Root Cause |
| :--- | :--- | :--- | :--- |
| **DEF-01** | **Critical** | `ffmpeg_video_decoder.cpp` / `FFmpegVideoDecoder.kt` | Buffer index collision when `numOutputBuffers > 16` |
| **DEF-02** | **Critical** | `FFmpegVideoDecoder.kt` | Immediate EOS flag truncates tail B-frames |
| **DEF-03** | **Critical** | `Dav1dVideoDecoder.kt` | Draining frame on EOS without terminal EOS flag hangs playback |
| **DEF-04** | **High** | `ffmpeg_video_decoder.cpp` | Missing length-prefixed NAL to Annex-B conversion for MP4/MKV H.264/HEVC |
| **DEF-05** | **High** | `ffmpeg_video_decoder.cpp` | Out-of-order PTS assignment on B-frame decode when PTS is unset |
| **DEF-06** | **High** | `ffmpeg_audio_decoder.cpp` | Multi-channel surround audio unconditionally downmixed to stereo |
| **DEF-07** | **High** | `FFmpegRenderersFactory.kt` | Coupled audio and video software decoder priorities |
| **DEF-08** | **Medium** | `ffmpeg_video_decoder.cpp` / `dav1d_video_decoder.cpp` | BT.601 color matrix used for BT.709/BT.2020 content |
| **DEF-09** | **Medium** | `ffmpeg_video_decoder.cpp` / `dav1d_video_decoder.cpp` | 10-bit content truncated to 8-bit `RGBA_8888` |
| **DEF-10** | **Medium** | `FFmpegVideoRenderer.kt` / `Dav1dVideoRenderer.kt` | Decoder reported active prematurely in `createDecoder` |
| **DEF-11** | **Medium** | `VideoDecoderCapabilities.kt` / `AudioDecoderCapabilities.kt` | Format capabilities claim support without profile/level or runtime validation |
| **DEF-12** | **Low** | `native_bridge.cpp` | Per-frame `ANativeWindow_fromSurface` lookup overhead |

