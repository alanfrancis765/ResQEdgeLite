package com.resqedge.lite.mesh

import com.resqedge.lite.model.SosPacket
import org.junit.Assert.*
import org.junit.Test

class PingTest {
    @Test
    fun testPingRoundTrip() {
        val nonces = listOf(0L, 123456789L, -1L, Long.MAX_VALUE)
        for (nonce in nonces) {
            val bytes = Ping.encodePing(nonce)
            val msg = Ping.parse(bytes)
            assertTrue(msg is PingMessage.Ping)
            assertEquals(nonce, (msg as PingMessage.Ping).nonce)
        }
    }

    @Test
    fun testPongRoundTrip() {
        val nonces = listOf(0L, 987654321L, -1L, Long.MIN_VALUE)
        for (nonce in nonces) {
            val bytes = Ping.encodePong(nonce)
            val msg = Ping.parse(bytes)
            assertTrue(msg is PingMessage.Pong)
            assertEquals(nonce, (msg as PingMessage.Pong).nonce)
        }
    }

    @Test
    fun testParseReturnsNullForSosPacket() {
        val sos = SosPacket(
            packetId = 1L, senderId = 1, lat = 0f, lon = 0f,
            accuracyM = 0, headcount = 0, medicalFlags = 0,
            waterLevel = 0, urgency = 1, timestamp = 0, ttl = 7, hopCount = 0
        ).toBytes()
        assertNull(Ping.parse(sos))
    }

    @Test
    fun testParseReturnsNullForWrongPrefix() {
        val badBytes = "BADP12345678".toByteArray(Charsets.US_ASCII)
        assertNull(Ping.parse(badBytes))
    }

    @Test
    fun testParseReturnsNullForEmptyInput() {
        assertNull(Ping.parse(ByteArray(0)))
        assertNull(Ping.parse(null))
        assertNull(Ping.parse(ByteArray(5)))
    }
}
