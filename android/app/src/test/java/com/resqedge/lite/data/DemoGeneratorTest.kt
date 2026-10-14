package com.resqedge.lite.data

import com.resqedge.lite.model.SosPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

class DemoGeneratorTest {
    private val now = 1_800_000_000L
    private val packets = DemoGenerator.generate(now)

    private fun metres(a: SosPacket, b: SosPacket): Double {
        val dLat = (a.lat.toDouble() - b.lat.toDouble()) * 111_000.0
        val dLon = (a.lon.toDouble() - b.lon.toDouble()) * 111_000.0 *
            cos(Math.toRadians(a.lat.toDouble()))
        return hypot(dLat, dLon)
    }

    @Test fun producesTwoHundredPackets() = assertEquals(200, packets.size)

    @Test fun everyPacketIsValidAndRoundTrips() {
        packets.forEach {
            assertTrue(it.isValid())
            assertEquals(it, SosPacket.fromBytes(it.toBytes()))
        }
    }

    @Test fun idsAreUnique() = assertEquals(200, packets.map { it.packetId }.toSet().size)

    @Test fun sameSeedGivesSameIdsWhateverTheClock() = assertEquals(
        packets.map { it.packetId },
        DemoGenerator.generate(now + 999).map { it.packetId }
    )

    @Test fun waterLevelsStayInProtocolRangeAndUseAllLevels() {
        assertTrue(packets.all { it.waterLevel in 0..4 })
        assertEquals(5, packets.map { it.waterLevel }.toSet().size)
    }

    @Test fun locationsAreInKerala() = assertTrue(
        packets.all { it.lat in 9.0f..10.6f && it.lon in 76.0f..77.0f }
    )

    @Test fun timestampsAreRecent() = assertTrue(
        packets.all { it.timestamp.toLong() in (now - 300 - 6 * 3600)..(now - 300) }
    )

    @Test fun hopsAndTtlAreConsistent() = assertTrue(
        packets.all { it.hopCount in 0..3 && it.ttl + it.hopCount == 7 }
    )

    @Test fun mostReportsHaveANeighbourWithin100m() {
        val withNeighbour = packets.count { p ->
            packets.any { q -> q !== p && metres(p, q) < 100.0 }
        }
        assertTrue("only $withNeighbour of 200 have a neighbour", withNeighbour >= 140)
    }
}
