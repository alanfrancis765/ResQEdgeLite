package com.resqedge.lite.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.resqedge.lite.data.Ingestor
import com.resqedge.lite.data.IngestResult
import com.resqedge.lite.model.SosPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
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

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isStarted = false

    private val knownEndpoints = mutableSetOf<String>()

    fun start() {
        if (isStarted) {
            logMessage("Already started")
            return
        }
        isStarted = true
        myEndpointName = "ResQ-" + generateRandomChars(4)
        knownEndpoints.clear()
        _connectedPeers.value = emptyList()

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
        knownEndpoints.clear()
    }

    fun sendToAll(bytes: ByteArray): Int {
        val peers = _connectedPeers.value
        if (peers.isEmpty()) {
            logMessage("No peers connected to send packet")
            return 0
        }
        val payload = Payload.fromBytes(bytes)
        var sentCount = 0
        for (peer in peers) {
            connectionsClient.sendPayload(peer.id, payload)
                .addOnSuccessListener {
                    logMessage("Sent packet to ${peer.name} (${peer.id})")
                }
                .addOnFailureListener { e ->
                    logMessage("Failed to send to ${peer.name}: ${e.message}")
                }
            sentCount++
        }
        return sentCount
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
            if (knownEndpoints.contains(endpointId)) return

            if (shouldInitiate(myEndpointName, theirName)) {
                logMessage("Initiating connection to $theirName ($endpointId)")
                knownEndpoints.add(endpointId)
                connectionsClient.requestConnection(myEndpointName, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        logMessage("Request connection failed to $endpointId: ${e.message}")
                        knownEndpoints.remove(endpointId)
                    }
            } else {
                logMessage("Waiting for connection from $theirName ($endpointId)")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            logMessage("Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            logMessage("Connection initiated with ${info.endpointName} ($endpointId). Auto-accepting.")
            // NOTE: Authentication token is ignored in this prototype implementation.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val status = resolution.status
            if (status.isSuccess) {
                logMessage("Connected to endpoint: $endpointId")
                val current = _connectedPeers.value.toMutableList()
                if (current.none { it.id == endpointId }) {
                    current.add(Peer(id = endpointId, name = endpointId))
                    _connectedPeers.value = current
                }
            } else {
                logMessage("Connection failed for $endpointId: status code ${status.statusCode}, message: ${status.statusMessage}")
                knownEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            logMessage("Disconnected from endpoint: $endpointId")
            val current = _connectedPeers.value.filter { it.id != endpointId }
            _connectedPeers.value = current
            knownEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) {
                logMessage("Ignored non-bytes payload from $endpointId")
                return
            }
            val bytes = payload.asBytes()
            if (bytes == null || bytes.size != SosPacket.SIZE) {
                logMessage("Ignored payload of size ${bytes?.size ?: 0} from $endpointId")
                return
            }
            scope.launch(Dispatchers.IO) {
                val result = ingestor.ingest(bytes)
                val resultDesc = when (result) {
                    is IngestResult.Stored -> "Stored packetId=${result.packet.packetId}"
                    is IngestResult.Duplicate -> "Duplicate packet"
                    is IngestResult.Malformed -> "Malformed packet"
                }
                logMessage("Received from $endpointId: $resultDesc")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // no-op for small BYTES payloads
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
