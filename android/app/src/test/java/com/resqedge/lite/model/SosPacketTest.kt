package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SosPacketTest {
    private val sample = SosPacket(
        packetId = 123456789L, senderId = 42,
        lat = 10.0159f, lon = 76.3419f, accuracyM = 12,
        headcount = 5,
        medicalFlags = SosPacket.FLAG_INJURED or SosPacket.FLAG_INFANT,
        waterLevel = 3, urgency = 4,
        timestamp = 1_790_000_000, ttl = 7, hopCount = 0
    )

    @Test fun roundTrip() =
        assertEquals(sample, SosPacket.fromBytes(sample.toBytes()))

    @Test fun sizeIsFixed() =
        assertEquals(SosPacket.SIZE, sample.toBytes().size)

    @Test fun rejectsTooShort() =
        assertNull(SosPacket.fromBytes(ByteArray(10)))

    @Test fun rejectsTooLong() =
        assertNull(SosPacket.fromBytes(ByteArray(40)))

    @Test fun rejectsBadVersion() {
        val bad = sample.toBytes().also { it[0] = 99 }
        assertNull(SosPacket.fromBytes(bad))
    }

    @Test fun rejectsOutOfRangeUrgencyOnDecode() {
        // urgency byte is at offset 25
        val bad = sample.toBytes().also { it[25] = 9 }
        assertNull(SosPacket.fromBytes(bad))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToEncodeInvalid() {
        sample.copy(urgency = 9).toBytes()
    }
}
