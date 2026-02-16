#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <github-owner> <repo-name> [public|private]"
  exit 1
fi

OWNER="$1"
REPO="$2"
VISIBILITY="${3:-private}"

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required"
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export GH_TOKEN

gh auth status >/dev/null 2>&1 || true

gh repo view "$OWNER/$REPO" >/dev/null 2>&1 || gh repo create "$OWNER/$REPO" "--${VISIBILITY}" --source . --remote origin --push

if ! git remote get-url origin >/dev/null 2>&1; then
  git remote add origin "https://github.com/$OWNER/$REPO.git"
fi

git push -u origin main

# Trigger workflow and print latest runs.
gh workflow run CI --repo "$OWNER/$REPO" || true
sleep 3
gh run list --repo "$OWNER/$REPO" --limit 5
