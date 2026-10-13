package com.resqedge.lite.data

import android.content.Context
import com.resqedge.lite.model.SosPacket

data class DemoLoadResult(val stored: Int, val duplicates: Int, val skipped: Int)

object DemoData {
    private const val ASSET = "synthetic_sos_200.csv"

    /** CSV columns: packet_id,sender_id,lat,lon,accuracy_m,headcount,medical_flags,
     *  water_level,urgency,timestamp,ttl,hop_count,area */
    fun parseCsv(lines: List<String>, nowSec: Long): List<SosPacket> {
        val parsed = lines.drop(1).mapNotNull { parseLine(it) }.filter { it.isValid() }
        if (parsed.isEmpty()) return parsed
        // Shift timestamps so the newest report is 5 minutes old, whenever it is loaded.
        val shift = (nowSec - 300) - parsed.maxOf { it.timestamp.toLong() }
        return parsed.map { it.copy(timestamp = (it.timestamp + shift).toInt()) }
    }

    private fun parseLine(line: String): SosPacket? {
        val c = line.split(",")
        if (c.size < 12) return null
        return try {
            SosPacket(
                packetId = c[0].trim().toLong(),
                senderId = c[1].trim().toInt(),
                lat = c[2].trim().toFloat(),
                lon = c[3].trim().toFloat(),
                accuracyM = c[4].trim().toInt(),
                headcount = c[5].trim().toInt(),
                medicalFlags = c[6].trim().toInt(),
                waterLevel = c[7].trim().toInt(),
                urgency = c[8].trim().toInt(),
                timestamp = c[9].trim().toInt(),
                ttl = c[10].trim().toInt(),
                hopCount = c[11].trim().toInt()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Pushes every simulated row through the same ingest() path real packets use. */
    suspend fun load(
        context: Context,
        ingestor: Ingestor,
        nowSec: Long = System.currentTimeMillis() / 1000
    ): DemoLoadResult {
        val lines = try {
            context.assets.open(ASSET).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            return DemoLoadResult(0, 0, 0)
        }
        val packets = parseCsv(lines, nowSec)
        var stored = 0
        var duplicates = 0
        for (p in packets) {
            when (ingestor.ingest(p.toBytes())) {
                is IngestResult.Stored -> stored++
                IngestResult.Duplicate -> duplicates++
                IngestResult.Malformed -> {}
            }
        }
        val rows = (lines.size - 1).coerceAtLeast(0)
        return DemoLoadResult(stored, duplicates, rows - packets.size)
    }
}
