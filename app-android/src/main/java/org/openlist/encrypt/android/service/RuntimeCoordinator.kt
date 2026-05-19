package org.openlist.encrypt.android.service

import kotlinx.coroutines.delay

class RuntimeCoordinator(
    private val processController: RuntimeProcessController,
    private val maxRecoverAttempts: Int = 4
) {
    @Volatile
    private var state: RuntimeState = RuntimeState.Idle
    @Volatile
    private var lastError: String = ""

    suspend fun startAll() {
        state = RuntimeState.Starting
        runCatching {
            startSequence()
        }.onFailure {
            state = RuntimeState.Degraded
            lastError = it.message ?: "start sequence failed"
            recover()
        }
    }

    suspend fun stopAll() {
        runCatching { processController.stopOpenList() }
        state = RuntimeState.Stopped
    }

    fun currentState(): RuntimeState = state
    fun lastErrorDetail(): String = lastError

    private suspend fun startSequence() {
        processController.startOpenList()
        if (!waitHealthy { processController.checkOpenListHealth() }) {
            throw IllegalStateException("openlist health check timeout")
        }

        state = RuntimeState.Running
        lastError = ""
    }

    private suspend fun recover() {
        var backoffMs = 500L
        repeat(maxRecoverAttempts) {
            state = RuntimeState.Recovering
            delay(backoffMs)
            runCatching {
                startSequence()
                return
            }.onFailure { e -> lastError = e.message ?: "recover attempt failed" }
            backoffMs = (backoffMs * 2).coerceAtMost(20_000L)
        }
        state = RuntimeState.Degraded
    }

    private suspend fun waitHealthy(check: suspend () -> Boolean): Boolean {
        repeat(10) {
            if (check()) return true
            delay(200)
        }
        return false
    }
}
