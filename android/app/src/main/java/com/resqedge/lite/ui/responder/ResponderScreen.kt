package com.resqedge.lite.ui.responder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.resqedge.lite.data.ServiceLocator
import com.resqedge.lite.data.StoredPacket
import com.resqedge.lite.model.SosPacket
import com.resqedge.lite.model.Triage
import kotlinx.coroutines.delay
import java.util.Locale

private val WATER = listOf("ankle", "knee", "waist", "chest", "roof", "submerged")

private data class Ranked(val sp: StoredPacket, val waitMin: Long, val score: Double)

private fun medicalText(flags: Int): String {
    val names = buildList {
        if ((flags and SosPacket.FLAG_INJURED) != 0) add("injured")
        if ((flags and SosPacket.FLAG_ELDERLY) != 0) add("elderly")
        if ((flags and SosPacket.FLAG_INFANT) != 0) add("infant")
        if ((flags and SosPacket.FLAG_MEDICATION) != 0) add("medication")
        if ((flags and SosPacket.FLAG_PREGNANT) != 0) add("pregnant")
    }
    return if (names.isEmpty()) "no medical flags" else names.joinToString(", ")
}

private fun waitText(min: Long) =
    if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

private fun hopText(h: Int) = if (h == 1) "1 hop" else "$h hops"

@Composable
fun ResponderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ServiceLocator.store(context) }
    val stored by store.observeAll().collectAsState(initial = emptyList())

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()   // waiting times and ranking keep updating
        }
    }

    val ranked = remember(stored, nowMs) {
        stored.map { sp ->
            val wait = Triage.waitMinutes(sp.packet.timestamp.toLong(), sp.receivedAtMs, nowMs)
            Ranked(sp, wait, Triage.score(sp.packet, wait))
        }.sortedWith(
            compareByDescending<Ranked> { it.score }.thenBy { it.sp.packet.packetId }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text("Responder mode", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${ranked.size} reports, ${ranked.sumOf { it.sp.packet.headcount }} people",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Ranked by a rule-based score. Packets loaded from the debug panel are simulated.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        IngestDebugPanel()
        Spacer(Modifier.height(8.dp))
        if (ranked.isEmpty()) Text("No packets yet. Use the debug panel to add some.")

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(ranked, key = { _, r -> r.sp.packet.packetId }) { index, r ->
                val p = r.sp.packet
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "#${index + 1}   score ${r.score.toInt()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("${p.headcount} people  |  water: ${WATER.getOrElse(p.waterLevel) { "?" }}")
                        Text(medicalText(p.medicalFlags))
                        Text(
                            String.format(Locale.US, "%.4f, %.4f", p.lat, p.lon),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${hopText(p.hopCount)}  |  accuracy ${p.accuracyM} m  |  waiting ${waitText(r.waitMin)}  |  sender hint ${p.urgency}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
