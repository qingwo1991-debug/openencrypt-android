#!/usr/bin/env bash
set -euo pipefail

BASE_FILE="${1:-tools/version-base.env}"
if [[ ! -f "$BASE_FILE" ]]; then
  echo "missing base file: $BASE_FILE" >&2
  exit 1
fi

source "$BASE_FILE"

: "${BASE_MAJOR:?BASE_MAJOR is required}"
: "${BASE_MINOR:?BASE_MINOR is required}"
: "${BASE_PATCH_START:=0}"
: "${START_VERSION_CODE:=1}"

prefix="v${BASE_MAJOR}.${BASE_MINOR}."
max_patch=-1

while IFS= read -r tag; do
  patch="${tag#"$prefix"}"
  if [[ "$patch" =~ ^[0-9]+$ ]] && (( patch > max_patch )); then
    max_patch="$patch"
  fi
done < <(git tag -l "${prefix}*" | sort -V)

if (( max_patch < BASE_PATCH_START )); then
  max_patch="$BASE_PATCH_START"
fi

next_patch=$((max_patch + 1))
next_tag="${prefix}${next_patch}"
next_version_code=$((START_VERSION_CODE + next_patch))

echo "NEXT_TAG=$next_tag"
echo "NEXT_VERSION_NAME=$next_tag"
echo "NEXT_PATCH=$next_patch"
echo "NEXT_VERSION_CODE=$next_version_code"
