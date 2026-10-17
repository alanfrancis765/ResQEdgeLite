package com.resqedge.lite.ui.capture

import android.location.Location
import android.os.SystemClock

object GpsPolicy {
    fun chooseBestFix(
        candidates: List<Location>,
        nowNs: Long = SystemClock.elapsedRealtimeNanos(),
        maxAgeNs: Long = 900_000_000_000L // 15 minutes in ns
    ): Location? {
        if (candidates.isEmpty()) return null

        val valid = candidates.filter { loc ->
            val locNs = loc.elapsedRealtimeNanos
            val ageNs = if (locNs > 0) nowNs - locNs else 0L
            ageNs in 0..maxAgeNs
        }

        if (valid.isEmpty()) return null

        return valid.sortedWith(Comparator { a, b ->
            val timeA = a.elapsedRealtimeNanos
            val timeB = b.elapsedRealtimeNanos
            val ageDiff = timeB - timeA

            if (kotlin.math.abs(ageDiff) < 5_000_000_000L) {
                val accA = if (a.hasAccuracy()) a.accuracy else Float.MAX_VALUE
                val accB = if (b.hasAccuracy()) b.accuracy else Float.MAX_VALUE
                accA.compareTo(accB) // smaller accuracy first
            } else {
                timeB.compareTo(timeA) // larger time (newer) first
            }
        }).firstOrNull()
    }
}
