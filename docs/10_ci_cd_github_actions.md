# 10 CI/CD (GitHub Actions)

Language: [中文](#中文) | [English](#english)

## 中文
### PR CI
- Kotlin lint + unit tests + debug assemble
- Rust fmt/clippy/test（当 `core-encrypt-rs/Cargo.toml` 存在）
- Go vet/test（当 `core-openlist-go/go.mod` 存在）
- schema 校验

### Release CI（tag）
- 触发条件：`main` push（自动计算并创建下一个 `vX.Y.Z` tag）
- 版本：`versionName=vX.Y.Z`，`versionCode` 自动递增（用于升级安装）
- 产出：`arm64-v8a` 和 `armeabi-v7a` split APK
- 签名：从 GitHub `production` Environment secrets 读取
- 产物：APK + `checksums.txt` 发布到 GitHub Release

### Acceptance CI（手动）
- 工作流：`Acceptance`
- 可传入 `soak_minutes` 与探测间隔；GitHub hosted runner 会将 `soak_minutes` 限制在 60 分钟以内
- 输出：Playback/WebDAV 矩阵与 Soak 报告（上传为 artifact）

### 必需 Secrets（production Environment）
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### 稳定性约定
- 优先使用 Gradle Wrapper（`./gradlew`）；若缺失则回退到 `gradle`。
- release 前校验 keystore + alias，避免“构建成功但签名无效”。

## English
### PR CI
- Kotlin lint + unit tests + debug assemble
- Rust fmt/clippy/test (only when `core-encrypt-rs/Cargo.toml` exists)
- Go vet/test (only when `core-openlist-go/go.mod` exists)
- schema validation

### Release CI (tag)
- Trigger: push to `main` (auto-resolve and create next `vX.Y.Z` tag)
- Versioning: `versionName=vX.Y.Z`, `versionCode` auto-incremented for upgrade installs
- Outputs: split APKs for `arm64-v8a` and `armeabi-v7a`
- Signing: read from GitHub `production` Environment secrets
- Publish: APKs + `checksums.txt` to GitHub Release

### Acceptance CI (manual)
- Workflow: `Acceptance`
- Inputs: `soak_minutes` (hosted runner capped at 60) and probe interval
- Outputs: playback/WebDAV matrix and soak reports uploaded as artifacts
- 72h soak is executed on real-device/self-hosted environment, not on GitHub hosted runner.
- Android CI/Release builds run `tools/check-android-runtime-bins.sh` before Gradle to fail fast when runtime binaries are missing.

### Required Secrets (production Environment)
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### Stability Notes
- Prefer Gradle Wrapper (`./gradlew`); fallback to `gradle` when wrapper is absent.
- Validate keystore + alias before release build to fail fast on signing issues.
