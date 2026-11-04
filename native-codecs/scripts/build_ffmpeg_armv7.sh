#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="C:/Android/NosvedMedia"
SRC_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party/ffmpeg-7.1"
OUTPUT_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party/ffmpeg_install/armeabi-v7a"
JNILIBS_DIR="${PROJECT_ROOT}/native-codecs/src/main/jniLibs/armeabi-v7a"
NDK="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/windows-x86_64"

API=26
ARCH=arm
CPU=armv7-a
TARGET=armv7a-linux-androideabi${API}

export PATH="${TOOLCHAIN}/bin:${NDK}/prebuilt/windows-x86_64/bin:/c/Program Files/Git/bin:/c/Program Files/Git/usr/bin:${PATH}"
MAKE_BIN="/c/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358/prebuilt/windows-x86_64/bin/make.exe"

echo "=== Building FFmpeg for Android armeabi-v7a (API ${API}) ==="

mkdir -p "${OUTPUT_DIR}"
mkdir -p "${JNILIBS_DIR}"
cd "${SRC_DIR}"

"${MAKE_BIN}" distclean || true

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
    --extra-cflags="--target=${TARGET} -march=armv7-a -mfloat-abi=softfp -mfpu=neon -O3 -fPIC -DANDROID" \
    --extra-ldflags="--target=${TARGET} -Wl,--fix-cortex-a8 -Wl,-Bsymbolic" \
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
    --enable-neon \
    --disable-vulkan \
    --disable-symver

echo "=== Configuration Complete ==="

echo "=== Compiling FFmpeg with make ==="
"${MAKE_BIN}" -j4 libavutil/libavutil.so libswresample/libswresample.so libswscale/libswscale.so
"${MAKE_BIN}" libavcodec/libavcodec.so
"${MAKE_BIN}" libavformat/libavformat.so
"${MAKE_BIN}" install

cp -f "${OUTPUT_DIR}/lib/"*.so "${JNILIBS_DIR}/"

echo "=== FFmpeg armeabi-v7a Build & Install Successful ==="

