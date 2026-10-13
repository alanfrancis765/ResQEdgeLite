package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TriageTest {
    @Test
    fun testScoreCalculation() {
        val packet = SosPacket(
            packetId = 1L,
            senderId = 100,
            lat = 10.0f,
            lon = 76.0f,
            accuracyM = 10,
            headcount = 4,
            medicalFlags = SosPacket.FLAG_INJURED or SosPacket.FLAG_ELDERLY,
            waterLevel = 3,
            urgency = 5,
            timestamp = 1000000,
            ttl = 7,
            hopCount = 0
        )
        // injured (40) + elderly (15) + headcount 4 * 2 (8) + waterLevel 3 * 10 (30) + wait 60 mins * 0.5 (30) = 123.0
        val score = Triage.score(packet, waitMinutes = 60)
        assertEquals(123.0, score, 0.001)
    }

    @Test
    fun testWaitMinutesPlausibleClock() {
        val nowMs = 1000_000_000L
        val timestampSec = (nowMs / 1000) - 1800 // 30 minutes ago
        val receivedAtMs = nowMs - 10000
        val wait = Triage.waitMinutes(timestampSec, receivedAtMs, nowMs)
        assertEquals(30L, wait)
    }

    @Test
    fun testWaitMinutesImplausibleClockFallback() {
        val nowMs = 1000_000_000L
        val timestampSec = (nowMs / 1000) + 10000 // future (implausible)
        val receivedAtMs = nowMs - 120000 // 2 minutes ago received
        val wait = Triage.waitMinutes(timestampSec, receivedAtMs, nowMs)
        assertEquals(2L, wait)
    }
}
