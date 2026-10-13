package com.resqedge.lite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {
    /** Returns -1 when a row with the same packetId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: PacketEntity): Long

    @Query("SELECT COUNT(*) FROM packets")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM packets")
    fun observeAll(): Flow<List<PacketEntity>>

    @Query("SELECT * FROM packets WHERE ttl > 0")
    suspend fun forwardable(): List<PacketEntity>

    @Query("DELETE FROM packets WHERE receivedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM packets")
    suspend fun deleteAll(): Int
}
