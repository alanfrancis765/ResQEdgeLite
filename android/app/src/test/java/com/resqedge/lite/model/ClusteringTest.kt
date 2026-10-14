package com.resqedge.lite.model

import com.resqedge.lite.data.DemoGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusteringTest {
    private val lat0 = 9.5
    private val lon0 = 76.4
    private fun north(metres: Double) = lat0 + metres / 111_000.0

    private fun pkt(id: Long, latitude: Double, headcount: Int = 1, flags: Int = 0) = SosPacket(
        packetId = id, senderId = 1, lat = latitude.toFloat(), lon = lon0.toFloat(),
        accuracyM = 10, headcount = headcount, medicalFlags = flags, waterLevel = 0,
        urgency = 1, timestamp = 0, ttl = 7, hopCount = 0
    )
    private fun sr(p: SosPacket, score: Double = 10.0) = ScoredReport(p, score)

    @Test fun emptyInputGivesNoClusters() =
        assertEquals(0, Clustering.cluster(emptyList()).size)

    @Test fun nearbyReportsFormOneCluster() {
        val c = Clustering.cluster(listOf(sr(pkt(1, north(0.0))), sr(pkt(2, north(30.0)))))
        assertEquals(1, c.size)
        assertEquals(2, c[0].reportCount)
    }

    @Test fun farReportsFormSeparateClusters() {
        val c = Clustering.cluster(listOf(sr(pkt(1, north(0.0))), sr(pkt(2, north(500.0)))))
        assertEquals(2, c.size)
    }

    @Test fun membershipIsMeasuredFromTheSeedNotByChaining() {
        // A is the seed. B is 60 m from A. C is 120 m from A but only 60 m from B.
        val a = sr(pkt(1, north(0.0)), 50.0)
        val b = sr(pkt(2, north(60.0)), 20.0)
        val c = sr(pkt(3, north(120.0)), 10.0)
        val clusters = Clustering.cluster(listOf(c, b, a))
        assertEquals(2, clusters.size)
        assertEquals(setOf(1L, 2L), clusters[0].members.map { it.packet.packetId }.toSet())
        assertEquals(setOf(3L), clusters[1].members.map { it.packet.packetId }.toSet())
    }

    @Test fun totalsAddUp() {
        val c = Clustering.cluster(listOf(
            sr(pkt(1, north(0.0), headcount = 3, flags = SosPacket.FLAG_INJURED)),
            sr(pkt(2, north(20.0), headcount = 4))
        ))
        assertEquals(1, c.size)
        assertEquals(7, c[0].totalPeople)
        assertEquals(1, c[0].injuredCount)
    }

    @Test fun clustersAreOrderedByTopScore() {
        val c = Clustering.cluster(listOf(
            sr(pkt(1, north(0.0)), 10.0),
            sr(pkt(2, north(1000.0)), 90.0)
        ))
        assertEquals(90.0, c[0].topScore, 0.001)
        assertEquals(10.0, c[1].topScore, 0.001)
    }

    @Test fun radiusCanBeChanged() {
        val pair = listOf(sr(pkt(1, north(0.0))), sr(pkt(2, north(100.0))))
        assertEquals(2, Clustering.cluster(pair, radiusM = 75.0).size)
        assertEquals(1, Clustering.cluster(pair, radiusM = 150.0).size)
    }

    @Test fun centerIsTheMeanOfTheMembers() {
        val c = Clustering.cluster(listOf(sr(pkt(1, north(0.0))), sr(pkt(2, north(40.0)))))
        assertEquals(north(20.0), c[0].centerLat, 0.00005)
    }

    private val demo = DemoGenerator.generate(1_800_000_000L)
        .map { ScoredReport(it, Triage.score(it, 0)) }

    @Test fun everyDemoReportIsAssignedExactlyOnce() {
        val ids = Clustering.cluster(demo).flatMap { c -> c.members.map { it.packet.packetId } }
        assertEquals(200, ids.size)
        assertEquals(200, ids.toSet().size)
    }

    @Test fun demoDataGroupsIntoFewerClustersThanReports() {
        val c = Clustering.cluster(demo)
        assertTrue("clusters: ${c.size}", c.size < 100)
        assertTrue("largest: ${c.maxOf { it.reportCount }}", c.maxOf { it.reportCount } >= 5)
    }
}
