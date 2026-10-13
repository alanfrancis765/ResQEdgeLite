package com.resqedge.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.resqedge.lite.model.SosPacket

@Entity(tableName = "packets")
data class PacketEntity(
    @PrimaryKey val packetId: Long,      // primary key = duplicate detection
    val senderId: Int,
    val lat: Float,
    val lon: Float,
    val accuracyM: Int,
    val headcount: Int,
    val medicalFlags: Int,
    val waterLevel: Int,
    val urgency: Int,
    val timestamp: Int,
    val ttl: Int,
    val hopCount: Int,
    val receivedAtMs: Long
) {
    fun toPacket() = SosPacket(
        packetId, senderId, lat, lon, accuracyM, headcount,
        medicalFlags, waterLevel, urgency, timestamp, ttl, hopCount
    )

    companion object {
        fun from(p: SosPacket, receivedAtMs: Long) = PacketEntity(
            p.packetId, p.senderId, p.lat, p.lon, p.accuracyM, p.headcount,
            p.medicalFlags, p.waterLevel, p.urgency, p.timestamp, p.ttl, p.hopCount,
            receivedAtMs
        )
    }
}
