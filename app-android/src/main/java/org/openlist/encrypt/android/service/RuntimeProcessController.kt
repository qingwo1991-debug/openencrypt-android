package org.openlist.encrypt.android.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openlist.encrypt.android.config.ConfigRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

interface RuntimeProcessController {
    suspend fun startOpenList()
    suspend fun stopOpenList()
    suspend fun startGateway()
    suspend fun stopGateway()
    suspend fun checkOpenListHealth(): Boolean
    suspend fun checkGatewayHealth(): Boolean
}

class DefaultRuntimeProcessController(
    private val context: Context,
    private val healthTimeoutMs: Int = 1500
) : RuntimeProcessController {
    private val configRepo = ConfigRepository(context)

    override suspend fun startOpenList() = withContext(Dispatchers.IO) {
        val cfg = configRepo.loadOrDefault()
        val host = cfg.openlist.host
        val port = cfg.openlist.port

        if (isProcessRunning(openListProcess)) return@withContext

        val bin = resolveBinary("OPENLIST_BIN", "openlist-runtime")
        val env = mapOf(
            "LISTEN_ADDR" to "$host:$port",
            "GATEWAY_BASE_URL" to "http://127.0.0.1:${cfg.gateway.port}",
            "HEADER_TIMEOUT_MS" to cfg.webdav.headerTimeoutMs.toString(),
            "READ_IDLE_TIMEOUT_MS" to cfg.webdav.readIdleTimeoutMs.toString(),
            "PROBE_BUDGET_LIST_MS" to cfg.runtime.probeBudgetListMs.toString(),
            "PROBE_BUDGET_STREAM_MS" to cfg.runtime.probeBudgetStreamMs.toString(),
            "UPSTREAM_BACKOFF_SECONDS" to cfg.runtime.upstreamBackoffSeconds.toString(),
            "ENABLE_UPSTREAM_FAST_FAIL" to cfg.runtime.enableUpstreamFastFail.toString(),
            "ENABLE_PARALLEL_DECRYPT" to cfg.gateway.enableParallelDecrypt.toString(),
            "PARALLEL_DECRYPT_CONCURRENCY" to cfg.gateway.parallelDecryptConcurrency.toString()
        )

        openListProcess = launch(bin, env)
    }

    override suspend fun stopOpenList() = withContext(Dispatchers.IO) {
        openListProcess?.let { stopProcess(it) }
        openListProcess = null
    }

    override suspend fun startGateway() = withContext(Dispatchers.IO) {
        val cfg = configRepo.loadOrDefault()
        if (isProcessRunning(gatewayProcess)) return@withContext

        val bin = resolveBinary("GATEWAY_BIN", "openencrypt-gateway")
        val dbPath = File(context.filesDir, "openencrypt/data/openencrypt.sqlite3").absolutePath
        val env = mapOf(
            "LISTEN_ADDR" to "127.0.0.1:${cfg.gateway.port}",
            "SQLITE_PATH" to dbPath,
            "AUTO_MIGRATE" to "true"
        )

        gatewayProcess = launch(bin, env)
    }

    override suspend fun stopGateway() = withContext(Dispatchers.IO) {
        gatewayProcess?.let { stopProcess(it) }
        gatewayProcess = null
    }

    override suspend fun checkOpenListHealth(): Boolean {
        val cfg = configRepo.loadOrDefault()
        val base = "http://${cfg.openlist.host}:${cfg.openlist.port}"
        return probe("$base/ping") || probe("$base/healthz")
    }

    override suspend fun checkGatewayHealth(): Boolean {
        val cfg = configRepo.loadOrDefault()
        return probe("http://127.0.0.1:${cfg.gateway.port}/healthz")
    }

    private fun launch(binary: File, env: Map<String, String>): Process {
        require(binary.exists()) { "binary not found: ${binary.absolutePath}" }
        require(binary.canExecute()) { "binary not executable: ${binary.absolutePath}" }

        return ProcessBuilder(binary.absolutePath)
            .directory(binary.parentFile)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(env)
            }
            .start()
    }

    private fun resolveBinary(envKey: String, defaultName: String): File {
        val byEnv = System.getenv(envKey)
        if (!byEnv.isNullOrBlank()) {
            return File(byEnv)
        }
        return File(context.filesDir, "openencrypt/bin/$defaultName")
    }

    private fun stopProcess(p: Process) {
        if (!isProcessRunning(p)) return
        p.destroy()
        if (!waitUntilExit(p, 1500)) {
            p.destroyForcibly()
            waitUntilExit(p, 1000)
        }
    }

    private fun isProcessRunning(p: Process?): Boolean {
        if (p == null) return false
        return try {
            p.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun waitUntilExit(p: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (isProcessRunning(p) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        return !isProcessRunning(p)
    }

    private suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.connectTimeout = healthTimeoutMs
            conn.readTimeout = healthTimeoutMs
            conn.connect()
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    companion object {
        @Volatile
        private var openListProcess: Process? = null

        @Volatile
        private var gatewayProcess: Process? = null
    }
}
