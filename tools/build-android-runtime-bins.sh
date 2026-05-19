#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_ROOT="${RUNTIME_BIN_ROOT:-$ROOT/.tmp/android-runtime-bins}"
NDK_ROOT="${ANDROID_NDK_ROOT:-}"

if [[ -z "$NDK_ROOT" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  NDK_ROOT="$(find "$ANDROID_SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n1 || true)"
fi

if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT" ]]; then
  echo "ANDROID_NDK_ROOT is required. Current: '$NDK_ROOT'"
  exit 1
fi

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [[ ! -d "$TOOLCHAIN" ]]; then
  echo "missing Android NDK LLVM toolchain: $TOOLCHAIN"
  exit 1
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing command: $1"
    exit 1
  }
}

require_cmd go

mkdir -p "$OUT_ROOT/arm64-v8a" "$OUT_ROOT/armeabi-v7a"

echo "[1/2] build openlist-runtime (Go, arm64-v8a)"
(
  cd "$ROOT/core-openlist-go"
  CC="$TOOLCHAIN/aarch64-linux-android21-clang" \
  CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
    go build -trimpath -ldflags="-s -w" -o "$OUT_ROOT/arm64-v8a/openlist-runtime" ./cmd/openlist-runtime
)

echo "[2/2] build openlist-runtime (Go, armeabi-v7a)"
(
  cd "$ROOT/core-openlist-go"
  CC="$TOOLCHAIN/armv7a-linux-androideabi21-clang" \
  CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 \
    go build -trimpath -ldflags="-s -w" -o "$OUT_ROOT/armeabi-v7a/openlist-runtime" ./cmd/openlist-runtime
)

chmod +x "$OUT_ROOT/arm64-v8a/openlist-runtime" \
         "$OUT_ROOT/armeabi-v7a/openlist-runtime"

echo "Android runtime binaries ready at: $OUT_ROOT"
find "$OUT_ROOT" -maxdepth 2 -type f -printf "%P\n" | sort
