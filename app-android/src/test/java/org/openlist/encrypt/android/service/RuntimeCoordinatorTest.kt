package org.openlist.encrypt.android.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeCoordinatorTest {
    @Test
    fun startAndStopTransitions() = runTest {
        val c = FakeController(openListHealthy = true, gatewayHealthy = true)
        val coordinator = RuntimeCoordinator(c, maxRecoverAttempts = 1)

        coordinator.startAll()
        assertEquals(RuntimeState.Running, coordinator.currentState())

        coordinator.stopAll()
        assertEquals(RuntimeState.Stopped, coordinator.currentState())
        assertEquals(listOf("startGateway", "startOpenList", "stopOpenList", "stopGateway"), c.calls)
    }

    @Test
    fun entersDegradedWhenRecoveryExhausted() = runTest {
        val c = FakeController(openListHealthy = false, gatewayHealthy = false)
        val coordinator = RuntimeCoordinator(c, maxRecoverAttempts = 1)

        coordinator.startAll()
        assertEquals(RuntimeState.Degraded, coordinator.currentState())
    }
}

private class FakeController(
    private val openListHealthy: Boolean,
    private val gatewayHealthy: Boolean
) : RuntimeProcessController {
    val calls = mutableListOf<String>()

    override suspend fun startOpenList() {
        calls += "startOpenList"
    }

    override suspend fun stopOpenList() {
        calls += "stopOpenList"
    }

    override suspend fun startGateway() {
        calls += "startGateway"
    }

    override suspend fun stopGateway() {
        calls += "stopGateway"
    }

    override suspend fun checkOpenListHealth(): Boolean = openListHealthy

    override suspend fun checkGatewayHealth(): Boolean = gatewayHealthy
}
