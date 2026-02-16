#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
RUST_DB="$TMP/openencrypt.sqlite3"
RUST_LOG="$TMP/rust.log"
GO_LOG="$TMP/go.log"
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
if ! curl_local -fsS "http://127.0.0.1:${RUST_PORT}/healthz" >/dev/null; then
  echo "rust service failed to start"
  cat "$RUST_LOG" || true
  exit 1
fi

(
  cd "$ROOT/core-openlist-go"
  LISTEN_ADDR="127.0.0.1:${GO_PORT}" GATEWAY_BASE_URL="http://127.0.0.1:${RUST_PORT}" go run ./cmd/openlist-runtime >"$GO_LOG" 2>&1
) &
GO_PID=$!

for _ in $(seq 1 80); do
  if curl_local -fsS "http://127.0.0.1:${GO_PORT}/ping" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done
if ! curl_local -fsS "http://127.0.0.1:${GO_PORT}/ping" >/dev/null; then
  echo "go service failed to start"
  cat "$GO_LOG" || true
  exit 1
fi

curl_local -fsS -X PUT "http://127.0.0.1:${RUST_PORT}/v2/admin/runtime-kv/test_key" \
  -H 'content-type: application/json' \
  -d '{"value":"abc"}' >/dev/null

val="$(curl_local -fsS "http://127.0.0.1:${RUST_PORT}/v2/admin/runtime-kv/test_key" | python3 -c 'import sys,json; print(json.load(sys.stdin)["value"])')"
[[ "$val" == "abc" ]]

curl_local -fsS -X PUT "http://127.0.0.1:${RUST_PORT}/v2/admin/timeout-profiles/default" \
  -H 'content-type: application/json' \
  -d '{"connect_ms":800,"ttfb_ms":900,"read_idle_ms":1200,"total_ms":3000}' >/dev/null

curl_local -fsS "http://127.0.0.1:${RUST_PORT}/v2/admin/db/integrity" >/dev/null
curl_local -fsS "http://127.0.0.1:${RUST_PORT}/v2/admin/db/checkpoint" >/dev/null

out="$(curl_local -fsS -X POST "http://127.0.0.1:${GO_PORT}/v1/crypto/decrypt-names" -H 'content-type: application/json' -d '{"names":["foo.enc","bar.enc"]}')"
python3 - "$out" <<'PY'
import json,sys
obj=json.loads(sys.argv[1])
assert obj["names"]==["foo","bar"]
PY

curl_local -fsS -X POST "http://127.0.0.1:${GO_PORT}/v1/admin/backoff/activate?seconds=2" >/dev/null
status="$(curl_local -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${GO_PORT}/healthz")"
[[ "$status" == "503" ]]

sleep 2
curl_local -fsS "http://127.0.0.1:${GO_PORT}/healthz" >/dev/null

echo "runtime-e2e-check passed"
