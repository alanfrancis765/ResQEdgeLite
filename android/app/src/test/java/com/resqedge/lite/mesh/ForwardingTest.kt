package com.resqedge.lite.mesh

import com.resqedge.lite.model.SosPacket
import org.junit.Assert.*
import org.junit.Test

class ForwardingTest {
    private val packet = SosPacket(
        packetId = 100L, senderId = 1,
        lat = 10f, lon = 76f, accuracyM = 5,
        headcount = 2, medicalFlags = 0, waterLevel = 1,
        urgency = 3, timestamp = 1000, ttl = 7, hopCount = 0
    )

    @Test
    fun testForwardCopy() {
        val f1 = Forwarding.forwardCopy(packet)
        assertNotNull(f1)
        assertEquals(6, f1!!.ttl)
        assertEquals(1, f1.hopCount)
        assertEquals(packet.packetId, f1.packetId)
        assertEquals(packet.senderId, f1.senderId)
        assertTrue(f1.isValid())
    }

    @Test
    fun testTtlZeroReturnsNull() {
        val zeroTtl = packet.copy(ttl = 0)
        assertNull(Forwarding.forwardCopy(zeroTtl))
    }

    @Test
    fun testExactSevenForwards() {
        var current: SosPacket? = packet
        var count = 0
        while (current != null) {
            current = Forwarding.forwardCopy(current)
            if (current != null) {
                count++
                assertTrue(current.isValid())
            }
        }
        assertEquals(7, count)
    }

    @Test
    fun testSyncOrder() {
        val p1 = packet.copy(packetId = 1, urgency = 2, timestamp = 100)
        val p2 = packet.copy(packetId = 2, urgency = 3, timestamp = 200)
        val p3 = packet.copy(packetId = 3, urgency = 3, timestamp = 100)
        val p4 = packet.copy(packetId = 4, urgency = 3, timestamp = 200)

        val sorted = Forwarding.syncOrder(listOf(p1, p2, p3, p4))
        assertEquals(listOf(p2, p4, p3, p1), sorted)
    }
}
