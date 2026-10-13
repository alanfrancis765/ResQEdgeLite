package com.resqedge.lite.data

import com.resqedge.lite.model.SosPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore : PacketStore {
    val rows = linkedMapOf<Long, Pair<SosPacket, Long>>()
    private val count = MutableStateFlow(0)
    override suspend fun insertIfNew(p: SosPacket, receivedAtMs: Long): Boolean {
        if (rows.containsKey(p.packetId)) return false
        rows[p.packetId] = p to receivedAtMs
        count.value = rows.size
        return true
    }
    override fun observeCount(): Flow<Int> = count
    override suspend fun forwardable() = rows.values.map { it.first }.filter { it.ttl > 0 }
    override suspend fun deleteOlderThan(cutoffMs: Long): Int {
        val old = rows.filterValues { it.second < cutoffMs }.keys
        old.forEach { rows.remove(it) }
        count.value = rows.size
        return old.size
    }
    override suspend fun clear() { rows.clear(); count.value = 0 }
}

class IngestorTest {
    private val sample = SosPacket(
        packetId = 77L, senderId = 1, lat = 10f, lon = 76f, accuracyM = 10,
        headcount = 3, medicalFlags = 0, waterLevel = 2, urgency = 3,
        timestamp = 1_790_000_000, ttl = 7, hopCount = 0
    )

    @Test fun storesNewValidPacket() = runBlocking {
        val store = FakeStore()
        val r = Ingestor(store).ingest(sample.toBytes())
        assertTrue(r is IngestResult.Stored)
        assertEquals(1, store.rows.size)
    }

    @Test fun duplicateIsIgnored() = runBlocking {
        val store = FakeStore()
        val ing = Ingestor(store)
        ing.ingest(sample.toBytes())
        assertEquals(IngestResult.Duplicate, ing.ingest(sample.toBytes()))
        assertEquals(1, store.rows.size)
    }

    @Test fun samePacketArrivingByAnotherPathIsDuplicate() = runBlocking {
        // Same packet ID but a different ttl and hop count (it took another route).
        val store = FakeStore()
        val ing = Ingestor(store)
        ing.ingest(sample.toBytes())
        val viaOtherPath = sample.copy(ttl = 4, hopCount = 3).toBytes()
        assertEquals(IngestResult.Duplicate, ing.ingest(viaOtherPath))
        assertEquals(1, store.rows.size)
    }

    @Test fun malformedBytesAreRejected() = runBlocking {
        val store = FakeStore()
        assertEquals(IngestResult.Malformed, Ingestor(store).ingest(ByteArray(5)))
        assertEquals(0, store.rows.size)
    }

    @Test fun outOfRangeFieldIsRejected() = runBlocking {
        val store = FakeStore()
        val bad = sample.toBytes().also { it[25] = 9 }   // urgency byte
        assertEquals(IngestResult.Malformed, Ingestor(store).ingest(bad))
        assertEquals(0, store.rows.size)
    }
}
