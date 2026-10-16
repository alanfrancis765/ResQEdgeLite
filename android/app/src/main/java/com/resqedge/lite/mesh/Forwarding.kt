package com.resqedge.lite.mesh

import com.resqedge.lite.model.SosPacket

object Forwarding {
    fun forwardCopy(p: SosPacket): SosPacket? {
        if (p.ttl <= 0) return null
        val copied = p.copy(
            ttl = p.ttl - 1,
            hopCount = p.hopCount + 1
        )
        return if (copied.isValid()) copied else null
    }

    fun syncOrder(packets: List<SosPacket>): List<SosPacket> {
        return packets.sortedWith(
            compareByDescending<SosPacket> { it.urgency }
                .thenByDescending { it.timestamp }
                .thenBy { it.packetId }
        )
    }
}
