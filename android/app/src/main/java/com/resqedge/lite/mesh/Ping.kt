package com.resqedge.lite.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface PingMessage {
    data class Ping(val nonce: Long) : PingMessage
    data class Pong(val nonce: Long) : PingMessage
}

object Ping {
    private val PING_PREFIX = "PING".toByteArray(Charsets.US_ASCII)
    private val PONG_PREFIX = "PONG".toByteArray(Charsets.US_ASCII)
    const val SIZE = 12

    fun encodePing(nonce: Long): ByteArray {
        val buf = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put(PING_PREFIX)
        buf.putLong(nonce)
        return buf.array()
    }

    fun encodePong(nonce: Long): ByteArray {
        val buf = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put(PONG_PREFIX)
        buf.putLong(nonce)
        return buf.array()
    }

    fun parse(bytes: ByteArray?): PingMessage? {
        if (bytes == null || bytes.size != SIZE) return null
        val prefix = bytes.copyOfRange(0, 4)
        val buf = ByteBuffer.wrap(bytes, 4, 8).order(ByteOrder.BIG_ENDIAN)
        val nonce = buf.long
        return when {
            prefix.contentEquals(PING_PREFIX) -> PingMessage.Ping(nonce)
            prefix.contentEquals(PONG_PREFIX) -> PingMessage.Pong(nonce)
            else -> null
        }
    }
}
