package com.resqedge.lite.mesh

import kotlin.random.Random

enum class Action { Initiate, WaitForPeer, Ignore }

enum class EndpointState { FOUND, REQUESTING, INITIATED, CONNECTED }

class EndpointTracker(private val myName: String) {

    private data class Entry(
        val name: String,
        var state: EndpointState,
        var stateTimeMs: Long,
        val randomDelayMs: Long = Random.nextLong(0, 1501)
    )

    private val endpoints = mutableMapOf<String, Entry>()
    private val disconnectTimes = mutableMapOf<String, Long>()

    fun onFound(id: String, name: String, nowMs: Long): Action {
        var entry = endpoints[id]
        if (entry == null) {
            entry = Entry(name, EndpointState.FOUND, nowMs)
            endpoints[id] = entry
        }

        if (entry.state == EndpointState.REQUESTING || 
            entry.state == EndpointState.INITIATED || 
            entry.state == EndpointState.CONNECTED) {
            return Action.Ignore
        }

        val isInitiator = shouldInitiate(myName, name)
        val elapsed = nowMs - entry.stateTimeMs
        
        if (isInitiator || elapsed > 10000 + entry.randomDelayMs) {
            entry.state = EndpointState.REQUESTING
            entry.stateTimeMs = nowMs
            return Action.Initiate
        }

        return Action.WaitForPeer
    }

    fun onRequestFailed(id: String) {
        endpoints.remove(id)
    }

    fun onInitiated(id: String, nowMs: Long) {
        val entry = endpoints[id]
        if (entry != null) {
            entry.state = EndpointState.INITIATED
            entry.stateTimeMs = nowMs
        }
    }

    fun onConnected(id: String, nowMs: Long): Long? {
        val entry = endpoints[id]
        if (entry != null) {
            entry.state = EndpointState.CONNECTED
            entry.stateTimeMs = nowMs
            val lastDowntimeStart = disconnectTimes.remove(entry.name)
            if (lastDowntimeStart != null) {
                return nowMs - lastDowntimeStart
            }
        }
        return null
    }

    fun onDisconnected(id: String, nowMs: Long) {
        val entry = endpoints.remove(id)
        if (entry != null) {
            disconnectTimes[entry.name] = nowMs
        }
    }

    fun onLost(id: String) {
        endpoints.remove(id)
    }

    fun sweep(nowMs: Long): List<String> {
        val stuckIds = mutableListOf<String>()
        val it = endpoints.iterator()
        while (it.hasNext()) {
            val (id, entry) = it.next()
            if ((entry.state == EndpointState.REQUESTING || entry.state == EndpointState.INITIATED) && 
                (nowMs - entry.stateTimeMs > 15000)) {
                stuckIds.add(id)
                it.remove()
            }
        }
        return stuckIds
    }
}
