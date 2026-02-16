#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <soak-log-file>"
  exit 1
fi

LOG_FILE="$1"
if [[ ! -f "$LOG_FILE" ]]; then
  echo "missing log file: $LOG_FILE"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/.reports"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/soak-report-$(date +%Y%m%d-%H%M%S).md"

total="$(wc -l < "$LOG_FILE" | tr -d ' ')"
go_ok="$(grep -c 'go=200' "$LOG_FILE" || true)"
rs_ok="$(grep -c 'rust=200' "$LOG_FILE" || true)"
full_ok="$(grep -Ec 'go=200 rust=200' "$LOG_FILE" || true)"
go_fail=$((total - go_ok))
rs_fail=$((total - rs_ok))

{
  echo "# Soak Report"
  echo
  echo "- generated_at: $(date -Iseconds)"
  echo "- source_log: $LOG_FILE"
  echo "- samples: $total"
  echo "- both_healthy_samples: $full_ok"
  echo "- go_fail_samples: $go_fail"
  echo "- rust_fail_samples: $rs_fail"
  echo
  echo "## Exit Gate"
  if [[ "$go_fail" -eq 0 && "$rs_fail" -eq 0 ]]; then
    echo "- status: PASS"
  else
    echo "- status: FAIL"
  fi
} >"$REPORT_FILE"

echo "soak-report generated: $REPORT_FILE"
