package org.openlist.encrypt.android.config

const val DEFAULT_UPDATE_GITHUB_REPO = "qingwo1991-debug/openencrypt-android"

data class OpenListConfig(
    val host: String = "127.0.0.1",
    val port: Int = 5244,
    val https: Boolean = false,
    val h2c: Boolean = false
)

data class GatewayConfig(
    val port: Int = 5344,
    val enableParallelDecrypt: Boolean = true,
    val parallelDecryptConcurrency: Int = 4,
    val streamBufferKb: Int = 512
)

data class WebDavConfig(
    val enable: Boolean = true,
    val headerTimeoutMs: Int = 5000,
    val readIdleTimeoutMs: Int = 12000,
    val propfindRetryTimeoutMs: Int = 1500
)

data class RuntimeConfig(
    val upstreamTimeoutSeconds: Int = 8,
    val probeTimeoutSeconds: Int = 3,
    val probeBudgetListMs: Int = 1200,
    val probeBudgetStreamMs: Int = 2500,
    val upstreamBackoffSeconds: Int = 20,
    val enableUpstreamFastFail: Boolean = true
)

data class EncryptRule(
    val path: String,
    val password: String,
    val encType: String = "aes-ctr",
    val encName: Boolean = false,
    val enable: Boolean = true
)

data class UpdateConfig(
    val channel: String = "stable",
    val githubRepo: String = DEFAULT_UPDATE_GITHUB_REPO,
    val autoCheck: Boolean = true
)

data class AppRuntimeConfig(
    val openlist: OpenListConfig = OpenListConfig(),
    val gateway: GatewayConfig = GatewayConfig(),
    val webdav: WebDavConfig = WebDavConfig(),
    val runtime: RuntimeConfig = RuntimeConfig(),
    val encryptRules: List<EncryptRule> = emptyList(),
    val update: UpdateConfig = UpdateConfig()
)

data class ConfigSaveResult(
    val changedKeys: List<String>,
    val backupPath: String?
)
