package com.resqedge.lite.data

import com.resqedge.lite.model.SosPacket
import kotlinx.coroutines.flow.Flow

interface PacketStore {
    /** True if the packet was new and stored, false if its ID was already known. */
    suspend fun insertIfNew(p: SosPacket, receivedAtMs: Long): Boolean
    fun observeCount(): Flow<Int>
    suspend fun forwardable(): List<SosPacket>
    suspend fun deleteOlderThan(cutoffMs: Long): Int
    suspend fun clear()
}

class RoomPacketStore(private val dao: PacketDao) : PacketStore {
    override suspend fun insertIfNew(p: SosPacket, receivedAtMs: Long) =
        dao.insert(PacketEntity.from(p, receivedAtMs)) != -1L

    override fun observeCount() = dao.observeCount()
    override suspend fun forwardable() = dao.forwardable().map { it.toPacket() }
    override suspend fun deleteOlderThan(cutoffMs: Long) = dao.deleteOlderThan(cutoffMs)
    override suspend fun clear() {
        dao.deleteAll()
    }
}
