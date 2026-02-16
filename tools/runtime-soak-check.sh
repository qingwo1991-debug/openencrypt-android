#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DURATION_MINUTES="${1:-10}"
INTERVAL_SECONDS="${2:-15}"
TMP="$(mktemp -d)"
RUST_DB="$TMP/openencrypt.sqlite3"
RUST_LOG="$TMP/rust.log"
GO_LOG="$TMP/go.log"
SOAK_LOG="$TMP/soak.log"
BASE_PORT=$(( 20000 + (RANDOM % 20000) ))
GO_PORT="$BASE_PORT"
RUST_PORT="$((BASE_PORT + 1))"

export NO_PROXY="127.0.0.1,localhost"
export no_proxy="$NO_PROXY"

curl_local() {
  curl --noproxy '*' "$@"
}

cleanup() {
  if [[ -n "${GO_PID:-}" ]] && kill -0 "$GO_PID" 2>/dev/null; then
    kill "$GO_PID" || true
    wait "$GO_PID" || true
  fi
  if [[ -n "${RUST_PID:-}" ]] && kill -0 "$RUST_PID" 2>/dev/null; then
    kill "$RUST_PID" || true
    wait "$RUST_PID" || true
  fi
  echo "runtime-soak-check logs:"
  echo "- rust: $RUST_LOG"
  echo "- go: $GO_LOG"
  echo "- soak: $SOAK_LOG"
}
trap cleanup EXIT

(
  cd "$ROOT/core-encrypt-rs"
  LISTEN_ADDR="127.0.0.1:${RUST_PORT}" SQLITE_PATH="$RUST_DB" AUTO_MIGRATE=true cargo run >"$RUST_LOG" 2>&1
) &
RUST_PID=$!

for _ in $(seq 1 80); do
  if curl_local -fsS "http://127.0.0.1:${RUST_PORT}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

(
  cd "$ROOT/core-openlist-go"
  LISTEN_ADDR="127.0.0.1:${GO_PORT}" GATEWAY_BASE_URL="http://127.0.0.1:${RUST_PORT}" go run ./cmd/openlist-runtime >"$GO_LOG" 2>&1
) &
GO_PID=$!

for _ in $(seq 1 80); do
  if curl_local -fsS "http://127.0.0.1:${GO_PORT}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

end_ts=$(( $(date +%s) + DURATION_MINUTES*60 ))
while (( $(date +%s) < end_ts )); do
  ts="$(date -Iseconds)"
  go_status="$(curl_local -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${GO_PORT}/healthz" || true)"
  rs_status="$(curl_local -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${RUST_PORT}/healthz" || true)"
  echo "$ts go=$go_status rust=$rs_status" | tee -a "$SOAK_LOG"
  sleep "$INTERVAL_SECONDS"
done

bash "$ROOT/tools/soak-report.sh" "$SOAK_LOG"
echo "runtime-soak-check passed"
