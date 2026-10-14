package com.resqedge.lite.model

/** Tunable weights. Set these with disaster-response experts, not by guesswork. */
data class TriageWeights(
    val injured: Double = 40.0,
    val vulnerable: Double = 15.0,        // infant or elderly
    val medicalOther: Double = 10.0,      // medication or pregnant
    val perPerson: Double = 2.0,
    val maxPeopleCounted: Int = 20,
    val perWaterLevel: Double = 10.0,
    val perMinuteWaiting: Double = 0.5,
    val maxWaitMinutes: Long = 60
)

object Triage {
    private fun has(flags: Int, mask: Int) = (flags and mask) != 0

    /** Minutes waiting, safe against wrong sender clocks. */
    fun waitMinutes(timestampSec: Long, receivedAtMs: Long, nowMs: Long): Long {
        val nowSec = nowMs / 1000
        val senderClockPlausible =
            timestampSec in (nowSec - 24 * 3600)..(nowSec + 300)
        val startSec = if (senderClockPlausible) timestampSec else receivedAtMs / 1000
        return ((nowSec - startSec) / 60).coerceIn(0, 24 * 60)
    }

    fun score(p: SosPacket, waitMinutes: Long, w: TriageWeights = TriageWeights()): Double {
        var s = 0.0
        if (has(p.medicalFlags, SosPacket.FLAG_INJURED)) s += w.injured
        if (has(p.medicalFlags, SosPacket.FLAG_INFANT or SosPacket.FLAG_ELDERLY)) s += w.vulnerable
        if (has(p.medicalFlags, SosPacket.FLAG_MEDICATION or SosPacket.FLAG_PREGNANT)) s += w.medicalOther
        s += w.perPerson * p.headcount.coerceAtMost(w.maxPeopleCounted)
        s += w.perWaterLevel * p.waterLevel
        s += w.perMinuteWaiting * waitMinutes.coerceIn(0, w.maxWaitMinutes)
        return s
    }
}
