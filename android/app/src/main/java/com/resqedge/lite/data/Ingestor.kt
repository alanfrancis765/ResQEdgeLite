package com.resqedge.lite.data

import com.resqedge.lite.model.SosPacket

sealed interface IngestResult {
    data class Stored(val packet: SosPacket) : IngestResult
    object Duplicate : IngestResult
    object Malformed : IngestResult
}

class Ingestor(
    private val store: PacketStore,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    /** Every packet, from any source, goes through here. */
    suspend fun ingest(bytes: ByteArray): IngestResult {
        val packet = SosPacket.fromBytes(bytes) ?: return IngestResult.Malformed
        return if (store.insertIfNew(packet, nowMs()))
            IngestResult.Stored(packet)
        else
            IngestResult.Duplicate
    }
}
