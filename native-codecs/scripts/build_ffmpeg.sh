#!/usr/bin/env bash
set -e

# Use Windows-friendly paths with forward slashes
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="C:/Android/NosvedMedia"
SRC_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party/ffmpeg-7.1"
OUTPUT_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party/ffmpeg_install/arm64-v8a"
NDK="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/windows-x86_64"

API=26
ARCH=aarch64
CPU=armv8-a

# Put Git bin (for sh, cp, rm, sed) and NDK toolchain in PATH
export PATH="${TOOLCHAIN}/bin:${NDK}/prebuilt/windows-x86_64/bin:/c/Program Files/Git/bin:/c/Program Files/Git/usr/bin:${PATH}"
MAKE_BIN="/c/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358/prebuilt/windows-x86_64/bin/make.exe"

echo "=== Building FFmpeg for Android arm64-v8a (API ${API}) ==="
echo "NDK: ${NDK}"
echo "Source: ${SRC_DIR}"
echo "Output: ${OUTPUT_DIR}"

mkdir -p "${OUTPUT_DIR}"
cd "${SRC_DIR}"

# Run configure directly inside SRC_DIR so SRC_PATH is relative '.'
./configure \
    --prefix="${OUTPUT_DIR}" \
    --enable-cross-compile \
    --target-os=android \
    --arch="${ARCH}" \
    --cpu="${CPU}" \
    --host-cc="${TOOLCHAIN}/bin/clang.exe" \
    --host-cflags="--target=x86_64-linux-android${API}" \
    --cc="${TOOLCHAIN}/bin/clang.exe" \
    --cxx="${TOOLCHAIN}/bin/clang++.exe" \
    --ar="${TOOLCHAIN}/bin/llvm-ar.exe" \
    --as="${TOOLCHAIN}/bin/clang.exe" \
    --nm="${TOOLCHAIN}/bin/llvm-nm.exe" \
    --ranlib="${TOOLCHAIN}/bin/llvm-ranlib.exe" \
    --strip="${TOOLCHAIN}/bin/llvm-strip.exe" \
    --extra-cflags="--target=aarch64-linux-android${API} -O3 -fPIC -DANDROID" \
    --extra-ldflags="--target=aarch64-linux-android${API} -Wl,-Bsymbolic" \
    --enable-shared \
    --disable-static \
    --disable-programs \
    --disable-doc \
    --disable-avdevice \
    --disable-postproc \
    --disable-avfilter \
    --disable-encoders \
    --disable-muxers \
    --disable-filters \
    --disable-devices \
    --enable-pic \
    --enable-jni \
    --enable-mediacodec \
    --enable-pthreads \
    --disable-vulkan \
    --disable-symver

echo "=== Configuration Complete ==="

echo "=== Compiling FFmpeg with make ==="
# Build libraries sequentially to avoid Windows Win32 command-line buffer overflow
"${MAKE_BIN}" -j4 libavutil/libavutil.so libswresample/libswresample.so libswscale/libswscale.so
"${MAKE_BIN}" libavcodec/libavcodec.so
"${MAKE_BIN}" libavformat/libavformat.so
"${MAKE_BIN}" install

echo "=== FFmpeg arm64-v8a Build & Install Successful ==="
