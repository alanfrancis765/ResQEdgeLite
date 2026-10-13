package com.resqedge.lite.ui.responder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.resqedge.lite.data.IngestResult
import com.resqedge.lite.data.ServiceLocator
import com.resqedge.lite.model.PacketBuilder
import kotlinx.coroutines.launch
import kotlin.random.Random

private fun describe(r: IngestResult) = when (r) {
    is IngestResult.Stored -> "Stored: new packet"
    IngestResult.Duplicate -> "Duplicate ignored"
    IngestResult.Malformed -> "Malformed: rejected"
}

@Composable
fun IngestDebugPanel() {
    val context = LocalContext.current
    val store = remember { ServiceLocator.store(context) }
    val ingestor = remember { ServiceLocator.ingestor(context) }
    val scope = rememberCoroutineScope()
    val count by store.observeCount().collectAsState(initial = 0)
    var last by remember { mutableStateOf<ByteArray?>(null) }
    var msg by remember { mutableStateOf("No test run yet") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Debug: receive path", style = MaterialTheme.typography.titleSmall)
            Text("Stored on this phone: $count")
            Text(msg, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    onClick = {
                        val p = PacketBuilder.build(
                            headcount = Random.nextInt(1, 10),
                            medicalFlags = Random.nextInt(0, 32),
                            waterLevel = Random.nextInt(0, 5),
                            lat = 9.4f + Random.nextFloat() * 0.1f,
                            lon = 76.4f + Random.nextFloat() * 0.1f,
                            accuracyM = 10,
                            senderId = Random.nextInt(0, Int.MAX_VALUE)
                        )
                        val bytes = p.toBytes()
                        last = bytes
                        scope.launch { msg = describe(ingestor.ingest(bytes)) }
                    }
                ) {
                    Text("New", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    onClick = {
                        val b = last
                        if (b == null) msg = "Press New first"
                        else scope.launch { msg = describe(ingestor.ingest(b)) }
                    }
                ) {
                    Text("Duplicate", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    onClick = {
                        scope.launch { msg = describe(ingestor.ingest(ByteArray(10))) }
                    }
                ) {
                    Text("Bad bytes", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { scope.launch { store.clear(); msg = "Cleared" } }) {
                Text("Clear all stored packets")
            }
        }
    }
}
