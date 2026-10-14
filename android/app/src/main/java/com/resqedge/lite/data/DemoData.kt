package com.resqedge.lite.data

data class DemoLoadResult(val stored: Int, val duplicates: Int, val skipped: Int)

object DemoData {
    /** Pushes every simulated packet through the same ingest() path real packets use. */
    suspend fun load(
        ingestor: Ingestor,
        nowSec: Long = System.currentTimeMillis() / 1000
    ): DemoLoadResult {
        var stored = 0
        var duplicates = 0
        var skipped = 0
        for (p in DemoGenerator.generate(nowSec)) {
            when (ingestor.ingest(p.toBytes())) {
                is IngestResult.Stored -> stored++
                IngestResult.Duplicate -> duplicates++
                IngestResult.Malformed -> skipped++
            }
        }
        return DemoLoadResult(stored, duplicates, skipped)
    }
}
