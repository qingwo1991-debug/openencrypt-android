#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

bash "$ROOT/tools/phase-check.sh"

test -f "$ROOT/schemas/config.schema.json" || { echo "missing schema"; exit 1; }
test -f "$ROOT/.github/workflows/ci.yml" || { echo "missing ci workflow"; exit 1; }
test -f "$ROOT/.github/workflows/release.yml" || { echo "missing release workflow"; exit 1; }

echo "bootstrap-check passed"
