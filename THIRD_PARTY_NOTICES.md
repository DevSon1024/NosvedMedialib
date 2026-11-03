# Third-Party Notices and Licenses

This project incorporates third-party open-source software components under their respective licenses.

---

## 1. FFmpeg

- **Component:** FFmpeg
- **Version:** 7.1 ("Péter")
- **Upstream Website:** [https://ffmpeg.org](https://ffmpeg.org)
- **Source Archive:** [https://ffmpeg.org/releases/ffmpeg-7.1.tar.xz](https://ffmpeg.org/releases/ffmpeg-7.1.tar.xz)
- **Upstream Git:** `https://git.ffmpeg.org/ffmpeg.git`
- **License:** GNU Lesser General Public License (LGPL), version 2.1 or later (LGPL v2.1+)

### Audited Build Configuration

FFmpeg was built using the following reproducible configuration options:

```bash
./configure \
    --prefix="${OUTPUT_DIR}" \
    --enable-cross-compile \
    --target-os=android \
    --arch=aarch64 \
    --cpu=armv8-a \
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
```

### Licensing & Compliance Audit

1. **No GPL or Nonfree Components:**
   - `--enable-gpl` was NOT passed.
   - `--enable-version3` was NOT passed.
   - `--enable-nonfree` was NOT passed.
   - GPL-only components (e.g., `libpostproc`, GPL-licensed filters) are disabled.
   - Resulting configuration is strictly under **LGPL v2.1 or later**.

2. **External Dependencies Audit:**
   - `bionic` (Android C/C++ runtime): BSD / Apache 2.0.
   - `zlib`: zlib license (permissive).
   - `libmediandk` / `libandroid`: Apache 2.0 (Android platform).
   - No external GPL libraries (such as x264, x265, or xvid) are enabled or linked.

3. **LGPL Compliance & Shared Library Distribution Requirements:**
   - **Dynamic Linking:** FFmpeg is built and distributed exclusively as separate shared libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`) rather than statically linked into a single monolithic proprietary binary.
   - **Relinking / Replacement:** Applications distributing NosvedMedia as an AAR dynamically load these libraries via `System.loadLibrary()`, permitting end users to replace the FFmpeg shared libraries with modified versions conforming to Section 6(a) of the LGPL v2.1.
   - **Source Code Availability:** NosvedMedia provides reproducible build instructions and scripts ([`build_ffmpeg.sh`](file:///native-codecs/scripts/build_ffmpeg.sh)) along with source distribution references to obtain and rebuild the exact FFmpeg components utilized.

---

## 2. GNU Lesser General Public License (LGPL) Version 2.1 Text

```
                  GNU LESSER GENERAL PUBLIC LICENSE
                       Version 2.1, February 1999

 Copyright (C) 1991, 1999 Free Software Foundation, Inc.
 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.

[This is the first released version of the Lesser GPL.  It also counts
 as the successor of the GNU Library Public License, version 2, hence
 the version number 2.1.]

                            Preamble

  The licenses for most software are designed to take away your
freedom to share and change it.  By contrast, the GNU General Public
Licenses are intended to guarantee your freedom to share and change
free software--to make sure the software is free for all its users.

  This license, the Lesser General Public License, applies to some
specially designated software packages--typically libraries--of the
Free Software Foundation and other authors who decide to use it.  You
can use it too, but we suggest you first think carefully about whether
this license or the ordinary General Public License is the better
strategy to use in any particular case, based on the explanations below.

  When we speak of free software, we are referring to freedom of use,
not price.  Our General Public Licenses are designed to make sure that
you have the freedom to distribute copies of free software (and charge
for this service if you wish); that you receive source code or can get
it if you want it; that you can change the software and use pieces of it
in new free programs; and that you are informed that you can do these
things.

  To protect your rights, we need to make restrictions that forbid
distributors to deny you these rights or to ask you to surrender these
rights.  These restrictions translate to certain responsibilities for
you if you distribute copies of the library or if you modify it.

  For example, if you distribute copies of the library, whether gratis
or for a fee, you must give the recipients all the rights that we gave
you.  You must make sure that they, too, receive or can get the source
code.  If you link other code with the library, you must provide
complete object files to the recipients, so that they can relink them
with the library after making changes to the library and recompiling
it.  And you must show them these terms so they know their rights.

  We protect your rights with a two-step method: (1) we copyright the
library, and (2) we offer you this license, which gives you legal
permission to copy, distribute and/or modify the library.

  Also, for each distributor's protection, we want to make certain
that everyone understands that there is no warranty for this free
library.  If the library is modified by someone else and passed on, we
want its recipients to know that what they have is not the original
version, so that any problems introduced by others will not reflect on
the original authors' reputations.

  Finally, software patents pose a constant threat to the existence of
any free program.  We wish to make sure that a company cannot
effectively restrict the users of a free program by obtaining a
restrictive license from a patent holder.  Therefore, we insist that
any patent license obtained for a version of the library must be
consistent with the full freedom of use specified in this license.

  Most GNU software, including some libraries, is covered by the
ordinary GNU General Public License.  This license, the GNU Lesser
General Public License, applies to certain designated libraries, and
is quite different from the ordinary General Public License.  We use
this license for certain libraries in order to permit linking those
libraries into non-free programs.

  When a program is linked with a library, whether statically or using
a shared library, the combination of the two is legally speaking a
combined work, a derivative of the original library.  The ordinary
General Public License therefore permits such linking only if the
entire combination fits its criteria of freedom.  The Lesser General
Public License permits more lax criteria for linking other code with
the library.

  We call this license the "Lesser" General Public License because it
does Less to protect the user's freedom than the ordinary General
Public License.  It also provides other free software developers Less
of an advantage over competing non-free programs.  These disadvantages
are the reason we use the ordinary General Public License for many
libraries.  However, the Lesser license provides advantages in certain
special circumstances.

  For example, on rare occasions, there may be a special need to
encourage the widest possible use of a certain library, so that it becomes
a de-facto standard.  To achieve this, non-free programs must be
allowed to use the library.  A more frequent case is that a free
library does the same job as widely used non-free libraries.  In this
case, there is little to gain by limiting the free library to free
software only, so we use the Lesser General Public License.

  In other cases, permission to use a particular library in non-free
programs enables a greater number of people to use a large body of
free software.  For example, permission to use the GNU C Library in
non-free programs enables many more people to use the whole GNU
operating system, as well as its variant, the GNU/Linux operating
system.

  Although the Lesser General Public License is Less protective of the
users' freedom, it does ensure that the user of a program that is linked
with the Library has the freedom and the wherewithal to run that program
using a modified version of the Library.

  The precise terms and conditions for copying, distribution and
modification follow.  Pay close attention to the difference between a
"work based on the library" and a "work that uses the library".  The
former contains code derived from the library, whereas the latter must
be combined with the library in order to run.
```



---

## 3. dav1d

- **Component:** dav1d (AV1 Decoder)
- **Version:** 1.5.0
- **Upstream Website:** [https://code.videolan.org/videolan/dav1d](https://code.videolan.org/videolan/dav1d)
- **Source Archive:** [https://downloads.videolan.org/pub/videolan/dav1d/1.5.0/dav1d-1.5.0.tar.xz](https://downloads.videolan.org/pub/videolan/dav1d/1.5.0/dav1d-1.5.0.tar.xz)
- **Upstream Git:** `https://code.videolan.org/videolan/dav1d.git`
- **License:** BSD 2-Clause License ("Simplified BSD License")

### Audited Build Configuration

dav1d was built using Android NDK LLVM/Clang (API 26+) for `arm64-v8a` via Meson and Ninja:

```ini
[binaries]
c = ['${TOOLCHAIN}/bin/clang.exe', '--target=aarch64-linux-android26']
cpp = ['${TOOLCHAIN}/bin/clang++.exe', '--target=aarch64-linux-android26']
ar = '${TOOLCHAIN}/bin/llvm-ar.exe'
strip = '${TOOLCHAIN}/bin/llvm-strip.exe'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'
```

Options: `--default-library shared --buildtype release -Denable_tools=false -Denable_examples=false -Denable_tests=false`. Full ARM64 NEON assembly optimizations enabled.

### Licensing & Compliance Audit

1. **Permissive Licensing:** The BSD 2-Clause license is a permissive open-source license that permits redistribution and use in source and binary forms, with or without modification, for commercial and proprietary software.
2. **Distribution Requirements:** Requires preservation of the original copyright notice and disclaimer in binary distributions.
3. **Compatibility:** Fully compatible with NosvedMedia AAR distribution and LGPL v2.1+ requirements.

### BSD 2-Clause License Text

```
Copyright (c) 2018-2024, VideoLAN and dav1d authors
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
