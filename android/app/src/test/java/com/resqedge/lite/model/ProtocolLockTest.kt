package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** If one of these fails, the wire protocol changed. Update packet-spec-v1.md deliberately. */
class ProtocolLockTest {
    private val golden =
        "0101020304050607080A0B0C0D41200000429800000A030502046A0000000701"

    private val p = SosPacket(
        packetId = 0x0102030405060708L, senderId = 0x0A0B0C0D,
        lat = 10f, lon = 76f, accuracyM = 10, headcount = 3, medicalFlags = 5,
        waterLevel = 2, urgency = 4, timestamp = 0x6A000000, ttl = 7, hopCount = 1
    )

    @Test fun encodingMatchesGoldenBytes() =
        assertEquals(golden, p.toBytes().joinToString("") { "%02X".format(it) })

    @Test fun decodingGoldenBytesReturnsThePacket() {
        val bytes = golden.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(p, SosPacket.fromBytes(bytes))
    }

    @Test fun sizeAndVersionAreFixed() {
        assertEquals(32, SosPacket.SIZE)
        assertEquals(1, SosPacket.VERSION)
    }

    @Test fun waterLevelFiveIsInvalid() = assertFalse(p.copy(waterLevel = 5).isValid())
    @Test fun waterLevelNegativeIsInvalid() = assertFalse(p.copy(waterLevel = -1).isValid())
    @Test fun urgencyZeroIsInvalid() = assertFalse(p.copy(urgency = 0).isValid())
    @Test fun urgencySixIsInvalid() = assertFalse(p.copy(urgency = 6).isValid())
    @Test fun ttlEightIsInvalid() = assertFalse(p.copy(ttl = 8).isValid())
}
