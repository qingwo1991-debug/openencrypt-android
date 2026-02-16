# Decisions Log

## 2026-02-16
- Use standalone repo `openencrypt-android` (not shared with existing single repo).
- Keep OpenList core in Go; do not rewrite OpenList fully in Rust in this delivery.
- Use Rust for encryption/proxy critical path.
- Android frontend is native Kotlin (not Flutter).
- APK in-app update source is GitHub Release (Stable only).
- CI signing uses GitHub Secrets.
- Config UX uses full coverage + layered visibility (Standard/Expert).
- Android data backend is SQLite only.
