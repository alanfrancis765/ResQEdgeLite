package com.resqedge.lite.ui.capture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqedge.lite.model.PacketBuilder
import com.resqedge.lite.model.SosPacket
import kotlin.random.Random

private val medicalOptions = listOf(
    "Someone is injured" to SosPacket.FLAG_INJURED,
    "Elderly person" to SosPacket.FLAG_ELDERLY,
    "Infant or small child" to SosPacket.FLAG_INFANT,
    "Needs regular medication" to SosPacket.FLAG_MEDICATION,
    "Pregnant" to SosPacket.FLAG_PREGNANT
)
private val waterOptions = listOf(
    "Ankle deep", "Knee deep", "Waist deep", "Chest deep", "Up to the roof"
)

// Placeholder until GPS is added in the next step.
private const val PLACEHOLDER_LAT = 10.0159f
private const val PLACEHOLDER_LON = 76.3419f

@Composable
fun CitizenScreen(onBack: () -> Unit) {
    var headcount by remember { mutableStateOf(1) }
    var flags by remember { mutableStateOf(0) }
    var water by remember { mutableStateOf(2) }
    var packet by remember { mutableStateOf<SosPacket?>(null) }
    val senderId = remember { Random.nextInt(0, Int.MAX_VALUE) } // persisted per install later

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text("Request help", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))
        Text("How many people?", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (headcount > 1) headcount-- }) { Text("-") }
            Text("  $headcount  ", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { if (headcount < 99) headcount++ }) { Text("+") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Medical needs", style = MaterialTheme.typography.titleMedium)
        medicalOptions.forEach { (label, bit) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = (flags and bit) != 0,
                    onCheckedChange = { flags = flags xor bit }
                )
                Text(label)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Water level", style = MaterialTheme.typography.titleMedium)
        waterOptions.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = water == index,
                        onClick = { water = index },
                        role = Role.RadioButton
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = water == index, onClick = null)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Location: placeholder for now (GPS is the next step)",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                packet = PacketBuilder.build(
                    headcount = headcount,
                    medicalFlags = flags,
                    waterLevel = water,
                    lat = PLACEHOLDER_LAT,
                    lon = PLACEHOLDER_LON,
                    accuracyM = 0,
                    senderId = senderId
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Prepare SOS packet") }

        packet?.let { p ->
            val bytes = p.toBytes()
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Packet ready: ${bytes.size} bytes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Urgency ${p.urgency}  |  ttl ${p.ttl}  |  hop ${p.hopCount}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bytes.joinToString(" ") { "%02X".format(it) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Not sent yet. Storage and mesh sending come in later steps.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
