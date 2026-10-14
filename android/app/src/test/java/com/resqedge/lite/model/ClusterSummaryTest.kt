package com.resqedge.lite.model

import com.resqedge.lite.data.DemoGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterSummaryTest {
    private fun pkt(id: Long, headcount: Int, flags: Int, water: Int) = SosPacket(
        packetId = id, senderId = 1, lat = 9.5f, lon = 76.4f, accuracyM = 10,
        headcount = headcount, medicalFlags = flags, waterLevel = water, urgency = 1,
        timestamp = 0, ttl = 7, hopCount = 0
    )
    private fun cluster(vararg p: SosPacket) =
        Cluster(p.map { ScoredReport(it, 10.0) }, 9.5, 76.4)

    @Test fun headlineSingular() =
        assertEquals("1 report, 3 people", cluster(pkt(1, 3, 0, 0)).headline())

    @Test fun headlineWithOneInjuredCase() = assertEquals(
        "2 reports, 7 people, 1 injured case",
        cluster(pkt(1, 3, SosPacket.FLAG_INJURED, 0), pkt(2, 4, 0, 0)).headline()
    )

    @Test fun headlineWithTwoInjuredCases() = assertEquals(
        "2 reports, 2 people, 2 injured cases",
        cluster(
            pkt(1, 1, SosPacket.FLAG_INJURED, 0), pkt(2, 1, SosPacket.FLAG_INJURED, 0)
        ).headline()
    )

    @Test fun deepestWaterIsTheMaximum() =
        assertEquals(3, cluster(pkt(1, 1, 0, 1), pkt(2, 1, 0, 3)).deepestWater)

    @Test fun needsSummaryListsFlags() = assertEquals(
        "elderly x2, infant x1",
        cluster(
            pkt(1, 1, SosPacket.FLAG_ELDERLY, 0),
            pkt(2, 1, SosPacket.FLAG_ELDERLY or SosPacket.FLAG_INFANT, 0)
        ).needsSummary()
    )

    @Test fun needsSummaryWhenNone() =
        assertEquals("no other medical flags", cluster(pkt(1, 1, 0, 0)).needsSummary())

    @Test fun waterNameHandlesBadValue() = assertEquals("?", waterName(9))

    @Test fun demoClustersCoverEveryPersonExactlyOnce() {
        val reports = DemoGenerator.generate(1_800_000_000L)
            .map { ScoredReport(it, Triage.score(it, 0)) }
        val clusters = Clustering.cluster(reports)
        assertEquals(
            reports.sumOf { it.packet.headcount },
            clusters.sumOf { it.totalPeople }
        )
    }
}
