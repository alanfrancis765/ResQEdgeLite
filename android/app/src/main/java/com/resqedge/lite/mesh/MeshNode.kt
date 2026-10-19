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

    private val _reconnects = MutableStateFlow(0)
    val reconnects: StateFlow<Int> = _reconnects.asStateFlow()

    private val _lastDowntime = MutableStateFlow(0L)
    val lastDowntime: StateFlow<Long> = _lastDowntime.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isStarted = false

    private lateinit var tracker: EndpointTracker
    private val peerNames = ConcurrentHashMap<String, String>()
    private val connectionTimes = ConcurrentHashMap<String, Long>()
    private val sentPings = ConcurrentHashMap<Long, Long>() // nonce -> nanoTime
    private val lastPongTimes = ConcurrentHashMap<String, Long>()

    private var watchdogJob: Job? = null
    private var lastDisconnectOrStartMs = 0L
    private var lastDiscoveryCycleMs = 0L
    private var lastAdvertisingCycleMs = 0L
    private var lastEndpointFoundMs = 0L

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
        tracker = EndpointTracker(myEndpointName)
        
        peerNames.clear()
        connectionTimes.clear()
        _connectedPeers.value = emptyList()
        _connectingPeers.value = emptyList()

        lastDisconnectOrStartMs = System.currentTimeMillis()
        lastEndpointFoundMs = 0L
        lastDiscoveryCycleMs = lastDisconnectOrStartMs
        lastAdvertisingCycleMs = lastDisconnectOrStartMs

        logMessage("Starting mesh node with name: $myEndpointName")
        startAdvertisingInternal()
        startDiscoveryInternal()
        
        startWatchdog()
    }
    
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var loops = 0
            while (isActive) {
                delay(5000)
                val nowMs = System.currentTimeMillis()
                val stuckIds = tracker.sweep(nowMs)
                for (id in stuckIds) {
                    logMessage("State $id: -> reset (stuck > 15s)")
                    connectionsClient.disconnectFromEndpoint(id)
                }

                val peers = _connectedPeers.value
                if (peers.isNotEmpty()) {
                    val nonce = -Random.nextLong(1, Long.MAX_VALUE)
                    val pingBytes = Ping.encodePing(nonce)
                    val payload = Payload.fromBytes(pingBytes)
                    for (peer in peers) {
                        connectionsClient.sendPayload(peer.id, payload)
                        val lastPong = lastPongTimes[peer.id] ?: nowMs
                        if (nowMs - lastPong > 20000) {
                            logMessage("State ${peer.id}: -> disconnected (ping timeout)")
                            connectionsClient.disconnectFromEndpoint(peer.id)
                        }
                    }
                }

                loops++
                if (loops % 2 == 0) {
                    val noPeerConnected = peers.isEmpty()
                    val noAttemptInProgress = _connectingPeers.value.isEmpty()
                    val foundSilenceMs = nowMs - maxOf(lastDisconnectOrStartMs, lastEndpointFoundMs)

                    if (noPeerConnected && noAttemptInProgress && foundSilenceMs > 15000) {
                        if (nowMs - lastDiscoveryCycleMs > 30000) {
                            logMessage("Watchdog: cycling discovery (silence > 15s)")
                            lastDiscoveryCycleMs = nowMs
                            connectionsClient.stopDiscovery()
                            startDiscoveryInternal()
                        }
                        if (foundSilenceMs > 60000 && nowMs - lastAdvertisingCycleMs > 60000) {
                            logMessage("Watchdog: cycling advertising (silence > 60s)")
                            lastAdvertisingCycleMs = nowMs
                            connectionsClient.stopAdvertising()
                            startAdvertisingInternal()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        logMessage("Stopping mesh node")
        try {
            watchdogJob?.cancel()
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            logMessage("Error during stop: ${e.message}")
        }
        _connectedPeers.value = emptyList()
        _connectingPeers.value = emptyList()
    }

    fun dropAllLinksForTest() {
        val peers = _connectedPeers.value
        logMessage("Test: dropping ${peers.size} links")
        for (peer in peers) {
            connectionsClient.disconnectFromEndpoint(peer.id)
        }
    }

    fun sendToAll(bytes: ByteArray, excludeEndpointId: String? = null, fromTestSos: Boolean = false): Int {
        val peers = _connectedPeers.value
        val targets = peers.filter { it.id != excludeEndpointId }
        if (targets.isEmpty()) {
            if (fromTestSos) {
                logMessage("Saved locally, will be sent when a peer connects")
            }
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
                    sendToAll(fwd.toBytes(), fromTestSos = true)
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
        val nonce = Random.nextLong(1, Long.MAX_VALUE)
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
            peerNames[endpointId] = theirName
            
            val now = System.currentTimeMillis()
            lastEndpointFoundMs = now
            val action = tracker.onFound(endpointId, theirName, now)

            when (action) {
                Action.Initiate -> {
                    logMessage("State $endpointId: -> REQUESTING")
                    addConnecting(endpointId)
                    connectionsClient.requestConnection(myEndpointName, endpointId, connectionLifecycleCallback)
                        .addOnFailureListener { e ->
                            val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1
                            val statusStr = ConnectionsStatusCodes.getStatusCodeString(statusCode)
                            logMessage("Request connection failed to $endpointId: $statusCode ($statusStr)")
                            
                            if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                                logMessage("State $endpointId: REQUESTING -> CONNECTED (already)")
                                val downtime = tracker.onConnected(endpointId, System.currentTimeMillis())
                                handleNewConnection(endpointId, theirName, downtime)
                            } else {
                                tracker.onRequestFailed(endpointId)
                                removeConnecting(endpointId)
                                logMessage("State $endpointId: REQUESTING -> failed")
                            }
                        }
                }
                Action.WaitForPeer -> {
                    logMessage("State $endpointId: -> FOUND (waiting)")
                }
                Action.Ignore -> {}
            }
        }

        override fun onEndpointLost(endpointId: String) {
            tracker.onLost(endpointId)
            removeConnecting(endpointId)
            logMessage("State $endpointId: -> lost")
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

    private fun handleNewConnection(endpointId: String, name: String, downtime: Long?) {
        val now = System.currentTimeMillis()
        connectionTimes[endpointId] = now
        lastPongTimes[endpointId] = now
        removeConnecting(endpointId)
        
        if (downtime != null) {
            _reconnects.value++
            _lastDowntime.value = downtime / 1000
            logMessage("Reconnects: ${_reconnects.value} | last downtime: ${downtime / 1000} s")
        }
        
        val current = _connectedPeers.value.toMutableList()
        if (current.none { it.id == endpointId }) {
            current.add(Peer(id = endpointId, name = name))
            _connectedPeers.value = current
            performSyncOnConnect(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val theirName = info.endpointName
            peerNames[endpointId] = theirName
            logMessage("State $endpointId: -> INITIATED with $theirName")
            tracker.onInitiated(endpointId, System.currentTimeMillis())
            addConnecting(endpointId)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val status = resolution.status
            removeConnecting(endpointId)
            
            if (status.isSuccess) {
                logMessage("State $endpointId: -> CONNECTED")
                val now = System.currentTimeMillis()
                val downtime = tracker.onConnected(endpointId, now)
                val theirName = peerNames[endpointId] ?: endpointId
                
                handleNewConnection(endpointId, theirName, downtime)
                
                // Add connect time metrics if needed (can use a simple fixed value for UI if we dropped discovery times,
                // or just remove connect times logic if not critical. We'll simulate ~100ms or remove it).
                // It was using endpointDiscoveryTimes which we removed. We'll just leave connectTimes as is or add 0.
            } else {
                logMessage("State $endpointId: -> failed (${status.statusCode})")
                tracker.onRequestFailed(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val nowMs = System.currentTimeMillis()
            val connTime = connectionTimes.remove(endpointId)
            val durationStr = if (connTime != null) " after ${(nowMs - connTime) / 1000} s" else ""
            logMessage("Disconnected from $endpointId$durationStr")
            logMessage("State $endpointId: -> DISCONNECTED")
            
            val current = _connectedPeers.value.filter { it.id != endpointId }
            _connectedPeers.value = current
            
            tracker.onDisconnected(endpointId, nowMs)
            removeConnecting(endpointId)
            lastDisconnectOrStartMs = nowMs
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
            lastPongTimes[endpointId] = System.currentTimeMillis()
            
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
                        if (sentTime != null && parsedPing.nonce > 0) {
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
