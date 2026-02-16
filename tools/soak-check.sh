#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DURATION_HOURS="${1:-72}"
INTERVAL_SECONDS="${2:-30}"
LOG_DIR="${ROOT}/.soak"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/soak-$(date +%Y%m%d-%H%M%S).log"

export NO_PROXY="127.0.0.1,localhost"
export no_proxy="$NO_PROXY"

end_ts=$(( $(date +%s) + DURATION_HOURS*3600 ))

while (( $(date +%s) < end_ts )); do
  ts="$(date -Iseconds)"
  go_status="$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' http://127.0.0.1:5244/healthz || true)"
  rs_status="$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' http://127.0.0.1:5344/healthz || true)"
  echo "$ts go=$go_status rust=$rs_status" | tee -a "$LOG_FILE"
  sleep "$INTERVAL_SECONDS"
done

echo "soak-check complete: $LOG_FILE"
