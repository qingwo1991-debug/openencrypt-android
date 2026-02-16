# 00 Overview

## Goal
Deliver a production-grade Android APK that combines OpenList capability and transparent encryption proxy capability with stable playback, fast list loading, robust background behavior, and complete frontend configuration.

## In Scope
- Android app (`minSdk 21`) supporting `armeabi-v7a` and `arm64-v8a`.
- Go OpenList runtime integration.
- Rust encryption gateway integration.
- SQLite-only local persistence.
- GitHub Actions build/release pipeline.
- GitHub Release as stable update source.

## Out of Scope
- Full OpenList rewrite in Rust.
- Multi-channel update policy (beta/nightly) in first release.

## Success Criteria
- Core chain works: cloud config, encryption config, WebDAV, upload encrypt, download decrypt, streaming playback.
- 72h stability run passes.
- App update check/download/install from GitHub stable release works.
- Frontend config coverage is complete with no duplicated editable entry.
