#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <owner> <repo> [branch]"
  exit 1
fi

OWNER="$1"
REPO="$2"
BRANCH="${3:-main}"
FULL_REPO="${OWNER}/${REPO}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAX_RETRIES="${CI_MAX_RETRIES:-3}"
RETRY_BACKOFF_SECONDS="${CI_RETRY_BACKOFF_SECONDS:-15}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing command: $1"
    exit 1
  }
}

require_cmd gh
require_cmd git

cd "$ROOT"

if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not authenticated. run: gh auth login"
  exit 1
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "git remote 'origin' is not set"
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "working tree is not clean; commit or stash first"
  exit 1
fi

echo "[1/5] pushing ${BRANCH}"
git push origin "$BRANCH"

echo "[2/5] triggering CI workflow"
gh workflow run CI --repo "$FULL_REPO" --ref "$BRANCH"

attempt=1
while [[ "$attempt" -le "$MAX_RETRIES" ]]; do
  # Wait for run to appear.
  echo "[3/5] waiting for run creation (attempt ${attempt}/${MAX_RETRIES})"
  RUN_ID=""
  for _ in $(seq 1 20); do
    RUN_ID="$(gh run list --repo "$FULL_REPO" --workflow CI --branch "$BRANCH" --limit 1 --json databaseId --jq '.[0].databaseId' || true)"
    if [[ -n "$RUN_ID" && "$RUN_ID" != "null" ]]; then
      break
    fi
    sleep 2
  done

  if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
    echo "failed to locate the triggered run"
    exit 1
  fi

  echo "[4/5] watching run ${RUN_ID}"
  set +e
  gh run watch "$RUN_ID" --repo "$FULL_REPO" --exit-status
  STATUS=$?
  set -e

  if [[ $STATUS -eq 0 ]]; then
    echo "[5/5] CI passed: run ${RUN_ID}"
    gh run view "$RUN_ID" --repo "$FULL_REPO"
    exit 0
  fi

  LOG_DIR="$ROOT/.ci-logs"
  mkdir -p "$LOG_DIR"
  LOG_FILE="$LOG_DIR/run-${RUN_ID}.log"

  echo "[5/5] CI failed, downloading logs -> ${LOG_FILE}"
  if gh run view "$RUN_ID" --repo "$FULL_REPO" --log > "$LOG_FILE"; then
    echo "----- failure summary (first 80 matched lines) -----"
    grep -nE "(^Error:|^FAIL|\bException\b|\bBUILD FAILED\b|\berror:\b|\bfailed\b)" "$LOG_FILE" | head -n 80 || true
    echo "full log: $LOG_FILE"
  else
    echo "failed to download full logs (possible workflow-file failure before jobs)"
    gh run view "$RUN_ID" --repo "$FULL_REPO" || true
  fi

  if [[ "$attempt" -ge "$MAX_RETRIES" ]]; then
    echo "CI failed after ${MAX_RETRIES} attempts"
    exit "$STATUS"
  fi

  echo "retrying in ${RETRY_BACKOFF_SECONDS}s..."
  sleep "$RETRY_BACKOFF_SECONDS"
  attempt=$((attempt + 1))
  gh workflow run CI --repo "$FULL_REPO" --ref "$BRANCH"
done
