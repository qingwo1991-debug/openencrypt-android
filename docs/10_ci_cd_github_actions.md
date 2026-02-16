# 10 CI/CD (GitHub Actions)

Language: [中文](#中文) | [English](#english)

## 中文
### PR CI
- Kotlin lint + unit tests + debug assemble
- Rust fmt/clippy/test（当 `core-encrypt-rs/Cargo.toml` 存在）
- Go vet/test（当 `core-openlist-go/go.mod` 存在）
- schema 校验

### Release CI（tag）
- 触发条件：`v*.*.*`
- 产出：`arm64-v8a` 和 `armeabi-v7a` split APK
- 签名：从 GitHub `production` Environment secrets 读取
- 产物：APK + `checksums.txt` 发布到 GitHub Release

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
- Trigger: `v*.*.*`
- Outputs: split APKs for `arm64-v8a` and `armeabi-v7a`
- Signing: read from GitHub `production` Environment secrets
- Publish: APKs + `checksums.txt` to GitHub Release

### Required Secrets (production Environment)
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### Stability Notes
- Prefer Gradle Wrapper (`./gradlew`); fallback to `gradle` when wrapper is absent.
- Validate keystore + alias before release build to fail fast on signing issues.
