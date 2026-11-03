# Building NosvedMedia

This document provides step-by-step instructions for building the NosvedMedia Android media library and its native dependencies reproducibly from source.

## Prerequisites

- **Host OS:** Windows 10/11, Linux, or macOS
- **JDK:** Java 17 or Java 21
- **Android SDK:** Compile SDK 35, Build Tools 35.0.0
- **Android NDK:** Version 27.0.12077973 (r27) or compatible
- **CMake:** Version 3.22.1+
- **Native Tools for Codecs:**
  - `bash`, `sed`, `make` (via MSYS2 or Git Bash on Windows)
  - `meson` and `ninja` (for dav1d compilation)
  - `nasm` 2.14+ (for assembly optimizations)

## Building Native Codecs

### 1. FFmpeg 7.1 (LGPL v2.1+)

FFmpeg is built using the provided configuration script located in `native-codecs/src/main/cpp/third_party`:

```bash
cd native-codecs/src/main/cpp/third_party
./build_ffmpeg.sh
```

Key configuration flags:
- Target ABI: `arm64-v8a` (`aarch64-linux-android26`)
- Shared libraries: `--enable-shared --disable-static`
- Licensing compliance: strictly LGPL (no `--enable-gpl`, no `--enable-nonfree`)
- Output binaries copied to `native-codecs/src/main/jniLibs/arm64-v8a/`:
  - `libavcodec.so`
  - `libavformat.so`
  - `libavutil.so`
  - `libswscale.so`
  - `libswresample.so`

### 2. dav1d 1.5.0 (BSD 2-Clause)

dav1d is built using Meson and Ninja with NDK LLVM Clang:

```bash
cd native-codecs/src/main/cpp/third_party/dav1d-1.5.0
meson setup build-android \
    --cross-file cross_android_arm64.ini \
    --default-library shared \
    --buildtype release \
    -Denable_tools=false \
    -Denable_examples=false \
    -Denable_tests=false
ninja -C build-android install
```

Output binary:
- `libdav1d.so` copied to `native-codecs/src/main/jniLibs/arm64-v8a/`.

## Building the Android AARs

### 1. Build Release AAR Artifacts

Run the Gradle release bundling tasks:

```bash
./gradlew :native-codecs:bundleReleaseAar :nosved-media3:bundleReleaseAar --no-daemon
```

Generated AAR artifacts:
- `native-codecs/build/outputs/aar/native-codecs-release.aar`
- `nosved-media3/build/outputs/aar/nosved-media3-release.aar`

### 2. Publish to Maven Local

To publish artifacts for consumption by local apps (such as Nosved Player Lite):

```bash
./gradlew publishToMavenLocal --no-daemon
```

This installs:
- `com.devson.nosvedmedia:native-codecs:1.0.0`
- `com.devson.nosvedmedia:nosved-media3:1.0.0`
into `$HOME/.m2/repository/com/devson/nosvedmedia/`.

## Building the Sample Application

To assemble the sample debug APK:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Output:
- `app/build/outputs/apk/debug/app-debug.apk`

