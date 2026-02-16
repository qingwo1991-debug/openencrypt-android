# app-android

Native Kotlin Android app module.

Planned responsibilities:
- foreground service lifecycle orchestration
- full configuration UI (standard + expert)
- diagnostics and update center

Runtime binary packaging:
- Build requires ABI binaries in `src/main/assets/bin/<abi>/` for:
  - `openlist-runtime`
  - `openencrypt-gateway`
- `app-android/build.gradle.kts` auto-syncs from either:
  - `RUNTIME_BIN_ROOT/<abi>/<binary>`
  - `core-openlist-go/target/android/<abi>/openlist-runtime`
  - `core-encrypt-rs/target/android/<abi>/openencrypt-gateway`
- Missing binaries fail the build to prevent shipping a non-functional APK.
