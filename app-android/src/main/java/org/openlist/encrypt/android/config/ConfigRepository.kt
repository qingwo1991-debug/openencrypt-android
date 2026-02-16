package org.openlist.encrypt.android.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigRepository(context: Context) {
    private val rootDir = File(context.filesDir, "openencrypt")
    private val backupDir = File(rootDir, "snapshots")
    private val configFile = File(rootDir, "runtime-config.json")

    fun loadOrDefault(): AppRuntimeConfig {
        if (!configFile.exists()) {
            return AppRuntimeConfig()
        }
        return try {
            val raw = configFile.readText()
            parse(raw)
        } catch (_: Throwable) {
            AppRuntimeConfig()
        }
    }

    fun saveAtomically(next: AppRuntimeConfig): ConfigSaveResult {
        val errors = ConfigValidator.validate(next)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        rootDir.mkdirs()
        backupDir.mkdirs()

        val previous = if (configFile.exists()) loadOrDefault() else null
        val changedKeys = computeChangedKeys(previous, next)

        val backupPath = if (configFile.exists()) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val backup = File(backupDir, "runtime-config-$stamp.json")
            configFile.copyTo(backup, overwrite = true)
            backup.absolutePath
        } else {
            null
        }

        val tmp = File(rootDir, "runtime-config.json.tmp")
        tmp.writeText(serialize(next).toString(2))
        if (!tmp.renameTo(configFile)) {
            throw IllegalStateException("failed to atomically replace runtime config")
        }

        return ConfigSaveResult(changedKeys = changedKeys, backupPath = backupPath)
    }

    fun diffKeys(previous: AppRuntimeConfig?, next: AppRuntimeConfig): List<String> {
        return computeChangedKeys(previous, next)
    }

    fun listSnapshotNames(limit: Int = 20): List<String> {
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .sortedByDescending { it.name }
            .take(limit)
            .map { it.name }
    }

    fun restoreFromSnapshot(snapshotName: String): ConfigSaveResult {
        val snapshot = File(backupDir, snapshotName)
        require(snapshot.exists() && snapshot.isFile) {
            "snapshot not found: $snapshotName"
        }
        val parsed = parse(snapshot.readText())
        return saveAtomically(parsed)
    }

    fun parseJson(raw: String): AppRuntimeConfig {
        return parse(raw)
    }

    fun toPrettyJson(config: AppRuntimeConfig): String {
        return serialize(config).toString(2)
    }

    private fun parse(raw: String): AppRuntimeConfig {
        val root = JSONObject(raw)
        val openlist = root.optJSONObject("openlist") ?: JSONObject()
        val gateway = root.optJSONObject("gateway") ?: JSONObject()
        val webdav = root.optJSONObject("webdav") ?: JSONObject()
        val runtime = root.optJSONObject("runtime") ?: JSONObject()
        val update = root.optJSONObject("update") ?: JSONObject()

        val rulesJson = root.optJSONArray("encrypt_rules") ?: JSONArray()
        val rules = buildList {
            for (i in 0 until rulesJson.length()) {
                val r = rulesJson.optJSONObject(i) ?: continue
                add(
                    EncryptRule(
                        path = r.optString("path"),
                        password = r.optString("password"),
                        encType = r.optString("enc_type", "aes-ctr"),
                        encName = r.optBoolean("enc_name", false),
                        enable = r.optBoolean("enable", true)
                    )
                )
            }
        }

        return AppRuntimeConfig(
            openlist = OpenListConfig(
                host = openlist.optString("host", "127.0.0.1"),
                port = openlist.optInt("port", 5244),
                https = openlist.optBoolean("https", false),
                h2c = openlist.optBoolean("h2c", false)
            ),
            gateway = GatewayConfig(
                port = gateway.optInt("port", 5344),
                enableParallelDecrypt = gateway.optBoolean("enable_parallel_decrypt", true),
                parallelDecryptConcurrency = gateway.optInt("parallel_decrypt_concurrency", 4),
                streamBufferKb = gateway.optInt("stream_buffer_kb", 512)
            ),
            webdav = WebDavConfig(
                enable = webdav.optBoolean("enable", true),
                headerTimeoutMs = webdav.optInt("header_timeout_ms", 5000),
                readIdleTimeoutMs = webdav.optInt("read_idle_timeout_ms", 12000),
                propfindRetryTimeoutMs = webdav.optInt("propfind_retry_timeout_ms", 1500)
            ),
            runtime = RuntimeConfig(
                upstreamTimeoutSeconds = runtime.optInt("upstream_timeout_seconds", 8),
                probeTimeoutSeconds = runtime.optInt("probe_timeout_seconds", 3),
                probeBudgetListMs = runtime.optInt("probe_budget_list_ms", 1200),
                probeBudgetStreamMs = runtime.optInt("probe_budget_stream_ms", 2500),
                upstreamBackoffSeconds = runtime.optInt("upstream_backoff_seconds", 20),
                enableUpstreamFastFail = runtime.optBoolean("enable_upstream_fast_fail", true)
            ),
            encryptRules = rules,
            update = UpdateConfig(
                channel = update.optString("channel", "stable"),
                githubRepo = update.optString("github_repo", DEFAULT_UPDATE_GITHUB_REPO),
                autoCheck = update.optBoolean("auto_check", true)
            )
        )
    }

    private fun serialize(config: AppRuntimeConfig): JSONObject {
        val rules = JSONArray()
        config.encryptRules.forEach { r ->
            rules.put(
                JSONObject()
                    .put("path", r.path)
                    .put("password", r.password)
                    .put("enc_type", r.encType)
                    .put("enc_name", r.encName)
                    .put("enable", r.enable)
            )
        }

        return JSONObject()
            .put(
                "openlist",
                JSONObject()
                    .put("host", config.openlist.host)
                    .put("port", config.openlist.port)
                    .put("https", config.openlist.https)
                    .put("h2c", config.openlist.h2c)
            )
            .put(
                "gateway",
                JSONObject()
                    .put("port", config.gateway.port)
                    .put("enable_parallel_decrypt", config.gateway.enableParallelDecrypt)
                    .put("parallel_decrypt_concurrency", config.gateway.parallelDecryptConcurrency)
                    .put("stream_buffer_kb", config.gateway.streamBufferKb)
            )
            .put(
                "webdav",
                JSONObject()
                    .put("enable", config.webdav.enable)
                    .put("header_timeout_ms", config.webdav.headerTimeoutMs)
                    .put("read_idle_timeout_ms", config.webdav.readIdleTimeoutMs)
                    .put("propfind_retry_timeout_ms", config.webdav.propfindRetryTimeoutMs)
            )
            .put(
                "runtime",
                JSONObject()
                    .put("upstream_timeout_seconds", config.runtime.upstreamTimeoutSeconds)
                    .put("probe_timeout_seconds", config.runtime.probeTimeoutSeconds)
                    .put("probe_budget_list_ms", config.runtime.probeBudgetListMs)
                    .put("probe_budget_stream_ms", config.runtime.probeBudgetStreamMs)
                    .put("upstream_backoff_seconds", config.runtime.upstreamBackoffSeconds)
                    .put("enable_upstream_fast_fail", config.runtime.enableUpstreamFastFail)
            )
            .put("encrypt_rules", rules)
            .put(
                "update",
                JSONObject()
                    .put("channel", config.update.channel)
                    .put("github_repo", config.update.githubRepo)
                    .put("auto_check", config.update.autoCheck)
            )
    }

    private fun computeChangedKeys(previous: AppRuntimeConfig?, next: AppRuntimeConfig): List<String> {
        if (previous == null) return flatten(next).keys.sorted()
        val before = flatten(previous)
        val after = flatten(next)

        return (before.keys + after.keys)
            .toSet()
            .filter { before[it] != after[it] }
            .sorted()
    }

    private fun flatten(config: AppRuntimeConfig): Map<String, String> {
        val result = linkedMapOf<String, String>()
        result["openlist.host"] = config.openlist.host
        result["openlist.port"] = config.openlist.port.toString()
        result["openlist.https"] = config.openlist.https.toString()
        result["openlist.h2c"] = config.openlist.h2c.toString()
        result["gateway.port"] = config.gateway.port.toString()
        result["gateway.enable_parallel_decrypt"] = config.gateway.enableParallelDecrypt.toString()
        result["gateway.parallel_decrypt_concurrency"] = config.gateway.parallelDecryptConcurrency.toString()
        result["gateway.stream_buffer_kb"] = config.gateway.streamBufferKb.toString()
        result["runtime.upstream_timeout_seconds"] = config.runtime.upstreamTimeoutSeconds.toString()
        result["runtime.probe_timeout_seconds"] = config.runtime.probeTimeoutSeconds.toString()
        result["runtime.probe_budget_list_ms"] = config.runtime.probeBudgetListMs.toString()
        result["runtime.probe_budget_stream_ms"] = config.runtime.probeBudgetStreamMs.toString()
        result["runtime.upstream_backoff_seconds"] = config.runtime.upstreamBackoffSeconds.toString()
        result["runtime.enable_upstream_fast_fail"] = config.runtime.enableUpstreamFastFail.toString()
        result["update.channel"] = config.update.channel
        result["update.github_repo"] = config.update.githubRepo
        result["update.auto_check"] = config.update.autoCheck.toString()
        config.encryptRules.forEachIndexed { i, r ->
            result["encrypt_rules[$i].path"] = r.path
            result["encrypt_rules[$i].enc_type"] = r.encType
            result["encrypt_rules[$i].enc_name"] = r.encName.toString()
            result["encrypt_rules[$i].enable"] = r.enable.toString()
        }
        return result
    }
}
