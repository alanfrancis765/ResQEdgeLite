package com.resqedge.lite.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SosPacket(
    val packetId: Long,
    val senderId: Int,
    val lat: Float,
    val lon: Float,
    val accuracyM: Int,     // 0..255
    val headcount: Int,     // 0..255
    val medicalFlags: Int,  // bit flags, 0..255
    val waterLevel: Int,    // 0..4 (ankle, knee, waist, chest, roof)
    val urgency: Int,       // 1..5
    val timestamp: Int,     // unix seconds
    val ttl: Int,           // 0..7
    val hopCount: Int       // 0..255
) {
    fun isValid() =
        lat in -90f..90f && lon in -180f..180f &&
        accuracyM in 0..255 && headcount in 0..255 &&
        medicalFlags in 0..255 && waterLevel in 0..4 &&
        urgency in 1..5 && ttl in 0..7 && hopCount in 0..255

    fun toBytes(): ByteArray {
        require(isValid()) { "Invalid packet fields" }
        val b = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN)
        b.put(VERSION.toByte())
        b.putLong(packetId)
        b.putInt(senderId)
        b.putFloat(lat)
        b.putFloat(lon)
        b.put(accuracyM.toByte())
        b.put(headcount.toByte())
        b.put(medicalFlags.toByte())
        b.put(waterLevel.toByte())
        b.put(urgency.toByte())
        b.putInt(timestamp)
        b.put(ttl.toByte())
        b.put(hopCount.toByte())
        return b.array()
    }

    companion object {
        const val VERSION = 1
        const val SIZE = 32

        const val FLAG_INJURED = 1
        const val FLAG_ELDERLY = 2
        const val FLAG_INFANT = 4
        const val FLAG_MEDICATION = 8
        const val FLAG_PREGNANT = 16

        /** Returns null for anything malformed. */
        fun fromBytes(bytes: ByteArray): SosPacket? {
            if (bytes.size != SIZE) return null
            return try {
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                if ((b.get().toInt() and 0xFF) != VERSION) return null
                val p = SosPacket(
                    packetId = b.getLong(),
                    senderId = b.getInt(),
                    lat = b.getFloat(),
                    lon = b.getFloat(),
                    accuracyM = b.get().toInt() and 0xFF,
                    headcount = b.get().toInt() and 0xFF,
                    medicalFlags = b.get().toInt() and 0xFF,
                    waterLevel = b.get().toInt() and 0xFF,
                    urgency = b.get().toInt() and 0xFF,
                    timestamp = b.getInt(),
                    ttl = b.get().toInt() and 0xFF,
                    hopCount = b.get().toInt() and 0xFF
                )
                if (p.isValid()) p else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
