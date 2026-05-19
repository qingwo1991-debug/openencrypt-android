#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ABIS=("arm64-v8a" "armeabi-v7a")
BINARIES=("openlist-runtime")
RUNTIME_BIN_ROOT="${RUNTIME_BIN_ROOT:-}"

pick_existing() {
  for candidate in "$@"; do
    if [[ -f "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

echo "Checking Android runtime binaries..."
if [[ -n "$RUNTIME_BIN_ROOT" ]]; then
  echo "- RUNTIME_BIN_ROOT=$RUNTIME_BIN_ROOT"
else
  echo "- RUNTIME_BIN_ROOT is not set"
fi

missing=0
for abi in "${ABIS[@]}"; do
  echo "ABI: $abi"
  for name in "${BINARIES[@]}"; do
    candidates=()
    candidates+=("$ROOT/app-android/src/main/assets/bin/$abi/$name")
    if [[ -n "$RUNTIME_BIN_ROOT" ]]; then
      candidates+=("$RUNTIME_BIN_ROOT/$abi/$name")
    fi
    candidates+=("$ROOT/core-openlist-go/target/android/$abi/$name")

    if found="$(pick_existing "${candidates[@]}")"; then
      echo "  - $name: OK ($found)"
    else
      echo "  - $name: MISSING"
      missing=1
    fi
  done
done

if [[ "$missing" -ne 0 ]]; then
  cat <<'EOF'
Android runtime binaries are missing.
Provide binaries in one of these layouts before Android build:
1) app-android/src/main/assets/bin/<abi>/<binary>
2) RUNTIME_BIN_ROOT/<abi>/<binary>
3) core-openlist-go/target/android/<abi>/openlist-runtime
EOF
  exit 1
fi

echo "Android runtime binaries check passed."
