package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketBuilderTest {
    @Test fun urgencyBaseline() = assertEquals(1, PacketBuilder.urgencyFor(0, 0))

    @Test fun injuredRaisesUrgency() =
        assertEquals(3, PacketBuilder.urgencyFor(SosPacket.FLAG_INJURED, 0))

    @Test fun vulnerableAddsOne() =
        assertEquals(2, PacketBuilder.urgencyFor(SosPacket.FLAG_ELDERLY, 0))

    @Test fun deepWaterAddsOne() =
        assertEquals(2, PacketBuilder.urgencyFor(0, 3))

    @Test fun urgencyIsCappedAtFive() =
        assertEquals(
            5,
            PacketBuilder.urgencyFor(SosPacket.FLAG_INJURED or SosPacket.FLAG_INFANT, 4)
        )

    @Test fun builtPacketIsValidAndRoundTrips() {
        val p = PacketBuilder.build(
            headcount = 4, medicalFlags = SosPacket.FLAG_INFANT, waterLevel = 3,
            lat = 10.0f, lon = 76.0f, accuracyM = 15, senderId = 7
        )
        assertTrue(p.isValid())
        assertEquals(7, p.ttl)
        assertEquals(0, p.hopCount)
        assertEquals(p, SosPacket.fromBytes(p.toBytes()))
        assertNotNull(SosPacket.fromBytes(p.toBytes()))
    }

    @Test fun headcountIsClamped() {
        val p = PacketBuilder.build(0, 0, 0, 10f, 76f, 0, 1)
        assertEquals(1, p.headcount)
    }
}
