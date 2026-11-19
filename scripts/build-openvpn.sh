#!/usr/bin/env bash
# OpenVPN 2.7 (Android modu) ve G-Tarayıcı OpenVPN köprüsünü derleyip vpn-core/src/main/jniLibs altına koyar.
#
# Gereksinimler: Android NDK (ANDROID_NDK_ROOT), Go 1.25+, curl, make.
# Kaynaklar Debian arşivinden indirilir: OpenVPN 2.7.8, OpenSSL 3.5 (LTS), LZO 2.10, LZ4 1.10.
#
# Çıktılar (her mimari için):
#   libopenvpn.so     — OpenVPN istemcisi (TARGET_ANDROID: tünel yönetim kanalından istenir)
#   libovpnbridge.so  — OpenVPN'i kullanıcı alanı ağ yığınına bağlayan ve SOCKS5 sunan köprü
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_ROOT:?ANDROID_NDK_ROOT ayarlanmalı}"
API=26
WORK="${OVPN_WORK:-$ROOT/build/openvpn-android}"
DEBIAN=https://deb.debian.org/debian/pool/main
OPENVPN=2.7.8
OPENSSL=3.5.9
LZO=2.10
LZ4=1.10.0

TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TC/bin:$PATH"
mkdir -p "$WORK/src"

fetch() { # dosya url
  [ -f "$WORK/src/$1" ] || curl -sSLo "$WORK/src/$1" "$2"
}
fetch openvpn.tgz "$DEBIAN/o/openvpn/openvpn_${OPENVPN}.orig.tar.gz"
fetch openssl.tgz "$DEBIAN/o/openssl/openssl_${OPENSSL}.orig.tar.gz"
fetch lzo.tgz "$DEBIAN/l/lzo2/lzo2_${LZO}.orig.tar.gz"
fetch lz4.tgz "$DEBIAN/l/lz4/lz4_${LZ4}.orig.tar.gz"

build_abi() { # abi clang-triple openssl-target goos goarch goarm
  local abi=$1 triple=$2 ossl_target=$3 goos=$4 goarch=$5 goarm=${6:-}
  local cc="${triple}${API}-clang" prefix="$WORK/$abi/prefix" b="$WORK/$abi/build"
  rm -rf "$b" && mkdir -p "$b" "$prefix"
  echo "== $abi"

  tar -xzf "$WORK/src/openssl.tgz" -C "$b"
  (cd "$b"/openssl-* && ./Configure "$ossl_target" no-shared no-tests no-docs no-apps \
     --prefix="$prefix" --libdir=lib >/dev/null && make -j"$(nproc)" build_libs >/dev/null && make install_dev >/dev/null)

  tar -xzf "$WORK/src/lzo.tgz" -C "$b"
  (cd "$b"/lzo-* && ./configure --host="$triple" CC="$cc" AR=llvm-ar RANLIB=llvm-ranlib --disable-shared \
     --prefix="$prefix" >/dev/null && make -j"$(nproc)" >/dev/null && make install >/dev/null)

  tar -xzf "$WORK/src/lz4.tgz" -C "$b"
  (cd "$b"/lz4-*/lib && make -j"$(nproc)" CC="$cc" AR=llvm-ar liblz4.a >/dev/null && \
     cp lz4.h lz4hc.h lz4frame.h "$prefix/include/" && cp liblz4.a "$prefix/lib/")

  tar -xzf "$WORK/src/openvpn.tgz" -C "$b"
  (cd "$b"/openvpn-* && PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig" ./configure --host="$triple" CC="$cc" \
     AR=llvm-ar RANLIB=llvm-ranlib --disable-plugins --disable-dco --disable-shared --enable-static \
     OPENSSL_CFLAGS="-I$prefix/include" OPENSSL_LIBS="-L$prefix/lib -lssl -lcrypto" \
     LZO_CFLAGS="-I$prefix/include" LZO_LIBS="-L$prefix/lib -llzo2" \
     LZ4_CFLAGS="-I$prefix/include" LZ4_LIBS="-L$prefix/lib -llz4" \
     LIBCAPNG_CFLAGS="-DGT_NO_LIBCAPNG" LIBCAPNG_LIBS="-lc" \
     CFLAGS="-O2 -fPIE" LDFLAGS="-pie" >/dev/null
   # Android modu: tünel, VpnService yerine yönetim kanalından (köprüden) istenir.
   sed -i 's/^#define TARGET_LINUX 1/#define TARGET_ANDROID 1/; s/^#define HAVE_LIBCAPNG 1/\/* #undef HAVE_LIBCAPNG *\//' config.h
   make -j"$(nproc)" >/dev/null)

  local out="$ROOT/vpn-core/src/main/jniLibs/$abi"
  mkdir -p "$out"
  llvm-strip -o "$out/libopenvpn.so" "$b"/openvpn-*/src/openvpn/openvpn
  (cd "$ROOT/native/ovpn-bridge" && CGO_ENABLED=0 GOOS="$goos" GOARCH="$goarch" GOARM="$goarm" \
     go build -trimpath -ldflags "-s -w -buildid=" -o "$out/libovpnbridge.so" .)
  echo "✓ $abi"
}

# Köprü: arm64'te Android hedefi; diğerlerinde (cgo gerektirdiği için) statik Linux ikilisi.
build_abi arm64-v8a aarch64-linux-android android-arm64 android arm64
build_abi armeabi-v7a armv7a-linux-androideabi android-arm linux arm 7
build_abi x86_64 x86_64-linux-android android-x86_64 linux amd64
