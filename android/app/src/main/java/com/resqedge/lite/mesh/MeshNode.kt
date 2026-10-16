package com.resqedge.lite.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.resqedge.lite.data.Ingestor
import com.resqedge.lite.data.IngestResult
import com.resqedge.lite.data.ServiceLocator
import com.resqedge.lite.model.SosPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class Peer(val id: String, val name: String)

class MeshNode(
    context: Context,
    private val ingestor: Ingestor
) {
    private val appContext = context.applicationContext
    private val connectionsClient = Nearby.getConnectionsClient(appContext)

    private val serviceId = "com.resqedge.lite.mesh"
    private val strategy = Strategy.P2P_CLUSTER

    private var myEndpointName: String = ""
    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    val connectedPeers: StateFlow<List<Peer>> = _connectedPeers.asStateFlow()

    private val _connectingPeers = MutableStateFlow<List<String>>(emptyList())
    val connectingPeers: StateFlow<List<String>> = _connectingPeers.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _receivedStored = MutableStateFlow(0)
    val receivedStored: StateFlow<Int> = _receivedStored.asStateFlow()

    private val _receivedDuplicate = MutableStateFlow(0)
    val receivedDuplicate: StateFlow<Int> = _receivedDuplicate.asStateFlow()

    private val _receivedMalformed = MutableStateFlow(0)
    val receivedMalformed: StateFlow<Int> = _receivedMalformed.asStateFlow()

    private val _sentPackets = MutableStateFlow(0)
    val sentPackets: StateFlow<Int> = _sentPackets.asStateFlow()

    private val _lastSyncSent = MutableStateFlow("")
    val lastSyncSent: StateFlow<String> = _lastSyncSent.asStateFlow()

    private val _lastSyncMs = MutableStateFlow(0L)
    val lastSyncMs: StateFlow<Long> = _lastSyncMs.asStateFlow()

    private val _connectTimes = MutableStateFlow<List<Long>>(emptyList())
    val connectTimes: StateFlow<List<Long>> = _connectTimes.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isStarted = false

    private val knownEndpoints = mutableSetOf<String>()
    private val endpointDiscoveryTimes = ConcurrentHashMap<String, Long>()
    private val sentPings = ConcurrentHashMap<Long, Long>() // nonce -> nanoTime

    // Sync from peer burst tracking
    private data class BurstState(
        var count: Int = 0,
        var startTimeMs: Long = 0L,
        var job: Job? = null
    )
    private val burstStates = ConcurrentHashMap<String, BurstState>()

    fun start() {
        if (isStarted) {
            logMessage("Already started")
            return
        }
        isStarted = true
        myEndpointName = "ResQ-" + generateRandomChars(4)
        knownEndpoints.clear()
        _connectedPeers.value = emptyList()
        _connectingPeers.value = emptyList()

        logMessage("Starting mesh node with name: $myEndpointName")
        startAdvertisingInternal()
        startDiscoveryInternal()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        logMessage("Stopping mesh node")
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            logMessage("Error during stop: ${e.message}")
        }
        _connectedPeers.value = emptyList()
        _connectingPeers.value = emptyList()
        knownEndpoints.clear()
    }

    fun sendToAll(bytes: ByteArray, excludeEndpointId: String? = null): Int {
        val peers = _connectedPeers.value
        val targets = peers.filter { it.id != excludeEndpointId }
        if (targets.isEmpty()) {
            logMessage("Saved locally, will be sent when a peer connects")
            return 0
        }
        val payload = Payload.fromBytes(bytes)
        var sentCount = 0
        for (peer in targets) {
            connectionsClient.sendPayload(peer.id, payload)
                .addOnSuccessListener {
                    logMessage("Sent packet to ${peer.name} (${peer.id})")
                    _sentPackets.value++
                }
                .addOnFailureListener { e ->
                    logMessage("Failed to send to ${peer.name}: ${e.message}")
                }
            sentCount++
        }
        return sentCount
    }

    fun sendTestSos(bytes: ByteArray) {
        scope.launch(Dispatchers.IO) {
            val result = ingestor.ingest(bytes)
            val packet = SosPacket.fromBytes(bytes)
            logPacketResult(null, result, bytes)
            if (result is IngestResult.Stored && packet != null) {
                val fwd = Forwarding.forwardCopy(packet)
                if (fwd != null) {
                    sendToAll(fwd.toBytes())
                }
            }
        }
    }

    fun sendPing() {
        val peers = _connectedPeers.value
        if (peers.isEmpty()) {
            logMessage("No peers connected to ping")
            return
        }
        val nonce = Random.nextLong()
        val pingBytes = Ping.encodePing(nonce)
        val payload = Payload.fromBytes(pingBytes)
        for (peer in peers) {
            sentPings[nonce] = System.nanoTime()
            connectionsClient.sendPayload(peer.id, payload)
                .addOnFailureListener { e ->
                    logMessage("Failed to send ping to ${peer.name}: ${e.message}")
                }
        }
        logMessage("Sent ping with nonce $nonce to ${peers.size} peers")
    }

    private fun startAdvertisingInternal() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(myEndpointName, serviceId, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                logMessage("Advertising started successfully")
            }
            .addOnFailureListener { e ->
                logMessage("Failed to start advertising: ${e.message}")
            }
    }

    private fun startDiscoveryInternal() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                logMessage("Discovery started successfully")
            }
            .addOnFailureListener { e ->
                logMessage("Failed to start discovery: ${e.message}")
            }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val theirName = info.endpointName
            logMessage("Endpoint found: $theirName ($endpointId)")
            endpointDiscoveryTimes[endpointId] = System.nanoTime()
            if (knownEndpoints.contains(endpointId)) return

            if (shouldInitiate(myEndpointName, theirName)) {
                logMessage("Initiating connection to $theirName ($endpointId)")
                knownEndpoints.add(endpointId)
                addConnecting(endpointId)
                connectionsClient.requestConnection(myEndpointName, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        logMessage("Request connection failed to $endpointId: ${e.message}")
                        knownEndpoints.remove(endpointId)
                        removeConnecting(endpointId)
                        endpointDiscoveryTimes.remove(endpointId)
                    }
            } else {
                logMessage("Waiting for connection from $theirName ($endpointId)")
                addConnecting(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            logMessage("Endpoint lost: $endpointId")
            removeConnecting(endpointId)
            endpointDiscoveryTimes.remove(endpointId)
        }
    }

    private fun addConnecting(endpointId: String) {
        val list = _connectingPeers.value.toMutableList()
        if (!list.contains(endpointId)) {
            list.add(endpointId)
            _connectingPeers.value = list
        }
    }

    private fun removeConnecting(endpointId: String) {
        val list = _connectingPeers.value.filter { it != endpointId }
        _connectingPeers.value = list
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            logMessage("Connection initiated with ${info.endpointName} ($endpointId). Auto-accepting.")
            addConnecting(endpointId)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val status = resolution.status
            removeConnecting(endpointId)
            if (status.isSuccess) {
                logMessage("Connected to endpoint: $endpointId")
                val startTime = endpointDiscoveryTimes.remove(endpointId)
                if (startTime != null) {
                    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                    logMessage("Connect took $elapsedMs ms")
                    val currentTimes = _connectTimes.value.toMutableList()
                    currentTimes.add(elapsedMs)
                    if (currentTimes.size > 10) currentTimes.removeAt(0)
                    _connectTimes.value = currentTimes
                }

                val current = _connectedPeers.value.toMutableList()
                if (current.none { it.id == endpointId }) {
                    current.add(Peer(id = endpointId, name = endpointId))
                    _connectedPeers.value = current
                }

                performSyncOnConnect(endpointId)
            } else {
                logMessage("Connection failed for $endpointId: status code ${status.statusCode}, message: ${status.statusMessage}")
                knownEndpoints.remove(endpointId)
                endpointDiscoveryTimes.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            logMessage("Disconnected from endpoint: $endpointId")
            val current = _connectedPeers.value.filter { it.id != endpointId }
            _connectedPeers.value = current
            knownEndpoints.remove(endpointId)
            removeConnecting(endpointId)
            endpointDiscoveryTimes.remove(endpointId)
        }
    }

    private fun performSyncOnConnect(endpointId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val store = ServiceLocator.store(appContext)
                val forwardablePackets = store.forwardable()
                val sorted = Forwarding.syncOrder(forwardablePackets)
                val startTime = System.currentTimeMillis()
                var sentCount = 0
                var failCount = 0

                for ((index, packet) in sorted.withIndex()) {
                    val fwd = Forwarding.forwardCopy(packet)
                    if (fwd != null) {
                        try {
                            val payload = Payload.fromBytes(fwd.toBytes())
                            val success = suspendCancellableCoroutine { continuation ->
                                connectionsClient.sendPayload(endpointId, payload)
                                    .addOnSuccessListener { continuation.resume(true) { _, _, _ -> } }
                                    .addOnFailureListener { continuation.resume(false) { _, _, _ -> } }
                            }
                            if (success) {
                                sentCount++
                                _sentPackets.value++
                            } else {
                                failCount++
                                logMessage("Failed to sync packet ${packet.packetId} to $endpointId")
                            }
                        } catch (e: Exception) {
                            failCount++
                            logMessage("Error syncing packet ${packet.packetId} to $endpointId: ${e.message}")
                        }
                    }
                    if (index > 0 && index % 20 == 0) {
                        delay(50)
                    }
                }
                val durationMs = System.currentTimeMillis() - startTime
                val syncMsg = "Sync to $endpointId: sent $sentCount packets in $durationMs ms (fails: $failCount)"
                logMessage(syncMsg)
                _lastSyncSent.value = syncMsg
                _lastSyncMs.value = durationMs
            } catch (e: Exception) {
                logMessage("Error during sync to $endpointId: ${e.message}")
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) {
                logMessage("Ignored non-bytes payload from $endpointId")
                return
            }
            val bytes = payload.asBytes()
            if (bytes == null) {
                logMessage("Ignored null bytes payload from $endpointId")
                return
            }

            if (bytes.size == Ping.SIZE) {
                val parsedPing = Ping.parse(bytes)
                when (parsedPing) {
                    is PingMessage.Ping -> {
                        val pongBytes = Ping.encodePong(parsedPing.nonce)
                        connectionsClient.sendPayload(endpointId, Payload.fromBytes(pongBytes))
                            .addOnFailureListener { e ->
                                logMessage("Failed to send pong to $endpointId: ${e.message}")
                            }
                    }
                    is PingMessage.Pong -> {
                        val sentTime = sentPings.remove(parsedPing.nonce)
                        if (sentTime != null) {
                            val rttMs = (System.nanoTime() - sentTime) / 1_000_000.0
                            val oneWayMs = rttMs / 2.0
                            logMessage(String.format(Locale.US, "RTT to %s: %.1f ms (one-way about %.1f ms)", endpointId, rttMs, oneWayMs))
                        }
                    }
                    null -> {}
                }
                return
            }

            if (bytes.size != SosPacket.SIZE) {
                logMessage("Ignored payload of size ${bytes.size} from $endpointId")
                return
            }

            scope.launch(Dispatchers.IO) {
                val result = ingestor.ingest(bytes)
                logPacketResult(endpointId, result, bytes)

                when (result) {
                    is IngestResult.Stored -> {
                        _receivedStored.value++
                        handleIncomingSyncBurst(endpointId)
                        val stored = result.packet
                        if (stored.ttl > 0) {
                            val fwd = Forwarding.forwardCopy(stored)
                            if (fwd != null) {
                                sendToAll(fwd.toBytes(), excludeEndpointId = endpointId)
                            }
                        }
                    }
                    is IngestResult.Duplicate -> {
                        _receivedDuplicate.value++
                    }
                    is IngestResult.Malformed -> {
                        _receivedMalformed.value++
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun logPacketResult(endpointId: String?, result: IngestResult, bytes: ByteArray) {
        val parsed = SosPacket.fromBytes(bytes)
        val id = parsed?.packetId ?: 0L
        val hop = parsed?.hopCount ?: 0
        val ttl = parsed?.ttl ?: 0
        val resultName = when (result) {
            is IngestResult.Stored -> "Stored"
            is IngestResult.Duplicate -> "Duplicate"
            is IngestResult.Malformed -> "Malformed"
        }
        val source = if (endpointId != null) "from $endpointId" else "locally"
        logMessage("Packet $source: id=$id, result=$resultName, hopCount=$hop, ttl=$ttl")
    }

    private fun handleIncomingSyncBurst(endpointId: String) {
        val now = System.currentTimeMillis()
        val state = burstStates.getOrAdd(endpointId) { BurstState() }
        synchronized(state) {
            if (state.count == 0) {
                state.startTimeMs = now
                state.count = 1
            } else {
                state.count++
            }
            state.job?.cancel()
            state.job = scope.launch(Dispatchers.IO) {
                delay(2000L)
                val currentCount: Int
                val duration: Long
                synchronized(state) {
                    currentCount = state.count
                    duration = System.currentTimeMillis() - state.startTimeMs
                    state.count = 0
                    state.startTimeMs = 0L
                    state.job = null
                }
                if (currentCount > 0) {
                    logMessage("Sync from $endpointId: $currentCount packets in $duration ms")
                }
            }
        }
    }

    private inline fun <K, V> ConcurrentHashMap<K, V>.getOrAdd(key: K, defaultValue: () -> V): V {
        return this[key] ?: synchronized(this) {
            this[key] ?: defaultValue().also { this[key] = it }
        }
    }

    private fun generateRandomChars(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder()
        val random = Random.Default
        for (i in 0 until length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    private fun logMessage(msg: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formatted = "$timeStr $msg"
        Log.d("MeshNode", formatted)
        val current = _log.value.toMutableList()
        current.add(0, formatted)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _log.value = current
    }
}
