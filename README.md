# OpenEncrypt Android

Standalone Android project for OpenList + transparent encryption gateway.

## Status
- Phase-driven implementation in progress.
- Documentation-first freeze is complete in `docs/`.

## Fixed Project Decisions
- Repo: `openencrypt-android`
- Application ID: `org.openlist.encrypt.android`
- License: AGPL-3.0
- Update channel: Stable only (GitHub Releases)
- Min SDK: 21
- Target ABI: `armeabi-v7a`, `arm64-v8a`

## Monorepo Layout
- `app-android/`: Android native app (Kotlin)
- `core-openlist-go/`: OpenList integration core
- `core-encrypt-rs/`: Encryption gateway core (Rust)
- `schemas/`: config schemas and validators
- `.github/workflows/`: CI/CD workflows
- `docs/`: phased implementation specifications

## Docs Entry
Read in order:
1. `docs/00_overview.md`
2. `docs/01_architecture.md`
3. `docs/02_repo_layout.md`
4. `docs/03_roadmap.md`

Then continue with UI, core, and release specs.
