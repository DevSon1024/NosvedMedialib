# KNOWN ISSUES & TECHNICAL DEBT REGISTER

**Repository:** `NosvedMedia` & `Nosved-Player-Lite`  
**Phase:** Phase 0 Baseline Audit  
**Date:** 2026-11-05  

---

## 1. Confirmed Source Defects

### ISSUE-01: Native Output Buffer Registry Index Collision
* **Severity:** **Critical**
* **Affected Source File:** 
  - `native-codecs/src/main/cpp/video/ffmpeg_video_decoder.cpp` (lines 99, 166, 201)
  - `native-codecs/src/main/cpp/video/ffmpeg_video_decoder.h` (line 22)
  - `nosved-media3/src/main/java/com/nosved/nosvedmedia/media3/FFmpegVideoDecoder.kt` (lines 33-37)
* **Root Cause:** 
  `MAX_OUTPUT_BUFFERS` in C++ is fixed to 16, whereas Kotlin's `VideoDecoderConfig` allows arbitrary buffer counts. In Kotlin, `IdentityHashMap` uses an incremental modulo counter. When indices wrap or exceed 16, two different `VideoDecoderOutputBuffer` instances point to the exact same native `outputFrames_[idx]` slot. A newly decoded frame will overwrite an unrendered frame in flight, or releasing buffer A will invalidate buffer B.
* **Reproduction Conditions:** 
  High-bitrate streams or high frame rates where queue depth exceeds 16 frames, or configuring `VideoDecoderConfig(numOutputBuffers = 24)`.
* **Recommended Fix:** 
  Align Kotlin buffer pool capacity strictly with native slot capacity, or pass opaque token handles directly to avoid modulo mapping collisions.
* **Dependencies:** Phase 1 (Diagnostics & Architecture Alignment).

---

### ISSUE-02: Silent Frame Dropping on End-of-Stream (EOS) Truncation
* **Severity:** **Critical**
* **Affected Source File:**
  - `nosved-media3/src/main/java/com/nosved/nosvedmedia/media3/FFmpegVideoDecoder.kt` (lines 106-110)
* **Root Cause:**
  When `inputBuffer.isEndOfStream` is true, the decoder invokes `nativeLib.decodeVideo(..., isEos = true)` and immediately tags `outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)` before receiving pending drained frames. FFmpeg's delayed internal frames (reordered B-frames) in `pendingFrames_` or `avcodec_receive_frame` are never returned to the renderer.
* **Reproduction Conditions:**
  Playing short video clips or streams with trailing B-frames; last 2 to 5 frames cut off abruptly before reaching the final timestamp.
* **Recommended Fix:**
  Draining loop must continue returning frames until `avcodec_receive_frame` returns `AVERROR_EOF`. Only then should `BUFFER_FLAG_END_OF_STREAM` be applied.
* **Dependencies:** Phase 1 / Phase 3 (Seeking & EOS Handling).

---

### ISSUE-03: Playback Hang on dav1d AV1 End-of-Stream (EOS)
* **Severity:** **Critical**
* **Affected Source File:**
  - `nosved-media3/src/main/java/com/nosved/nosvedmedia/media3/Dav1dVideoDecoder.kt` (lines 110-120)
* **Root Cause:**
  In `Dav1dVideoDecoder.kt`, when `eosResult == 0` during an EOS drain call, the output buffer is initialized with video data but without `BUFFER_FLAG_END_OF_STREAM`. `SimpleDecoder` does not automatically enqueue subsequent empty input buffers once the stream has ended, causing the player pipeline to stall waiting for the final EOS signal.
* **Reproduction Conditions:**
  Playing any AV1 video stream to completion under SW decoder mode (`Dav1dVideoRenderer`).
* **Recommended Fix:**
  Properly implement the multi-pass drain protocol where the decoder signals EOS on the terminal empty buffer when `dav1d_get_picture` returns `DAV1D_ERR(EAGAIN)`.
* **Dependencies:** Phase 1 / Phase 3 (Seeking & EOS Handling).

---

### ISSUE-04: Missing Length-Prefixed NAL to Annex-B Conversion for MP4/MKV
* **Severity:** **High**
* **Affected Source File:**
  - `native-codecs/src/main/cpp/video/ffmpeg_video_decoder.cpp` (lines 106-114)
* **Root Cause:**
  H.264 and HEVC packets in MP4 and MKV containers are formatted with 4-byte big-endian NAL lengths (AVCC/HVCC format). `FfmpegVideoDecoder::decode()` calls `std::memcpy` into `AVPacket` without bitstream filtering (`h264_mp4toannexb`, `hevc_mp4toannexb`) or in-place 4-byte length conversion.
* **Reproduction Conditions:**
  Software decoding of H.264 / HEVC video files where the demuxer outputs AVCC/HVCC packets.
* **Recommended Fix:**
  Implement in-place NAL length-to-start-code (`00 00 00 01`) conversion or initialize an `AVBSFContext` (`h264_mp4toannexb` / `hevc_mp4toannexb`) in `FfmpegVideoDecoder`.
* **Dependencies:** Phase 1 / Phase 4 (Native Engine Robustness).

---

### ISSUE-05: Out-of-Order Timestamps in B-Frame Decoding Sequences
* **Severity:** **High**
* **Affected Source File:**
  - `native-codecs/src/main/cpp/video/ffmpeg_video_decoder.cpp` (lines 178)
* **Root Cause:**
  When `outputFrames_[idx]->pts == AV_NOPTS_VALUE`, the code falls back to `ptsUs` (the PTS of the packet currently passed to `decode()`). In B-frame streams, packet decode order differs from presentation order. Falling back to the packet PTS corrupts the timeline and causes frame jitter.
* **Reproduction Conditions:**
  H.264/HEVC streams with consecutive B-frames and missing per-frame container timestamps.
* **Recommended Fix:**
  Maintain a monotonic presentation timestamp queue or compute interpolated PTS based on frame duration.
* **Dependencies:** Phase 3 (Timestamp Synchronization & Seeking).

---

### ISSUE-06: Unconditional Surround Audio Downmix to Stereo
* **Severity:** **High**
* **Affected Source File:**
  - `native-codecs/src/main/cpp/audio/ffmpeg_audio_decoder.cpp` (lines 80-85, 125-133)
* **Root Cause:**
  `outChannelCount_` is hardcoded to `(inChannels == 1) ? 1 : 2`, and output layout is forced to `AV_CHANNEL_LAYOUT_STEREO`. 5.1/7.1 audio tracks (Dolby Digital Plus, TrueHD, DTS-HD) cannot output multi-channel PCM to Android `AudioTrack`.
* **Reproduction Conditions:**
  Playing multi-channel audio tracks on surround setups (HDMI AVR, Android TV, multichannel soundbars).
* **Recommended Fix:**
  Query `AudioSink` capabilities; allow direct 6-channel or 8-channel PCM 16-bit output when supported by device sink.
* **Dependencies:** Phase 2 / Phase 4 (Audio Fidelity).

---

### ISSUE-07: Coupled Audio and Video Software Decoder Priorities
* **Severity:** **High**
* **Affected Source File:**
  - `nosved-media3/src/main/java/com/nosved/nosvedmedia/media3/FFmpegRenderersFactory.kt` (lines 78-86, 148-155)
* **Root Cause:**
  `FFmpegRenderersFactory` uses a single `softwareDecoderPriority` enum for both video and audio. Setting `SoftwareDecoderPriority.DISABLED` disables both video and audio software fallback, resulting in muted audio on devices lacking hardware decoders for DTS/E-AC-3.
* **Reproduction Conditions:**
  Setting priority to `DISABLED` or `PREFER` in `FFmpegRenderersFactory`.
* **Recommended Fix:**
  Decouple into `videoDecoderPriority` and `audioDecoderPriority` while keeping backward-compatible constructors.
* **Dependencies:** Phase 2 (Decoder Selection Architecture).

---

### ISSUE-08: Color Matrix Distortion on HD/UHD Video (BT.601 Fallback)
* **Severity:** **Medium**
* **Affected Source File:**
  - `native-codecs/src/main/cpp/video/ffmpeg_video_decoder.cpp` (lines 231-237)
  - `native-codecs/src/main/cpp/video/dav1d_video_decoder.cpp` (lines 177-183)
* **Root Cause:**
  `sws_getCachedContext` is invoked without setting color space coefficients via `sws_setColorspaceDetails`. By default, `libswscale` defaults to SD (BT.601) coefficients, distorting greens and reds for BT.709 (1080p) and BT.2020 (4K).
* **Reproduction Conditions:**
  Playing 1080p or 4K SDR/HDR video in SW mode.
* **Recommended Fix:**
  Inspect frame color space metadata (`frame->colorspace`, `frame->color_range`) and invoke `sws_setColorspaceDetails` with appropriate coefficients.
* **Dependencies:** Phase 4 (Color Quality & HDR).

---

### ISSUE-09: 10-Bit Color Depth Truncation to 8-Bit RGBA
* **Severity:** **Medium**
* **Affected Source File:**
  - `native-codecs/src/main/cpp/video/ffmpeg_video_decoder.cpp` (line 213)
  - `native-codecs/src/main/cpp/video/dav1d_video_decoder.cpp` (line 158)
* **Root Cause:**
  `ANativeWindow_setBuffersGeometry` is hardcoded to `WINDOW_FORMAT_RGBA_8888`. 10-bit YUV content decoded by dav1d or FFmpeg is downscaled to 8-bit RGB, causing color banding on 10-bit displays.
* **Reproduction Conditions:**
  Playing 10-bit AV1 or HEVC Main 10 video on an HDR-capable Android device.
* **Recommended Fix:**
  On Android API 26+, configure `WINDOW_FORMAT_RGBA_1010102` for 10-bit sources when supported by the display.
* **Dependencies:** Phase 4 (Color Quality & HDR).

---

### ISSUE-10: Premature Active Decoder Reporting
* **Severity:** **Medium**
* **Affected Source File:**
  - `nosved-media3/src/main/java/com/nosved/nosvedmedia/media3/FFmpegVideoRenderer.kt` (line 59)
  - `nosved-media3/src/main/java/com/nosved/nosvedmedia/media3/Dav1dVideoRenderer.kt` (line 58)
* **Root Cause:**
  `selectionListener?.onDecoderSelected(getName(), true, format)` is fired during `createDecoder()` before any frame has been decoded or rendered.
* **Reproduction Conditions:**
  Any playback initiation where decoder creation succeeds but stream decoding fails on the first frame.
* **Recommended Fix:**
  Trigger decoder active notifications only when the first frame is rendered to the surface.
* **Dependencies:** Phase 1 (Diagnostics Accuracy).

---

### ISSUE-11: Broken SW Frame Rate Calculation in Player Lite
* **Severity:** **Medium**
* **Affected Source File:**
  - `C:\Android\NosvedPlayerLite\app\src\main\java\com\devson\nvplayerlite\player\PlayerManager.kt` (lines 541-561)
* **Root Cause:**
  `PlayerManager` relies on `ExoPlayer.setVideoFrameMetadataListener` to compute dynamic FPS. `DecoderVideoRenderer` does not support `MSG_SET_VIDEO_FRAME_METADATA_LISTENER`, so the callback never fires during software decoding.
* **Reproduction Conditions:**
  Enable Stats Overlay during SW video playback in Nosved Player Lite; FPS remains `0.0`.
* **Recommended Fix:**
  Hook `PerformanceMonitor.currentFps` directly into `PlayerManager` via `PlaybackDiagnosticsListener`.
* **Dependencies:** Phase 1 (Diagnostics Accuracy & Integration).

---

### ISSUE-12: Sample App `compileSdk` Mismatch
* **Severity:** **Low**
* **Affected Source File:**
  - `app/build.gradle.kts` (line 7)
* **Root Cause:**
  `app` is configured with `compileSdk = 35`, while `:nosved-media3` and `:native-codecs` use `compileSdk = 36` to match Media3 1.11.0 AAR metadata. `:app:checkDebugAarMetadata` fails.
* **Reproduction Conditions:**
  Running `./gradlew assembleDebug` or `./gradlew test`.
* **Recommended Fix:**
  Update `app/build.gradle.kts` to `compileSdk = 36`.
* **Dependencies:** Phase 1 (Build Alignment).

---

## 2. Issues Requiring Device-Level Reproduction

1. **Surface Geometry Invalidation Across Configuration Changes:**
   - *Hypothesis:* When an activity rotates or changes aspect ratio, `ANativeWindow` might require geometry re-negotiation even if video resolution has not changed.
   - *Test Plan:* Measure buffer allocation behavior during device rotation on physical device.
2. **Audio Track Under-run on High-Bitrate TrueHD / DTS-HD Streams:**
   - *Hypothesis:* Resampling heavy multi-channel audio on low-end ARM cores might exceed real-time processing deadlines.
   - *Test Plan:* Profile CPU consumption of `FfmpegAudioDecoder` decoding 7.1 TrueHD on a connected physical test device.

