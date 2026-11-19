#!/usr/bin/env bash
# Release APK'larını kalıcı anahtar ve anahtar döndürme zinciriyle (signing/lineage.bin) imzalar.
# Yalnızca APK İmza Şeması v3 kullanılır (Android 9+). Çıktılar: <klasör>/G-Tarayici-<sürüm>-<mimari>.apk
#
# Kullanım: GT_KEYSTORE_FILE=... GT_KEYSTORE_PASSWORD=... scripts/sign-release.sh [çıktı klasörü]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${GT_KEYSTORE_FILE:?GT_KEYSTORE_FILE ayarlanmalı}"
: "${GT_KEYSTORE_PASSWORD:?GT_KEYSTORE_PASSWORD ayarlanmalı}"
ALIAS="${GT_KEY_ALIAS:-gtarayici}"
OUT="${1:-$ROOT/dist}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?ANDROID_HOME ayarlanmalı}}"
APKSIGNER="$SDK/build-tools/${BUILD_TOOLS:-37.0.0}/apksigner"
VERSION="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$ROOT/app/build.gradle.kts")"

mkdir -p "$OUT"
shopt -s nullglob
apks=("$ROOT"/app/build/outputs/apk/release/app-*-release.apk)
[ ${#apks[@]} -gt 0 ] || { echo "Önce ./gradlew :app:assembleRelease :vpn-core:assembleRelease çalıştırın" >&2; exit 1; }
for apk in "${apks[@]}"; do
  abi="$(basename "$apk" | sed 's/^app-\(.*\)-release\.apk$/\1/')"
  out="$OUT/G-Tarayici-$VERSION-$abi.apk"
  "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass env:GT_KEYSTORE_PASSWORD --ks-key-alias "$ALIAS" \
    --lineage "$ROOT/signing/lineage.bin" \
    --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true \
    --rotation-min-sdk-version 28 --min-sdk-version 28 \
    --out "$out" "$apk"
  "$APKSIGNER" verify --min-sdk-version 28 "$out"
  rm -f "$out.idsig"
  echo "✓ $out"
done

# VPN bileşeni (ayrı, kodsuz paket): kalıcı anahtarla, döndürme zinciri olmadan imzalanır.
for apk in "$ROOT"/vpn-core/build/outputs/apk/release/vpn-core-*-release.apk; do
  abi="$(basename "$apk" | sed 's/^vpn-core-\(.*\)-release\.apk$/\1/')"
  out="$OUT/G-Tarayici-VPN-$abi-core.apk"
  "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass env:GT_KEYSTORE_PASSWORD --ks-key-alias "$ALIAS" \
    --v1-signing-enabled false --min-sdk-version 28 --out "$out" "$apk"
  "$APKSIGNER" verify --min-sdk-version 28 "$out"
  rm -f "$out.idsig"
  echo "✓ $out"
done
