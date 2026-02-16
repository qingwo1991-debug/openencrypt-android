# 02 Repo Layout

## Directories
- `app-android/`
- `core-openlist-go/`
- `core-encrypt-rs/`
- `schemas/`
- `.github/workflows/`
- `docs/`
- `tools/`

## Ownership Boundaries
- app team: UI, permissions, service lifecycle, update UX
- go core team: OpenList integration, mobile embedding
- rust core team: encryption/proxy performance path, SQLite data

## Versioning
- Semantic tags: `vX.Y.Z`
- Stable release assets:
  - `openencrypt-android-vX.Y.Z-arm64-v8a.apk`
  - `openencrypt-android-vX.Y.Z-armeabi-v7a.apk`
  - `checksums.txt`
