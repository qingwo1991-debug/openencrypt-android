# 10 CI/CD (GitHub Actions)

## PR CI
- Kotlin lint + unit tests
- Rust fmt/clippy/test
- Go vet/test
- schema validation tests

## Release CI (tag)
- build split APKs for armv7/arm64
- sign APKs via GitHub Secrets
- generate checksum file
- publish release assets

## Required Secrets
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
