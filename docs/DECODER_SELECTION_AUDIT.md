# DECODER SELECTION & PLAYER LITE INTEGRATION AUDIT

**Target:** `DevSon1024/Nosved-Player-Lite`  
**Branch:** `audit/player-integration-baseline`  
**Date:** 2026-11-05  

---

## 1. Executive Summary

This audit evaluates the integration between the **NosvedMedia** decoding library and the consumer application **Nosved Player Lite**.

The audit examined player lifecycle management in `PlayerManager.kt`, fallback behavior in `HDRFallbackRenderersFactory`, UI selection in `DecoderSelectorDialog.kt` and `DecoderSideSheet.kt`, and diagnostics in `DeviceStatsOverlay.kt`.

### Key Findings
1. **HW+ Video Behavior is Indistinguishable from HW:** MediaCodec video renderer is prioritized first and accepts all standard video formats supported by hardware or Google platform software codecs (`c2.android.*`). NosvedMedia software video decoders (`dav1d`, `FFmpeg`) are never invoked in `HW+` mode for video.
2. **Missing Diagnostics & Telemetry Callbacks:** `PlayerManager` never passes `selectionListener` or `diagnosticsListener` to `FFmpegRenderersFactory`.
3. **Broken SW Frame Rate Telemetry:** `PlayerManager` relies on `ExoPlayer.setVideoFrameMetadataListener` to compute dynamic FPS for software playback. Because Media3's `DecoderVideoRenderer` does not support `MSG_SET_VIDEO_FRAME_METADATA_LISTENER`, `onVideoFrameAboutToBeRendered` is never called, and FPS displays as `0.0`.
4. **Premature Active Decoder Assertion:** Decoders are assumed active on initialization rather than on first successfully rendered frame.

---

## 2. Mechanics of HW, HW+, and SW Modes

### 2.1 Mode Configuration Table

| Mode | `extensionRendererMode` | Video Renderer Priority | Audio Renderer Priority | Intended Behavior | Actual Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **HW** | `EXTENSION_RENDERER_MODE_OFF` | MediaCodec only | MediaCodec + SW Fallback | Hardware decoding only | Hardware video decoding; audio still falls back to FFmpeg for unsupported formats |
| **HW+** | `EXTENSION_RENDERER_MODE_ON` | MediaCodec, then SW Fallback | MediaCodec + SW Fallback | Hardware preferred with software fallback | Video uses MediaCodec 100% of the time (including slow Google platform SW codecs); SW video never reached |
| **SW** | `EXTENSION_RENDERER_MODE_PREFER` | dav1d, FFmpeg, then MediaCodec | FFmpeg, then MediaCodec | Software decoding forced | Software decoders active for both video and audio; FPS stuck at 0.0 |

---

### 2.2 Deep Dive: Why HW+ Does Not Use Software Video Decoders

In `PlayerManager.kt` (lines 174-186):
```kotlin
val extensionMode = when (decoderMode) {
    DecoderMode.HW      -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
    DecoderMode.HW_PLUS -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    DecoderMode.SW      -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
}
val renderersFactory = HDRFallbackRenderersFactory(context).apply {
    setExtensionRendererMode(extensionMode)
    setEnableDecoderFallback(true)
    forceDisableMediaCodecAsynchronousQueueing()
    if (decoderMode == DecoderMode.SW) {
        setSoftwareDecoderPriority(SoftwareDecoderPriority.PREFER)
    }
}
```

In `FFmpegRenderersFactory.buildVideoRenderers`:
* For `HW+` (`EXTENSION_RENDERER_MODE_ON`), `effectivePriority` is `SoftwareDecoderPriority.FALLBACK`.
* `super.buildVideoRenderers(...)` is called first, which registers `MediaCodecVideoRenderer` at index 0.
* `dav1dRenderer` and `ffmpegRenderer` are added at the end of the `ArrayList<Renderer>`.
* During track selection, Media3 queries renderers in list order.
* For H.264, HEVC, and VP9, Android devices almost universally register either a hardware decoder (`c2.qti.*`, `c2.exynos.*`, `c2.mtk.*`) or a platform software decoder (`c2.android.*`, `omx.google.*`).
* `MediaCodecVideoRenderer.supportsFormat` returns `FORMAT_HANDLED` (or `FORMAT_EXCEEDS_CAPABILITIES`).
* Media3 selects `MediaCodecVideoRenderer` immediately.
* **Result:** Software video decoders are never selected in `HW+` mode unless the codec is completely missing from `MediaCodecList` (e.g. VC-1, MPEG-2).

---

## 3. Telemetry and Diagnostics Deficiencies

### 3.1 Unused NosvedMedia Callbacks
In `PlayerManager.kt`:
* `HDRFallbackRenderersFactory` is instantiated directly without setting `selectionListener` or `diagnosticsListener`.
* NosvedMedia's `PlaybackDiagnosticsListener` provides:
  - `onDecoderInitialized`
  - `onPerformanceMetricsUpdated` (real-time decode time, max/min latency, memory usage, FPS, dropped frames)
  - `onDecodingError`
* None of these metrics are connected to `PlayerManager`.

### 3.2 Broken FPS Display in Software Mode
In `PlayerManager.kt` (lines 541-561):
```kotlin
setVideoFrameMetadataListener(object : VideoFrameMetadataListener {
    private var frameCount = 0
    private var lastTimeMs = System.currentTimeMillis()

    override fun onVideoFrameAboutToBeRendered(...) {
        frameCount++
        ...
    }
})
```
* **Issue:** `ExoPlayer.setVideoFrameMetadataListener` communicates with renderers by sending `MSG_SET_VIDEO_FRAME_METADATA_LISTENER` (Message 7).
* `DecoderVideoRenderer` and its subclasses (`FFmpegVideoRenderer`, `Dav1dVideoRenderer`) do not implement handling for this message.
* In SW mode, `onVideoFrameAboutToBeRendered` is never invoked.
* In `DeviceStatsOverlay.kt`, `StatItem("Framerate", playbackStats.videoFramerate)` displays `0.0 FPS` or format metadata FPS instead of actual rendered FPS.

### 3.3 Premature & Fabricated Decoder Reporting
In `DeviceStatsOverlay.kt` (lines 473-493):
* If `videoDecoderName` is null, `parseVideoDecoder` fabricates a status based purely on `decoderMode`:
  ```kotlin
  return when (decoderMode) {
      DecoderMode.SW -> DecoderInfo(badge = "SW", isHardware = false, decoderName = if (sampleMimeType == "video/av01") "NosvedMedia dav1d" else "NosvedMedia FFmpeg"...)
      DecoderMode.HW -> DecoderInfo(badge = "HW", isHardware = true, decoderName = "MediaCodec Hardware"...)
      else -> DecoderInfo(badge = "HW+", isHardware = true, decoderName = "MediaCodec + Nosved Fallback"...)
  }
  ```
* This creates a false impression that software fallback is actively decoding when it is actually idle.

---

## 4. Decoder Mode Switching Latency

In `VideoViewModel.kt` (lines 245-263):
```kotlin
fun setDecoderMode(mode: DecoderMode) {
    ...
    val (savedPos, wasPlaying) = playerManager?.releasePlayerAndKeepState() ?: return@launch
    playerManager = PlayerManager(getApplication())
    playerManager?.initializePlayer(mode, ...)
}
```
* Switching between `HW`, `HW+`, and `SW` tears down the entire `PlayerManager`, `ExoPlayer`, and `MediaSession`.
* Causes complete playback stall (1000-2000 ms), audio rebuffering, and surface recreation.
* **Recommendation:** Support dynamic renderer track disable/enable or hot renderer parameters rather than full player recreation.

---

## 5. Required Integration Fixes

1. **Implement Frame-Rendered Callback:**
   - Introduce an `onFirstFrameRendered` listener in `FFmpegVideoRenderer` and `Dav1dVideoRenderer`.
   - Update `PlayerManager` only when a frame is verified to be on screen.
2. **Expose Performance Metrics to Player Lite:**
   - Pass `PlaybackDiagnosticsListener` from `PlayerManager` to `FFmpegRenderersFactory`.
   - Pipe `PerformanceMonitor.currentFps` directly to `_videoFps` during SW decoding.
3. **Smart Decoder Selector in HW+:**
   - Enable intelligent format routing: if a 10-bit or high-profile stream has known platform decoder stutter, automatically route to `Dav1dVideoRenderer` or `FFmpegVideoRenderer`.

