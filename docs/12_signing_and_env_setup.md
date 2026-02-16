# 12 Signing & Environment Setup

Language: [中文](#中文) | [English](#english)

## 中文
### 目标
- 使用全新 Android 签名并长期固定，确保后续发布是“升级安装”而非签名冲突。
- 固化 GitHub 与本地环境变量，支持无人值守发布流程。

### 1) 生成全新 keystore（一次性）
```bash
keytool -genkeypair \
  -v \
  -keystore release.keystore \
  -alias openencrypt-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 36500
```

固定建议：
- `alias` 固定为 `openencrypt-release`
- keystore 文件名固定为 `release.keystore`

### 2) 生成 Base64 并写入 GitHub Environment secrets
```bash
base64 -w 0 release.keystore > release.keystore.base64
```

在 GitHub 创建 `production` Environment，然后写入：
- `ANDROID_KEYSTORE_BASE64` = `release.keystore.base64` 内容
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS` = `openencrypt-release`
- `ANDROID_KEY_PASSWORD`

CLI（可选）：
```bash
gh secret set ANDROID_KEYSTORE_BASE64 --env production < release.keystore.base64
printf '%s' 'your-store-password' | gh secret set ANDROID_KEYSTORE_PASSWORD --env production
printf '%s' 'openencrypt-release' | gh secret set ANDROID_KEY_ALIAS --env production
printf '%s' 'your-key-password' | gh secret set ANDROID_KEY_PASSWORD --env production
```

### 3) 本地环境变量固定（direnv）
```bash
cp .envrc.example .envrc
direnv allow
```

`.envrc` 至少配置：
- `GH_TOKEN`
- `OPENENCRYPT_GH_OWNER`
- `OPENENCRYPT_GH_REPO`
- `CI_MAX_RETRIES`（默认 3）
- `CI_RETRY_BACKOFF_SECONDS`（默认 15）

### 4) 触发与验证
1. 推送 `main`：验证 CI。
2. 打 tag（如 `v0.1.0`）：触发 release。
3. 安装新 APK 到同包名旧版本设备上，验证升级安装成功（无签名冲突）。

### 5) 安全与备份
- `release.keystore` 离线加密备份至少两份。
- 密码与 keystore 分离保管。
- 不要轮换签名，除非明确接受“无法覆盖升级旧安装”。

## English
### Goal
- Create a brand-new Android signing identity and keep it fixed to ensure future installs are upgrades, not signature conflicts.
- Stabilize GitHub and local environment variables for unattended release flow.

### 1) Generate a new keystore (one-time)
```bash
keytool -genkeypair \
  -v \
  -keystore release.keystore \
  -alias openencrypt-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 36500
```

Recommended constants:
- alias: `openencrypt-release`
- keystore file: `release.keystore`

### 2) Base64 encode and upload GitHub Environment secrets
```bash
base64 -w 0 release.keystore > release.keystore.base64
```

Create a GitHub `production` Environment, then set:
- `ANDROID_KEYSTORE_BASE64` = content of `release.keystore.base64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS` = `openencrypt-release`
- `ANDROID_KEY_PASSWORD`

Optional CLI:
```bash
gh secret set ANDROID_KEYSTORE_BASE64 --env production < release.keystore.base64
printf '%s' 'your-store-password' | gh secret set ANDROID_KEYSTORE_PASSWORD --env production
printf '%s' 'openencrypt-release' | gh secret set ANDROID_KEY_ALIAS --env production
printf '%s' 'your-key-password' | gh secret set ANDROID_KEY_PASSWORD --env production
```

### 3) Persist local env with direnv
```bash
cp .envrc.example .envrc
direnv allow
```

Minimum `.envrc` keys:
- `GH_TOKEN`
- `OPENENCRYPT_GH_OWNER`
- `OPENENCRYPT_GH_REPO`
- `CI_MAX_RETRIES` (default 3)
- `CI_RETRY_BACKOFF_SECONDS` (default 15)

### 4) Trigger and verify
1. Push `main` to validate CI.
2. Create a tag (for example `v0.1.0`) to trigger release.
3. Install new APK over an existing install with same package name and confirm upgrade succeeds.

### 5) Security and backup
- Keep at least two encrypted offline backups of `release.keystore`.
- Store passwords separately from the keystore.
- Do not rotate signing identity unless you accept upgrade incompatibility.
