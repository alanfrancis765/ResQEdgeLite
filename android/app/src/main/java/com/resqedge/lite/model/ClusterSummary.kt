package com.resqedge.lite.model

fun Cluster.countWith(mask: Int): Int =
    members.count { (it.packet.medicalFlags and mask) != 0 }

val Cluster.deepestWater: Int get() = members.maxOf { it.packet.waterLevel }

private val WATER_NAMES = listOf("ankle", "knee", "waist", "chest", "roof")
fun waterName(level: Int): String = WATER_NAMES.getOrElse(level) { "?" }

/** Counts are numbers of REPORTS that mention the flag, not numbers of people. */
fun Cluster.headline(): String {
    val reports = if (reportCount == 1) "1 report" else "$reportCount reports"
    val injured = when (injuredCount) {
        0 -> ""
        1 -> ", 1 injured case"
        else -> ", $injuredCount injured cases"
    }
    return "$reports, $totalPeople people$injured"
}

fun Cluster.needsSummary(): String {
    val parts = buildList {
        countWith(SosPacket.FLAG_ELDERLY).takeIf { it > 0 }?.let { add("elderly x$it") }
        countWith(SosPacket.FLAG_INFANT).takeIf { it > 0 }?.let { add("infant x$it") }
        countWith(SosPacket.FLAG_MEDICATION).takeIf { it > 0 }?.let { add("medication x$it") }
        countWith(SosPacket.FLAG_PREGNANT).takeIf { it > 0 }?.let { add("pregnant x$it") }
    }
    return if (parts.isEmpty()) "no other medical flags" else parts.joinToString(", ")
}
