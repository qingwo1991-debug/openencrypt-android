# Plan Changelog

## v0.1.0-doc-freeze (2026-02-16)
- Initialized standalone project scaffold.
- Added phased implementation docs (`00` to `11`).
- Locked architecture, release, UI, CI, and acceptance criteria.
- Added decision log and phased anti-context-loss workflow.

## v0.2.0-bootstrap (2026-02-16)
- Added Android Gradle project skeleton (`app-android`) with `applicationId=org.openlist.encrypt.android`.
- Added config schema draft and example runtime config (`schemas/config.schema.json`, `schemas/config.example.json`).
- Added CI workflows for phase checks and stable release asset publication templates.
- Added runtime policy model skeletons (update policy, runtime state, diagnostics DTOs).
- Added `tools/phase-check.sh` and wired it into CI.

## v0.3.0-runtime-core (2026-02-16)
- Added Android runtime lifecycle implementation with start/recover/stop sequencing and foreground service actions.
- Added config repository with validation, diff calculation, atomic save and snapshot backup.
- Added schema-driven field registry from app assets and wired basic UI navigation pages.
- Added stable update decision utilities (semver compare, ABI/checksum asset selection, checksum verifier).
- Added Rust gateway DB integrity and WAL checkpoint admin endpoints.
- Upgraded CI with schema checks and full Rust quality gates.

## v0.4.0-go-runtime-integration (2026-02-16)
- Implemented `core-openlist-go` runtime service with health endpoints, gateway reverse proxy, timeout budgets, and upstream fast-fail backoff.
- Added bounded parallel filename decrypt implementation with stable order and concurrent-safe cache.
- Added Go unit tests for backoff, decrypt pipeline, and server endpoint.
- Reworked Android runtime process controller to launch/stop real Go and Rust native processes.
- Added native binary installer hook in Android application startup.
- Added local e2e runtime check and soak check scripts, and integrated them into acceptance checks.
