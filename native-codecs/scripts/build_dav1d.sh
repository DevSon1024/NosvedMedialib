#!/usr/bin/env bash
set -e
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="C:/Android/NosvedMedia"
THIRD_PARTY_DIR="${PROJECT_ROOT}/native-codecs/src/main/cpp/third_party"
DAV1D_VERSION="1.5.0"
DAV1D_TAR="${THIRD_PARTY_DIR}/dav1d-${DAV1D_VERSION}.tar.xz"
DAV1D_SRC="${THIRD_PARTY_DIR}/dav1d-${DAV1D_VERSION}"
BUILD_DIR="${THIRD_PARTY_DIR}/dav1d_build/arm64-v8a"
INSTALL_DIR="${THIRD_PARTY_DIR}/dav1d_install/arm64-v8a"
JNILIBS_DIR="${PROJECT_ROOT}/native-codecs/src/main/jniLibs/arm64-v8a"
PUBLIC_INCLUDE_DIR="${THIRD_PARTY_DIR}/dav1d/include"

NDK="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/ndk/28.2.13676358"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/windows-x86_64"
NINJA="C:/Users/DEVENDRA/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe"
API=26

echo "=== Building dav1d ${DAV1D_VERSION} for Android arm64-v8a (API ${API}) ==="

mkdir -p "${THIRD_PARTY_DIR}"
cd "${THIRD_PARTY_DIR}"

if [ ! -f "${DAV1D_TAR}" ]; then
    echo "Downloading dav1d ${DAV1D_VERSION}..."
    curl.exe -L -o "${DAV1D_TAR}" "https://downloads.videolan.org/pub/videolan/dav1d/${DAV1D_VERSION}/dav1d-${DAV1D_VERSION}.tar.xz"
fi

if [ ! -d "${DAV1D_SRC}" ]; then
    echo "Extracting dav1d..."
    tar --force-local -xf "${DAV1D_TAR}"
fi

mkdir -p "${BUILD_DIR}"
mkdir -p "${INSTALL_DIR}"
mkdir -p "${JNILIBS_DIR}"
mkdir -p "${PUBLIC_INCLUDE_DIR}"

CROSS_FILE="${BUILD_DIR}/arm64-android.ini"
cat <<EOF > "${CROSS_FILE}"
[binaries]
c = ['${TOOLCHAIN}/bin/clang.exe', '--target=aarch64-linux-android${API}']
cpp = ['${TOOLCHAIN}/bin/clang++.exe', '--target=aarch64-linux-android${API}']
ar = '${TOOLCHAIN}/bin/llvm-ar.exe'
strip = '${TOOLCHAIN}/bin/llvm-strip.exe'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
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
    -Denable_tools=false \
    -Denable_examples=false \
    -Denable_tests=false \
    --reconfigure

echo "=== Compiling dav1d with Ninja ==="
"${NINJA}" -C "${BUILD_DIR}"

echo "=== Copying artifacts to project ==="
cp -f "${BUILD_DIR}/src/libdav1d.so" "${JNILIBS_DIR}/libdav1d.so"
cp -rf "${DAV1D_SRC}/include/dav1d" "${PUBLIC_INCLUDE_DIR}/"

echo "=== dav1d arm64-v8a build complete ==="
