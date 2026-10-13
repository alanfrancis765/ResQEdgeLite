package com.resqedge.lite.ui.capture

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqedge.lite.data.IngestResult
import com.resqedge.lite.data.ServiceLocator
import com.resqedge.lite.model.PacketBuilder
import com.resqedge.lite.model.SosPacket
import kotlinx.coroutines.launch
import java.util.Locale
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

// Used only when "Use test location" is ticked.
private const val TEST_LAT = 10.0159f
private const val TEST_LON = 76.3419f

@Composable
fun CitizenScreen(onBack: () -> Unit) {
    var headcount by remember { mutableStateOf(1) }
    var flags by remember { mutableStateOf(0) }
    var water by remember { mutableStateOf(2) }
    var packet by remember { mutableStateOf<SosPacket?>(null) }
    var locationNote by remember { mutableStateOf("") }
    var useTest by remember { mutableStateOf(false) }
    val senderId = remember { Random.nextInt(0, Int.MAX_VALUE) } // persist per install later
    val gps = rememberGps()

    val appContext = LocalContext.current
    val ingestor = remember { ServiceLocator.ingestor(appContext) }
    val scope = rememberCoroutineScope()
    var saveMsg by remember { mutableStateOf("") }

    val canPrepare = gps.status == GpsStatus.FIX || useTest

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

        Spacer(Modifier.height(16.dp))
        GpsCard(gps = gps, useTest = useTest, onUseTest = { useTest = it })

        Spacer(Modifier.height(16.dp))
        Button(
            enabled = canPrepare,
            onClick = {
                val live = gps.status == GpsStatus.FIX && !useTest
                packet = PacketBuilder.build(
                    headcount = headcount,
                    medicalFlags = flags,
                    waterLevel = water,
                    lat = if (live) gps.lat.toFloat() else TEST_LAT,
                    lon = if (live) gps.lon.toFloat() else TEST_LON,
                    accuracyM = if (live) Math.round(gps.accuracyM) else 0,
                    senderId = senderId
                )
                locationNote = if (live)
                    "Live GPS, accuracy about ${Math.round(gps.accuracyM)} m"
                else
                    "TEST location (not real)"
                saveMsg = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Prepare SOS packet") }
        if (!canPrepare) {
            Text(
                "Waiting for a location. Tick the test option to continue indoors.",
                style = MaterialTheme.typography.bodySmall
            )
        }

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
                    Text(
                        "Location: $locationNote",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bytes.joinToString(" ") { "%02X".format(it) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            saveMsg = when (ingestor.ingest(bytes)) {
                                is IngestResult.Stored -> "Saved on this phone. Mesh sending comes later."
                                IngestResult.Duplicate -> "Already saved."
                                IngestResult.Malformed -> "Error: packet rejected."
                            }
                        }
                    }) { Text("Save on this phone") }
                    if (saveMsg.isNotEmpty()) {
                        Text(saveMsg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsCard(gps: GpsState, useTest: Boolean, onUseTest: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Location", style = MaterialTheme.typography.titleMedium)
            when (gps.status) {
                GpsStatus.NO_PERMISSION -> {
                    Text("Precise location permission is needed to attach your position.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = gps.requestPermission) { Text("Allow location") }
                }
                GpsStatus.GPS_OFF -> {
                    Text("Location is switched off on this phone.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) { Text("Open location settings") }
                }
                GpsStatus.SEARCHING ->
                    Text("Searching for a GPS signal. Go near a window or outside; this can take a minute.")
                GpsStatus.FIX ->
                    Text(
                        String.format(
                            Locale.US,
                            "%.5f, %.5f  (accuracy %d m, updated %d s ago)",
                            gps.lat, gps.lon, Math.round(gps.accuracyM), gps.ageSeconds
                        )
                    )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useTest, onCheckedChange = onUseTest)
                Text("Use test location (for indoor testing)")
            }
        }
    }
}
