#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 1 ]]; then
  echo "usage: $0 <apk> [apk...]"
  exit 1
fi

BINARIES=("openlist-runtime" "openencrypt-gateway")

for apk in "$@"; do
  if [[ ! -f "$apk" ]]; then
    echo "apk not found: $apk"
    exit 1
  fi
  echo "Checking APK assets: $apk"
  listing="$(unzip -l "$apk")"
  abi_dirs="$(awk '{print $4}' <<<"$listing" | sed -n 's#^assets/bin/\([^/]*\)/.*#\1#p' | sort -u)"
  if [[ -z "$abi_dirs" ]]; then
    echo "missing runtime asset ABI directory in $apk"
    exit 1
  fi
  for abi in $abi_dirs; do
    for bin in "${BINARIES[@]}"; do
      pattern="assets/bin/$abi/$bin"
      if ! grep -q "$pattern" <<<"$listing"; then
        echo "missing asset in $apk: $pattern"
        exit 1
      fi
    done
  done
done

echo "APK runtime assets check passed."
