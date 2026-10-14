package com.resqedge.lite.data

import com.resqedge.lite.model.PacketBuilder
import com.resqedge.lite.model.SosPacket
import java.util.Random

/** Deterministic simulated SOS packets. Coordinates are illustrative, not real incidents. */
object DemoGenerator {
    data class Area(val name: String, val lat: Double, val lon: Double, val weight: Int)

    val AREAS = listOf(
        Area("Kuttanad", 9.40, 76.40, 3),
        Area("Chengannur", 9.32, 76.61, 3),
        Area("Vaikom", 9.75, 76.40, 2),
        Area("Aluva", 10.11, 76.35, 2),
        Area("Chalakudy", 10.31, 76.33, 2),
        Area("Paravur", 10.15, 76.22, 1),
        Area("Ranni", 9.38, 76.78, 1)
    )

    private fun pick(r: Random, weights: List<Int>): Int {
        var x = r.nextInt(weights.sum())
        for ((i, w) in weights.withIndex()) {
            if (x < w) return i
            x -= w
        }
        return weights.lastIndex
    }

    fun generate(nowSec: Long, count: Int = 200, seed: Long = 42L): List<SosPacket> {
        val r = Random(seed)
        val areaWeights = AREAS.map { it.weight }
        val headcountWeights = listOf(8, 14, 14, 12, 10, 8, 6, 4, 3, 2, 1, 1) // headcount 1..12
        val waterWeights = listOf(10, 20, 30, 25, 15)                         // levels 0..4
        val hopWeights = listOf(3, 4, 3, 2)                                   // hops 0..3
        val out = ArrayList<SosPacket>(count)
        val usedIds = HashSet<Long>()
        while (out.size < count) {
            val a = AREAS[pick(r, areaWeights)]
            val sigma = if (r.nextDouble() < 0.85) 0.0004 else 0.01 // about 45 m or 1.1 km
            val lat = a.lat + r.nextGaussian() * sigma
            val lon = a.lon + r.nextGaussian() * sigma
            var flags = 0
            if (r.nextDouble() < 0.12) flags = flags or SosPacket.FLAG_INJURED
            if (r.nextDouble() < 0.20) flags = flags or SosPacket.FLAG_ELDERLY
            if (r.nextDouble() < 0.15) flags = flags or SosPacket.FLAG_INFANT
            if (r.nextDouble() < 0.15) flags = flags or SosPacket.FLAG_MEDICATION
            if (r.nextDouble() < 0.05) flags = flags or SosPacket.FLAG_PREGNANT
            val water = pick(r, waterWeights)
            val hops = pick(r, hopWeights)
            val id = r.nextLong() ushr 1
            if (!usedIds.add(id)) continue
            out += SosPacket(
                packetId = id,
                senderId = r.nextInt(Int.MAX_VALUE),
                lat = lat.toFloat(),
                lon = lon.toFloat(),
                accuracyM = 5 + r.nextInt(56),
                headcount = 1 + pick(r, headcountWeights),
                medicalFlags = flags,
                waterLevel = water,
                urgency = PacketBuilder.urgencyFor(flags, water),
                timestamp = (nowSec - 300 - r.nextInt(6 * 3600)).toInt(),
                ttl = 7 - hops,
                hopCount = hops
            )
        }
        return out
    }
}
