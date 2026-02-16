#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DOCS="$ROOT/docs"

required=(
  00_overview.md
  01_architecture.md
  02_repo_layout.md
  03_roadmap.md
  04_android_ui_ia.md
  05_android_ui_fields.md
  06_service_lifecycle.md
  07_webdav_parallel_timeout.md
  08_rust_sqlite_migration.md
  09_update_release_design.md
  10_ci_cd_github_actions.md
  11_test_acceptance.md
  DECISIONS.md
  CHANGELOG_PLAN.md
)

for f in "${required[@]}"; do
  test -f "$DOCS/$f" || { echo "missing docs/$f"; exit 1; }
done

echo "phase-check passed"
