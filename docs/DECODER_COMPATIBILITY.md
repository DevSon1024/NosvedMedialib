# NosvedMedia Decoder Compatibility Matrix

**Library Version:** 1.0.0  
**Framework:** AndroidX Media3 1.5.1  

---

## 1. Video Codec Compatibility Matrix

| Codec | MIME Type | Hardware Engine | Software Engine | Max Profile / Level | Max Resolution | Bit Depth |
|:---|:---|:---:|:---:|:---:|:---:|:---:|
| **H.264 (AVC)** | `video/avc` | MediaCodec | FFmpeg 7.1 | High Profile @ L5.2 | 4K @ 60fps | 8-bit, 10-bit (Hi10P) |
| **H.265 (HEVC)** | `video/hevc` | MediaCodec | FFmpeg 7.1 | Main / Main 10 @ L6.2 | 4K @ 60fps | 8-bit, 10-bit |
| **AV1** | `video/av01` | MediaCodec (API 29+) | dav1d 1.5.0 | Main Profile @ L5.3 | 4K @ 60fps | 8-bit, 10-bit |
| **VP9** | `video/x-vnd.on2.vp9` | MediaCodec | FFmpeg 7.1 | Profile 0, Profile 2 | 4K @ 60fps | 8-bit, 10-bit |
| **VP8** | `video/x-vnd.on2.vp8` | MediaCodec | FFmpeg 7.1 | Baseline | 1080p @ 60fps | 8-bit |
| **MPEG-2 Video** | `video/mpeg2` | MediaCodec (device dependent) | FFmpeg 7.1 | Main Profile @ High Level | 1080p @ 60fps | 8-bit |
| **MPEG-4 Part 2** | `video/mp4v-es` | MediaCodec | FFmpeg 7.1 | Advanced Simple Profile | 1080p @ 60fps | 8-bit |
| **VC-1 / WMV3** | `video/wvc1` | Platform dependent | FFmpeg 7.1 | Advanced Profile @ L3 | 1080p @ 30fps | 8-bit |
| **Apple ProRes** | `video/x-prores` | None | FFmpeg 7.1 | 422, 422 HQ, 4444 | 4K @ 30fps | 10-bit |

---

## 2. Audio Codec Compatibility Matrix

| Codec | MIME Type | Hardware Engine | Software Engine | Channels Supported | Sample Rates | Bit Depth |
|:---|:---|:---:|:---:|:---:|:---:|:---:|
| **AAC / HE-AAC** | `audio/mp4a-latm` | MediaCodec | FFmpeg 7.1 | Mono, Stereo, 5.1, 7.1 | 8 kHz – 96 kHz | 16-bit, Float |
| **MP3** | `audio/mpeg` | MediaCodec | FFmpeg 7.1 | Mono, Stereo | 16 kHz – 48 kHz | 16-bit |
| **FLAC** | `audio/flac` | MediaCodec | FFmpeg 7.1 | Up to 7.1 Surround | 8 kHz – 192 kHz | 16-bit, 24-bit, Float |
| **Opus** | `audio/opus` | MediaCodec | FFmpeg 7.1 | Mono, Stereo, 5.1 | 48 kHz | 16-bit, Float |
| **Vorbis** | `audio/vorbis` | MediaCodec | FFmpeg 7.1 | Up to 7.1 Surround | 8 kHz – 96 kHz | 16-bit, Float |
| **Apple ALAC** | `audio/alac` | None | FFmpeg 7.1 | Stereo, 5.1 | 44.1 kHz – 192 kHz | 16-bit, 24-bit |
| **Dolby AC-3** | `audio/ac3` | MediaCodec (if licensed) | FFmpeg 7.1 | 5.1 Surround | 32 kHz – 48 kHz | 16-bit |
| **Dolby E-AC-3** | `audio/eac3` | MediaCodec (if licensed) | FFmpeg 7.1 | 5.1, 7.1 Surround | 32 kHz – 48 kHz | 16-bit |
| **Dolby TrueHD** | `audio/true-hd` | None | FFmpeg 7.1 | Up to 7.1 Surround | 44.1 kHz – 96 kHz | 16-bit, 24-bit |
| **DTS** | `audio/vnd.dts` | Platform dependent | FFmpeg 7.1 | 5.1 Surround | 44.1 kHz – 48 kHz | 16-bit |
| **DTS-HD** | `audio/vnd.dts.hd` | None | FFmpeg 7.1 | Up to 7.1 Surround | 44.1 kHz – 192 kHz | 16-bit, 24-bit |
| **Linear PCM** | `audio/raw` | AudioTrack Direct | FFmpeg 7.1 | Mono to 7.1 | 8 kHz – 192 kHz | 16-bit, 24-bit, 32-bit Float |

---

## 3. Decoder Preference Modes

NosvedMedia allows independent decoder preferences for video and audio pipelines:

1. **`AUTO` (Default):**
   - Stream attributes (resolution, bit depth, codec profile) and device hardware capabilities are dynamically evaluated.
   - Hardware decoders are prioritized when power-efficient and supported.
   - dav1d is automatically prioritized for AV1 on devices lacking hardware AV1 decoders.
2. **`PLATFORM_PREFERRED`:**
   - Platform MediaCodec decoders are prioritized.
   - Custom software decoders act as immediate fallback if hardware decoder initialization or playback fails.
3. **`SOFTWARE_PREFERRED`:**
   - Custom software decoders (dav1d for AV1, FFmpeg for other formats) are prioritized.
   - Platform decoders act as fallback if software constraints are exceeded.
4. **`PLATFORM_ONLY`:**
   - Exclusively utilizes Android platform MediaCodec decoders. Custom software decoders are never registered.
5. **`SOFTWARE_ONLY`:**
   - Exclusively utilizes custom software decoders. Platform MediaCodec decoders are never registered.

---

## 4. Per-Track Fallback & Recovery Rules

When an error occurs during playback, the `DecoderRecoveryManager` executes isolated per-track recovery:

- **Video SW Failure (dav1d / FFmpeg) -> Platform Video (MediaCodec):**
  - Triggers if software decoding fails or cannot keep up with high-resolution frames.
  - Audio playback continues uninterrupted.
- **Video HW Failure (MediaCodec) -> Software Video (dav1d / FFmpeg):**
  - Triggers if device MediaCodec fails to initialize, encounters an unsupported profile, or runs out of secure buffers.
  - Audio playback continues uninterrupted.
- **Audio SW Failure (FFmpeg) -> Platform Audio (MediaCodec):**
  - Triggers if software audio pipeline fails.
  - Video rendering continues uninterrupted.
- **Audio HW Failure (MediaCodec) -> Software Audio (FFmpeg):**
  - Triggers on DTS/AC3/TrueHD streams where device lacks platform audio decoders.
  - Video rendering continues uninterrupted.

---

## 5. Known Limitations & Boundary Rules

1. **DRM-Protected Content:**
   - Widevine DRM protected streams cannot be processed by software decoders (FFmpeg/dav1d) due to hardware secure memory constraints. DRM formats are routed exclusively to secure hardware decoders.
2. **8K AV1 Software Decoding:**
   - 8K AV1 software decoding is deliberately capped at 4K UHD (3840x2160) to avoid excessive mobile CPU utilization and thermal throttling.
3. **Dolby Vision Profile 7 / 8:**
   - Dolby Vision metadata passthrough requires platform hardware decoder and display pipeline support. FFmpeg software path falls back to HDR10 or SDR base layer tone-mapping.

