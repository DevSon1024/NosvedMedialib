# NosvedMedia - Decoder Selection Engine and Diagnostics Architecture

## 1. Overview & Core Philosophy

The NosvedMedia Decoder Selection Engine decouples decoder preference, renderer evaluation, and real-time playback telemetry into an atomic, capability-aware architecture.

Prior to Phase 1, media engines frequently conflated three distinct states:
1. **Renderer Registration:** Merely constructing an extension renderer.
2. **Decoder Initialization:** JNI instantiation of a native codec context.
3. **Active Playback:** Verifiably rendering decoded video frames to the Android `Surface` or pushing audio PCM frames to `AudioSink`.

Phase 1 establishes a strict zero-hallucination contract: **A decoder engine is never reported as active until it has rendered its first valid output frame/sample.**

---

## 2. Independent Decoder Preferences API

Independent control over audio and video tracks allows asymmetric decoding strategies (e.g., hardware 4K HEVC video alongside software Opus/FLAC audio).

```kotlin
data class DecoderPreferences(
    val video: DecoderPreference = DecoderPreference.AUTO,
    val audio: DecoderPreference = DecoderPreference.AUTO,
    val allowFallback: Boolean = true
)

enum class DecoderPreference {
    AUTO,
    PLATFORM_PREFERRED,
    SOFTWARE_PREFERRED,
    PLATFORM_ONLY,
    SOFTWARE_ONLY
}
```

### Preference Behavior Matrix

| Preference | Video Renderers Order | Audio Renderers Order | Platform Fallback Allowed? | Software Fallback Allowed? |
| :--- | :--- | :--- | :--- | :--- |
| **AUTO** | Adaptive capability check | Adaptive capability check | Yes (if enabled) | Yes (if enabled) |
| **PLATFORM_PREFERRED** | MediaCodec -> dav1d -> FFmpeg | MediaCodec -> FFmpeg | N/A (primary) | Yes (if enabled) |
| **SOFTWARE_PREFERRED** | dav1d -> FFmpeg -> MediaCodec | FFmpeg -> MediaCodec | Yes (if enabled) | N/A (primary) |
| **PLATFORM_ONLY** | MediaCodec exclusively | MediaCodec exclusively | N/A | **Strictly Forbidden** |
| **SOFTWARE_ONLY** | dav1d -> FFmpeg exclusively | FFmpeg exclusively | **Strictly Forbidden** | N/A |

### Consumer Backward Compatibility Mapping
Legacy `DecoderMode` settings map directly without breaking existing apps:
- `DecoderMode.HW` -> `PLATFORM_ONLY` (`allowFallback = false`)
- `DecoderMode.HW_PLUS` -> `PLATFORM_PREFERRED` (`allowFallback = true`)
- `DecoderMode.SW` -> `SOFTWARE_ONLY` (`allowFallback = false`)

---

## 3. Capability-Aware Selection Algorithm

Renderer selection does not rely on static MIME type tables. Each stream candidate undergoes evaluation:

1. **DRM & Encryption Check:**
   - Any format requiring `drmInitData` or `cryptoType != C.CRYPTO_TYPE_NONE` is rejected by custom software renderers (`FORMAT_UNSUPPORTED_DRM`).
2. **Native Backend Verification:**
   - Dynamic query to `NativeLib.isCodecSupported` / `isAudioMimeTypeSupported`.
   - Rejects unverified codecs (e.g., AC-4).
3. **Resolution & Dimension Bounds:**
   - Evaluates width/height (up to 4096x2304 / 4K).
   - Dimension <= 0 or missing dimensions are deferred to container probe.
4. **AV1 Dedicated Path:**
   - AV1 streams (`video/av01`) route to `Dav1dVideoRenderer` first, bypassing FFmpeg's slower libdav1d wrapper for direct SIMD decoding.
5. **Renderer Ordering:**
   - `FFmpegRenderersFactory` arranges `ArrayList<Renderer>` according to `DecoderPreferences`. When `SOFTWARE_ONLY` is requested, `MediaCodec` renderers are omitted entirely to prevent silent hardware failover.

---

## 4. Unified Decoder Lifecycle State Model

```
IDLE
  └──> REQUESTED
         └──> CANDIDATE_SELECTED
                └──> INITIALIZING
                       └──> INITIALIZED
                              └──> FIRST_FRAME_DECODED
                                     └──> PLAYING (Reported as Active)
                                            ├──> FALLBACK_PENDING ──> FAILED
                                            └──> RELEASED
```

```kotlin
enum class DecoderState {
    IDLE,
    REQUESTED,
    CANDIDATE_SELECTED,
    INITIALIZING,
    INITIALIZED,
    FIRST_FRAME_DECODED,
    PLAYING,
    FALLBACK_PENDING,
    FAILED,
    RELEASED
}
```

### State Transition Invariants
- `INITIALIZING` or `INITIALIZED` must never update user-facing active decoder badges or telemetry overlays.
- `FIRST_FRAME_DECODED` occurs upon first successful buffer release.
- `PLAYING` confirms the frame reached the `Surface` (video) or `AudioSink` position advanced (audio).
- Switching tracks or media items transitions states to `IDLE` and resets telemetry counters.

---

## 5. Structured Error Classification

Stale error-message parsing is replaced with structured categorization:

```kotlin
enum class DecodingCategory {
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_PROFILE,
    DECODER_INITIALIZATION_FAILED,
    DECODING_FAILED,
    INVALID_CODEC_CONFIGURATION,
    SURFACE_RENDERING_FAILED,
    AUDIO_OUTPUT_FAILED,
    MEMORY_ALLOCATION_FAILED,
    SOURCE_ERROR
}

data class StructuredPlaybackError(
    val category: DecodingCategory,
    val trackType: TrackType,
    val decoderEngine: String?,
    val format: Format?,
    val message: String,
    val cause: Throwable? = null
)
```

### Fallback Policy
- When `allowFallback = true` (e.g. `HW+` / `PLATFORM_PREFERRED`):
  - Hardware decoder crash or format unhandled triggers fallback to `SW` (dav1d/FFmpeg).
- When `allowFallback = false` (e.g. `SW` / `SOFTWARE_ONLY` or `HW` / `PLATFORM_ONLY`):
  - No fallback occurs. The structured error is propagated directly to the UI, guaranteeing that `SOFTWARE_ONLY` mode never silently falls back to hardware decoding.

---

## 6. Real-Time Telemetry & Diagnostics Integration

- **Video FPS:** Calculated from actual rendered frames. For hardware renderers, hooked via `VideoFrameMetadataListener`; for software renderers, computed by `PlaybackDiagnosticsListener.onPerformanceMetricsUpdated`.
- **Diagnostics Reset Contract:**
  - Media transition -> clears active decoder names, resets states to `IDLE`, clears FPS.
  - Seek / discontinuity -> resets transient frame rate metrics to eliminate speed spikes.
  - Release -> sets states to `RELEASED`, tears down native contexts safely.

