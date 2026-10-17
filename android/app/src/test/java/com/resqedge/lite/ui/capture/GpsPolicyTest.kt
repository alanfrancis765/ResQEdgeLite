package com.resqedge.lite.ui.capture

import android.location.Location
import org.junit.Assert.*
import org.junit.Test

class GpsPolicyTest {

    private class TestLocation(
        provider: String,
        private val realtimeNs: Long,
        private val acc: Float,
        private val lat: Double = 0.0,
        private val lon: Double = 0.0
    ) : Location(provider) {
        override fun getElapsedRealtimeNanos(): Long = realtimeNs
        override fun hasAccuracy(): Boolean = true
        override fun getAccuracy(): Float = acc
        override fun getLatitude(): Double = lat
        override fun getLongitude(): Double = lon
    }

    @Test
    fun testEmptyListReturnsNull() {
        val best = GpsPolicy.chooseBestFix(emptyList(), nowNs = 1000L, maxAgeNs = 10_000L)
        assertNull(best)
    }

    @Test
    fun testRejectsFixesOlderThanLimit() {
        val loc = TestLocation("gps", realtimeNs = 100L, acc = 5f)
        val best = GpsPolicy.chooseBestFix(listOf(loc), nowNs = 1_000_000L, maxAgeNs = 1000L)
        assertNull(best)
    }

    @Test
    fun testPrefersNewerFix() {
        val now = 100_000_000_000L
        val older = TestLocation("gps", realtimeNs = now - 60_000_000_000L, acc = 5f, lat = 1.0, lon = 1.0)
        val newer = TestLocation("gps", realtimeNs = now - 10_000_000_000L, acc = 10f, lat = 2.0, lon = 2.0)
        val best = GpsPolicy.chooseBestFix(listOf(older, newer), nowNs = now, maxAgeNs = 100_000_000_000L)
        assertNotNull(best)
        assertEquals(2.0, best!!.latitude, 0.0001)
    }

    @Test
    fun testPrefersMoreAccurateWhenAgesAreSimilar() {
        val now = 100_000_000_000L
        val lessAccurate = TestLocation("gps", realtimeNs = now - 1_000_000_000L, acc = 50f, lat = 1.0, lon = 1.0)
        val moreAccurate = TestLocation("gps", realtimeNs = now - 1_500_000_000L, acc = 5f, lat = 2.0, lon = 2.0)
        val best = GpsPolicy.chooseBestFix(listOf(lessAccurate, moreAccurate), nowNs = now, maxAgeNs = 100_000_000_000L)
        assertNotNull(best)
        assertEquals(2.0, best!!.latitude, 0.0001)
    }
}
