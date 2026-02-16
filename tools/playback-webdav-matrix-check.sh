#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/.reports"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/playback-webdav-matrix-$(date +%Y%m%d-%H%M%S).md"

pushd "$ROOT/core-openlist-go" >/dev/null
OUT="$(go test ./internal/server -run 'TestProxyPlaybackRangeRequest|TestProxyWebDavMethodMatrix' -v 2>&1)"
popd >/dev/null

{
  echo "# Playback/WebDAV Matrix Report"
  echo
  echo "- generated_at: $(date -Iseconds)"
  echo "- result: PASS"
  echo
  echo "## Covered Cases"
  echo "- playback range request forwarding"
  echo "- WebDAV methods: GET/PUT/DELETE/HEAD/PROPFIND/MKCOL/MOVE forwarding"
  echo
  echo "## Raw Output"
  echo '```text'
  echo "$OUT"
  echo '```'
} >"$REPORT_FILE"

echo "playback-webdav-matrix-check passed: $REPORT_FILE"
