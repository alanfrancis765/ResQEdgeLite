package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec: injured +40, infant or elderly +15 (once), medication or pregnant +10 (once),
 *  +2 per person (counted up to 20), +10 per water level, +0.5 per minute waiting (capped at 180). */
class TriageSpecTest {
    private val base = SosPacket(
        packetId = 1, senderId = 1, lat = 10f, lon = 76f, accuracyM = 10,
        headcount = 3, medicalFlags = 0, waterLevel = 0, urgency = 1,
        timestamp = 0, ttl = 7, hopCount = 0
    )
    private fun score(p: SosPacket, wait: Long = 0) = Triage.score(p, wait)

    @Test fun baselineIsHeadcountOnly() = assertEquals(6.0, score(base), 0.001)
    @Test fun injuredAddsForty() =
        assertEquals(46.0, score(base.copy(medicalFlags = SosPacket.FLAG_INJURED)), 0.001)
    @Test fun infantAndElderlyCountOnce() = assertEquals(
        21.0,
        score(base.copy(medicalFlags = SosPacket.FLAG_INFANT or SosPacket.FLAG_ELDERLY)), 0.001
    )
    @Test fun medicationAddsTen() =
        assertEquals(16.0, score(base.copy(medicalFlags = SosPacket.FLAG_MEDICATION)), 0.001)
    @Test fun waterAddsTenPerLevel() =
        assertEquals(36.0, score(base.copy(waterLevel = 3)), 0.001)
    @Test fun waitingAddsHalfPointPerMinute() = assertEquals(16.0, score(base, 20), 0.001)
    @Test fun waitingBonusIsCapped() =
        assertEquals(score(base, 180), score(base, 100_000), 0.001)
    @Test fun headcountIsCapped() =
        assertEquals(40.0, score(base.copy(headcount = 200)), 0.001)

    @Test fun injuredInfantOutranksHealthyCrowd() {
        val a = base.copy(headcount = 2, waterLevel = 1,
            medicalFlags = SosPacket.FLAG_INJURED or SosPacket.FLAG_INFANT)
        val b = base.copy(headcount = 10)
        assertTrue(score(a) > score(b, 60))
    }

    private val nowMs = 1_800_000_000_000L
    private val nowSec = nowMs / 1000

    @Test fun plausibleSenderTimestampIsUsed() =
        assertEquals(10L, Triage.waitMinutes(nowSec - 600, nowMs - 3_600_000, nowMs))
    @Test fun twoYearOldTimestampFallsBackToReceiveTime() =
        assertEquals(2L, Triage.waitMinutes(nowSec - 63_000_000, nowMs - 120_000, nowMs))
    @Test fun slightlyFutureTimestampGivesZeroWait() =
        assertEquals(0L, Triage.waitMinutes(nowSec + 60, nowMs - 600_000, nowMs))
    @Test fun farFutureTimestampFallsBackToReceiveTime() =
        assertEquals(5L, Triage.waitMinutes(nowSec + 10_000, nowMs - 300_000, nowMs))
}
