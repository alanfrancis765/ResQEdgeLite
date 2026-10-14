package com.resqedge.lite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDataTest {
    private val header = "packet_id,sender_id,lat,lon,accuracy_m,headcount,medical_flags," +
        "water_level,urgency,timestamp,ttl,hop_count,area"
    private val now = 1_800_000_000L

    @Test fun parsesAndRebasesTimestamps() {
        val out = DemoData.parseCsv(listOf(
            header,
            "100,1,9.5,76.4,10,3,1,2,3,1700000000,7,0,Kuttanad",
            "101,2,9.6,76.5,12,4,0,1,1,1700000600,6,1,Aluva"
        ), now)
        assertEquals(2, out.size)
        assertEquals(now - 300, out.maxOf { it.timestamp.toLong() })
        assertEquals(600L, out[1].timestamp.toLong() - out[0].timestamp.toLong())
    }

    @Test fun skipsWaterLevelOutOfRange() {
        val out = DemoData.parseCsv(listOf(
            header, "100,1,9.5,76.4,10,3,1,5,3,1700000000,7,0,Zone A"
        ), now)
        assertEquals(0, out.size)
    }

    @Test fun skipsMalformedRows() =
        assertEquals(0, DemoData.parseCsv(listOf(header, "abc,def"), now).size)
}
