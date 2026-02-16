# Implementation Gap Matrix

Last updated: 2026-02-16

## docs/00_overview.md
- `Partially Implemented` Core runtime scaffolding, release pipeline, and update primitives exist.
- `Not Implemented` Full product chain (UI coverage, playback chain, 72h device soak) is incomplete.

## docs/01_architecture.md
- `Partially Implemented` Runtime topology, start order, health checks, and recovery loops are implemented.
- `Gap` Runtime config rollback and full diagnostics depth are not complete.

## docs/02_repo_layout.md
- `Implemented` Directory layout and release asset naming convention are in place.

## docs/03_roadmap.md
- `Partially Implemented` M1/M3 major scaffolding exists.
- `Not Implemented` M2 full product UX and M4 hardening are pending.

## docs/04_android_ui_ia.md
- `Partially Implemented` Main tabs and basic page summaries exist.
- `Not Implemented` Full 10-page IA with dedicated screens is pending.

## docs/05_android_ui_fields.md
- `Partially Implemented` Schema registry, duplicate editable check, and validator baseline exist.
- `Not Implemented` Full editable forms (standard/expert), diff preview page, and full atomic apply UX.

## docs/06_service_lifecycle.md
- `Partially Implemented` State machine, start/stop sequencing, and recovery are implemented.
- `Gap` Battery optimization guided remediation UX is not implemented.

## docs/07_webdav_parallel_timeout.md
- `Implemented` Parallel decrypt, timeout budgets, and fast-fail gate exist in Go runtime.
- `Gap` Real-world threshold tuning and matrix validation remain pending.

## docs/08_rust_sqlite_migration.md
- `Implemented` SQLite backend, WAL enablement, migration bootstrap, integrity/checkpoint endpoints are implemented.

## docs/09_update_release_design.md
- `Partially Implemented` Stable release fetch, semver compare, ABI/checksum/signing verify logic exists.
- `Implemented (this round)` In-app check/download/install workflow with update history persistence.

## docs/10_ci_cd_github_actions.md
- `Implemented (this round)` CI debug artifacts + preview release artifacts + release tag signing pipeline.
- `Implemented (this round)` Cache-warning mitigation in CI (disable unstable action cache paths).

## docs/11_test_acceptance.md
- `Partially Implemented` Automated subset exists in `tools/acceptance-check.sh`.
- `Gap` Full functional/stability/performance acceptance (including 72h soak and playback matrix) is pending.

## docs/12_signing_and_env_setup.md
- `Implemented` Secret model and release signing workflow are consistent with docs.

## Next execution order
1. Full config UI screens (standard/expert, diff preview, atomic apply UX).
2. Update center dedicated screen and richer failure remediation UX.
3. Functional playback/WebDAV matrix + 72h real-device soak automation/reporting.
4. ABI-tier performance tuning and release hardening pass for `v1.0.0`.
