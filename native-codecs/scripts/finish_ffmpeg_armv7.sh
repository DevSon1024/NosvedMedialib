#!/usr/bin/env bash
set -e

PROJECT_ROOT="C:/Android/NosvedMedia"
SRC_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party/ffmpeg-7.1"
OUTPUT_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party/ffmpeg_install/armeabi-v7a"
JNILIBS_DIR="${PROJECT_ROOT}/native-codecs/src/main/jniLibs/armeabi-v7a"
NDK="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/windows-x86_64"

export PATH="${TOOLCHAIN}/bin:${NDK}/prebuilt/windows-x86_64/bin:/c/Program Files/Git/bin:/c/Program Files/Git/usr/bin:${PATH}"
MAKE_BIN="/c/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358/prebuilt/windows-x86_64/bin/make.exe"

cd "${SRC_DIR}"
echo "=== Building libavformat.so ==="
"${MAKE_BIN}" libavformat/libavformat.so

echo "=== Installing FFmpeg armeabi-v7a ==="
"${MAKE_BIN}" install

mkdir -p "${JNILIBS_DIR}"
cp -f "${OUTPUT_DIR}/lib/"*.so "${JNILIBS_DIR}/"

echo "=== FFmpeg armeabi-v7a Complete! ==="

