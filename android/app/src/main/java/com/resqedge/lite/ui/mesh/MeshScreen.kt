package com.resqedge.lite.ui.mesh

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.resqedge.lite.data.ServiceLocator
import com.resqedge.lite.mesh.MeshNode
import com.resqedge.lite.mesh.rememberMeshPermissionsState
import com.resqedge.lite.model.PacketBuilder
import com.resqedge.lite.model.SosPacket
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun MeshScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
        }
    }

    val (permissionsGranted, requestPermissions) = rememberMeshPermissionsState()

    val ingestor = remember { ServiceLocator.ingestor(context) }
    val meshNode = remember { MeshNode(context, ingestor) }

    val connectedPeers by meshNode.connectedPeers.collectAsState()
    val logLines by meshNode.log.collectAsState()
    val storedCount by ServiceLocator.store(context).observeCount().collectAsState(initial = 0)

    var isRunning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            meshNode.stop()
        }
    }

    val coroutineScope = rememberScopeSafe()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mesh Test", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))

        val permStatus = if (permissionsGranted) "Permissions: OK" else "Permissions: Missing"
        val runStatus = if (isRunning) "Mesh: Running" else "Mesh: Stopped"
        Text("$permStatus | $runStatus", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))

        Text(
            "Turn hotspot off. Keep Bluetooth, Wi-Fi and Location on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (!permissionsGranted) {
                        requestPermissions()
                    } else {
                        meshNode.start()
                        isRunning = true
                    }
                },
                modifier = Modifier.weight(1.0f)
            ) {
                Text("Start mesh")
            }
            Button(
                onClick = {
                    meshNode.stop()
                    isRunning = false
                },
                modifier = Modifier.weight(1.0f)
            ) {
                Text("Stop mesh")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val packet = PacketBuilder.build(
                        headcount = 3,
                        medicalFlags = SosPacket.FLAG_INJURED,
                        waterLevel = 2,
                        lat = 10.0159f,
                        lon = 76.3419f,
                        accuracyM = 0,
                        senderId = Random.nextInt()
                    )
                    val bytes = packet.toBytes()
                    coroutineScope.launch {
                        ingestor.ingest(bytes)
                        meshNode.sendToAll(bytes)
                    }
                },
                modifier = Modifier.weight(1.0f)
            ) {
                Text("Send test SOS")
            }
            OutlinedButton(
                onClick = { },
                modifier = Modifier.weight(1.0f)
            ) {
                Text("Clear log")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Stored on this phone: $storedCount", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        Text("Connected Peers (${connectedPeers.size}):", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            items(connectedPeers) { peer ->
                Text("- ${peer.name} (${peer.id})", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Log:", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(logLines) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun rememberScopeSafe() = rememberCoroutineScope()
