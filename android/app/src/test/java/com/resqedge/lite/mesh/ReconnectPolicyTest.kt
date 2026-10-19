package com.resqedge.lite.mesh

import org.junit.Assert.*
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun testBackoffSchedule() {
        assertEquals(500L, ReconnectPolicy.delayBeforeAttemptMs(1))
        assertEquals(2000L, ReconnectPolicy.delayBeforeAttemptMs(2))
        assertEquals(5000L, ReconnectPolicy.delayBeforeAttemptMs(3))
        assertEquals(10000L, ReconnectPolicy.delayBeforeAttemptMs(4))
        assertEquals(20000L, ReconnectPolicy.delayBeforeAttemptMs(5))
        assertEquals(40000L, ReconnectPolicy.delayBeforeAttemptMs(6))
        assertEquals(60000L, ReconnectPolicy.delayBeforeAttemptMs(7))

        for (i in 1..10) {
            val delay = ReconnectPolicy.delayBeforeAttemptMs(i)
            assertTrue("Delay for attempt $i should be > 0", delay > 0L)
        }
    }

    @Test
    fun testNonInitiatorExtraDelay() {
        val actionInitiator = ReconnectPolicy.action(
            sinceDisconnectMs = 500L,
            attempt = 1,
            knownEndpointId = "id1",
            endpointStillDiscovered = true,
            iAmInitiator = true
        )
        assertEquals(ReconnectAction.RequestKnownEndpoint, actionInitiator)

        val actionNonInitTooSoon = ReconnectPolicy.action(
            sinceDisconnectMs = 500L,
            attempt = 1,
            knownEndpointId = "id1",
            endpointStillDiscovered = true,
            iAmInitiator = false
        )
        assertEquals(ReconnectAction.Nothing, actionNonInitTooSoon)

        val actionNonInitReady = ReconnectPolicy.action(
            sinceDisconnectMs = 2000L,
            attempt = 1,
            knownEndpointId = "id1",
            endpointStillDiscovered = true,
            iAmInitiator = false
        )
        assertEquals(ReconnectAction.RequestKnownEndpoint, actionNonInitReady)
    }

    @Test
    fun testCycleDiscoveryAtThreeSeconds() {
        val action = ReconnectPolicy.action(
            sinceDisconnectMs = 3000L,
            attempt = 1,
            knownEndpointId = null,
            endpointStillDiscovered = false,
            iAmInitiator = true
        )
        assertEquals(ReconnectAction.CycleDiscovery, action)
    }

    @Test
    fun testDiscoveryCyclingLimits() {
        assertFalse(ReconnectPolicy.canCycleDiscovery(sinceDisconnectMs = 10000L, lastCycleAgoMs = 3000L))
        assertTrue(ReconnectPolicy.canCycleDiscovery(sinceDisconnectMs = 10000L, lastCycleAgoMs = 6000L))

        assertFalse(ReconnectPolicy.canCycleDiscovery(sinceDisconnectMs = 70000L, lastCycleAgoMs = 20000L))
        assertTrue(ReconnectPolicy.canCycleDiscovery(sinceDisconnectMs = 70000L, lastCycleAgoMs = 35000L))
    }

    @Test
    fun testAdvertisingRestartThreshold() {
        assertFalse(ReconnectPolicy.canRestartAdvertising(59999L))
        assertTrue(ReconnectPolicy.canRestartAdvertising(60000L))
        assertTrue(ReconnectPolicy.canRestartAdvertising(70000L))
    }
}
