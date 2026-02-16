package org.openlist.encrypt.android.service

import kotlinx.coroutines.delay

class RuntimeCoordinator(
    private val processController: RuntimeProcessController,
    private val maxRecoverAttempts: Int = 4
) {
    @Volatile
    private var state: RuntimeState = RuntimeState.Idle

    suspend fun startAll() {
        state = RuntimeState.Starting
        runCatching {
            startSequence()
        }.onFailure {
            state = RuntimeState.Degraded
            recover()
        }
    }

    suspend fun stopAll() {
        runCatching { processController.stopOpenList() }
        runCatching { processController.stopGateway() }
        state = RuntimeState.Stopped
    }

    fun currentState(): RuntimeState = state

    private suspend fun startSequence() {
        processController.startGateway()
        if (!waitHealthy { processController.checkGatewayHealth() }) {
            throw IllegalStateException("gateway health check timeout")
        }

        processController.startOpenList()
        if (!waitHealthy { processController.checkOpenListHealth() }) {
            throw IllegalStateException("openlist health check timeout")
        }

        state = RuntimeState.Running
    }

    private suspend fun recover() {
        var backoffMs = 500L
        repeat(maxRecoverAttempts) {
            state = RuntimeState.Recovering
            delay(backoffMs)
            runCatching {
                startSequence()
                return
            }
            backoffMs = (backoffMs * 2).coerceAtMost(20_000L)
        }
        state = RuntimeState.Degraded
    }

    private suspend fun waitHealthy(check: suspend () -> Boolean): Boolean {
        repeat(8) {
            if (check()) return true
            delay(250)
        }
        return false
    }
}
