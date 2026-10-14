package com.resqedge.lite.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ScoredReport(val packet: SosPacket, val score: Double)

data class Cluster(
    val members: List<ScoredReport>,
    val centerLat: Double,
    val centerLon: Double
) {
    val reportCount: Int get() = members.size
    val totalPeople: Int get() = members.sumOf { it.packet.headcount }
    val injuredCount: Int
        get() = members.count { (it.packet.medicalFlags and SosPacket.FLAG_INJURED) != 0 }
    val topScore: Double get() = members.maxOf { it.score }
    val totalScore: Double get() = members.sumOf { it.score }
}

object Clustering {
    const val DEFAULT_RADIUS_M = 75.0

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    /**
     * Leader clustering. The most urgent unassigned report becomes a seed, and every unassigned
     * report within radiusM of the SEED joins that cluster. Each report lands in exactly one
     * cluster. Clusters come out ordered by their most urgent member (the seed), highest first.
     */
    fun cluster(
        reports: List<ScoredReport>,
        radiusM: Double = DEFAULT_RADIUS_M
    ): List<Cluster> {
        val remaining = reports
            .sortedWith(compareByDescending<ScoredReport> { it.score }.thenBy { it.packet.packetId })
            .toMutableList()
        val out = ArrayList<Cluster>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val seedLat = seed.packet.lat.toDouble()
            val seedLon = seed.packet.lon.toDouble()
            val members = mutableListOf(seed)
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val r = iter.next()
                val d = distanceMetres(
                    seedLat, seedLon, r.packet.lat.toDouble(), r.packet.lon.toDouble()
                )
                if (d <= radiusM) {
                    members += r
                    iter.remove()
                }
            }
            out += Cluster(
                members = members,
                centerLat = members.map { it.packet.lat.toDouble() }.average(),
                centerLon = members.map { it.packet.lon.toDouble() }.average()
            )
        }
        return out
    }
}
