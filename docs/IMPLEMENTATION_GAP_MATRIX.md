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
- `Implemented (baseline)` Main tabs now use single Activity + multi Fragment container.
- `Implemented (baseline)` Dashboard/Cloud/Encrypt/Tasks/Settings with sub-sections covering 10-page IA intent.
- `Gap` Dedicated standalone routes for all sub-pages and richer UX polish are pending.

## docs/05_android_ui_fields.md
- `Implemented (baseline)` Schema registry + duplicate editable check + validator baseline.
- `Implemented (this round)` Standard/Expert split UI and save flow `validate -> diff preview -> confirm -> apply atomically`.
- `Gap` Full schema-driven dynamic form generation and risk-label surface are pending.

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
- `Implemented (this round)` Settings page now exposes update center section in dedicated sub-page area.

## docs/10_ci_cd_github_actions.md
- `Implemented (this round)` CI debug artifacts + preview release artifacts + release tag signing pipeline.
- `Implemented (this round)` Cache-warning mitigation in CI (disable unstable action cache paths).
- `Implemented (this round)` `main` push auto patch bump + tag + GitHub Release publishing.

## docs/11_test_acceptance.md
- `Implemented (this round)` Automated acceptance includes playback/range/WebDAV matrix and runtime soak automation/report generation.
- `Gap` 72h real-device soak final sign-off remains an execution activity, not a tooling gap.

## docs/12_signing_and_env_setup.md
- `Implemented` Secret model and release signing workflow are consistent with docs.

## Next execution order
1. Execute 72h real-device soak run on device/self-hosted environment (`tools/runtime-soak-check.sh 4320 15`) and archive report.
2. ABI-tier performance tuning and release hardening pass for `v1.0.0`.
