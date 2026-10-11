package com.resqedge.lite.model

/** Temporary model for Phase 0. Replaced later by data decoded from SosPacket. */
data class SimulatedReport(
    val packetId: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Int,
    val headcount: Int,
    val medicalFlags: Int,
    val waterLevel: Int,
    val urgency: Int,
    val timestamp: Long,
    val hopCount: Int,
    val area: String
) {
    companion object {
        /** CSV columns: packet_id,sender_id,lat,lon,accuracy_m,headcount,
         *  medical_flags,water_level,urgency,timestamp,ttl,hop_count,area */
        fun fromCsvLine(line: String): SimulatedReport? {
            val c = line.split(",")
            if (c.size < 12) return null
            return try {
                SimulatedReport(
                    packetId = c[0].trim().toLong(),
                    lat = c[2].trim().toDouble(),
                    lon = c[3].trim().toDouble(),
                    accuracyM = c[4].trim().toInt(),
                    headcount = c[5].trim().toInt(),
                    medicalFlags = c[6].trim().toInt(),
                    waterLevel = c[7].trim().toInt(),
                    urgency = c[8].trim().toInt(),
                    timestamp = c[9].trim().toLong(),
                    hopCount = c[11].trim().toInt(),
                    area = if (c.size >= 13) c[12].trim() else "Zone A"
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
