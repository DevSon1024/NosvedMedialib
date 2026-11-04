#!/usr/bin/env bash
set -e
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="C:/Android/NosvedMedia"
THIRD_PARTY_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party"
DAV1D_VERSION="1.5.0"
DAV1D_SRC="${THIRD_PARTY_DIR}/dav1d-${DAV1D_VERSION}"
BUILD_DIR="${THIRD_PARTY_DIR}/dav1d_build/x86_64"
INSTALL_DIR="${THIRD_PARTY_DIR}/dav1d_install/x86_64"
JNILIBS_DIR="${PROJECT_ROOT}/native-codecs/src/main/jniLibs/x86_64"

NDK="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/windows-x86_64"
NINJA="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe"
API=26

echo "=== Building dav1d ${DAV1D_VERSION} for Android x86_64 (API ${API}) ==="

mkdir -p "${BUILD_DIR}"
mkdir -p "${INSTALL_DIR}"
mkdir -p "${JNILIBS_DIR}"

CROSS_FILE="${BUILD_DIR}/x86_64-android.ini"
cat <<EOF > "${CROSS_FILE}"
[binaries]
c = ['${TOOLCHAIN}/bin/clang.exe', '--target=x86_64-linux-android${API}']
cpp = ['${TOOLCHAIN}/bin/clang++.exe', '--target=x86_64-linux-android${API}']
ar = '${TOOLCHAIN}/bin/llvm-ar.exe'
strip = '${TOOLCHAIN}/bin/llvm-strip.exe'

[host_machine]
system = 'android'
cpu_family = 'x86_64'
cpu = 'x86_64'
endian = 'little'
EOF

echo "=== Configuring dav1d with Meson ==="
cd "${BUILD_DIR}"
python -m mesonbuild.mesonmain setup "${BUILD_DIR}" "${DAV1D_SRC}" \
    --cross-file "${CROSS_FILE}" \
    --prefix "/dav1d" \
    --libdir "lib" \
    --default-library shared \
    --buildtype release \
    -Denable_asm=false \
    -Denable_tools=false \
    -Denable_examples=false \
    -Denable_tests=false \
    --reconfigure

echo "=== Compiling dav1d with Ninja ==="
"${NINJA}" -C "${BUILD_DIR}"

echo "=== Copying artifacts to project ==="
cp -f "${BUILD_DIR}/src/libdav1d.so" "${JNILIBS_DIR}/libdav1d.so"

echo "=== dav1d x86_64 build complete ==="

