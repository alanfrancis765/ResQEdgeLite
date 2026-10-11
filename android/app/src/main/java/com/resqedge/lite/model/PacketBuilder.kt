package com.resqedge.lite.model

import kotlin.random.Random

object PacketBuilder {

    /** Same rule the synthetic data generator uses. */
    fun urgencyFor(medicalFlags: Int, waterLevel: Int): Int {
        var u = 1
        if ((medicalFlags and SosPacket.FLAG_INJURED) != 0) u += 2
        val vulnerable = SosPacket.FLAG_ELDERLY or SosPacket.FLAG_INFANT or SosPacket.FLAG_MEDICATION
        if ((medicalFlags and vulnerable) != 0) u += 1
        if (waterLevel >= 3) u += 1
        return u.coerceAtMost(5)
    }

    fun build(
        headcount: Int,
        medicalFlags: Int,
        waterLevel: Int,
        lat: Float,
        lon: Float,
        accuracyM: Int,
        senderId: Int,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): SosPacket = SosPacket(
        packetId = Random.nextLong(0, Long.MAX_VALUE),
        senderId = senderId,
        lat = lat,
        lon = lon,
        accuracyM = accuracyM.coerceIn(0, 255),
        headcount = headcount.coerceIn(1, 255),
        medicalFlags = medicalFlags and 0xFF,
        waterLevel = waterLevel.coerceIn(0, 4),
        urgency = urgencyFor(medicalFlags, waterLevel),
        timestamp = nowSeconds.toInt(),
        ttl = 7,
        hopCount = 0
    )
}
