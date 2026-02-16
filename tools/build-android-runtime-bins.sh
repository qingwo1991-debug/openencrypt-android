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
require_cmd cargo
require_cmd rustup

mkdir -p "$OUT_ROOT/arm64-v8a" "$OUT_ROOT/armeabi-v7a"

echo "[1/4] build openlist-runtime (Go, arm64-v8a)"
(
  cd "$ROOT/core-openlist-go"
  CC="$TOOLCHAIN/aarch64-linux-android21-clang" \
  CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
    go build -trimpath -ldflags="-s -w" -o "$OUT_ROOT/arm64-v8a/openlist-runtime" ./cmd/openlist-runtime
)

echo "[2/4] build openlist-runtime (Go, armeabi-v7a)"
(
  cd "$ROOT/core-openlist-go"
  CC="$TOOLCHAIN/armv7a-linux-androideabi21-clang" \
  CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 \
    go build -trimpath -ldflags="-s -w" -o "$OUT_ROOT/armeabi-v7a/openlist-runtime" ./cmd/openlist-runtime
)

echo "[3/4] build openencrypt-gateway (Rust, arm64-v8a)"
rustup target add aarch64-linux-android >/dev/null
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/aarch64-linux-android21-clang" \
CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android21-clang" \
AR_aarch64_linux_android="$TOOLCHAIN/llvm-ar" \
  cargo build --manifest-path "$ROOT/core-encrypt-rs/Cargo.toml" --bin openencrypt-gateway --release --target aarch64-linux-android
cp "$ROOT/core-encrypt-rs/target/aarch64-linux-android/release/openencrypt-gateway" "$OUT_ROOT/arm64-v8a/openencrypt-gateway"

echo "[4/4] build openencrypt-gateway (Rust, armeabi-v7a)"
rustup target add armv7-linux-androideabi >/dev/null
CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$TOOLCHAIN/armv7a-linux-androideabi21-clang" \
CC_armv7_linux_androideabi="$TOOLCHAIN/armv7a-linux-androideabi21-clang" \
AR_armv7_linux_androideabi="$TOOLCHAIN/llvm-ar" \
  cargo build --manifest-path "$ROOT/core-encrypt-rs/Cargo.toml" --bin openencrypt-gateway --release --target armv7-linux-androideabi
cp "$ROOT/core-encrypt-rs/target/armv7-linux-androideabi/release/openencrypt-gateway" "$OUT_ROOT/armeabi-v7a/openencrypt-gateway"

chmod +x "$OUT_ROOT/arm64-v8a/openlist-runtime" \
         "$OUT_ROOT/arm64-v8a/openencrypt-gateway" \
         "$OUT_ROOT/armeabi-v7a/openlist-runtime" \
         "$OUT_ROOT/armeabi-v7a/openencrypt-gateway"

echo "Android runtime binaries ready at: $OUT_ROOT"
find "$OUT_ROOT" -maxdepth 2 -type f -printf "%P\n" | sort
