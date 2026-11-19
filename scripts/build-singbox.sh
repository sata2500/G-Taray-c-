#!/usr/bin/env bash
# sing-box VPN çekirdeğini Android için derler ve vpn-core/src/main/jniLibs altına koyar.
# Gereksinim: Go (1.25+). Kaynak kod Go modül vekil sunucusundan indirilir.
set -euo pipefail
VERSION="${SING_BOX_VERSION:-v1.14.4}"
TAGS="with_quic,with_wireguard,with_utls,with_gvisor"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cd "$WORK"
go mod init singbox-fetch >/dev/null 2>&1
SRC="$(go mod download -json "github.com/sagernet/sing-box@${VERSION}" | sed -n 's/.*"Dir": "\(.*\)".*/\1/p')"
cp -r "$SRC" "$WORK/src" && chmod -R u+w "$WORK/src"
cd "$WORK/src"

build() { # goos goarch abi goarm
  mkdir -p "$ROOT/vpn-core/src/main/jniLibs/$3"
  CGO_ENABLED=0 GOOS="$1" GOARCH="$2" GOARM="${4:-}" go build -trimpath -tags "$TAGS" \
    -ldflags "-s -w -buildid= -checklinkname=0" \
    -o "$ROOT/vpn-core/src/main/jniLibs/$3/libsingbox.so" ./cmd/sing-box
  echo "✓ $3"
}
# arm64: Android hedefi (Android 11+ ağ kısıtlamalarına uyumlu yollar etkin olur).
build android arm64 arm64-v8a
# 32 bit ARM ve x86_64 için Android hedefi NDK gerektirir; statik Linux ikilisi kullanılır.
build linux arm armeabi-v7a 7
build linux amd64 x86_64
