package com.resqedge.lite.ui.responder

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.resqedge.lite.model.SimulatedReport

private val WATER = listOf("ankle", "knee", "waist", "chest", "roof")

private fun medicalText(flags: Int): String {
    val names = buildList {
        if ((flags and 1) != 0) add("injured")
        if ((flags and 2) != 0) add("elderly")
        if ((flags and 4) != 0) add("infant")
        if ((flags and 8) != 0) add("medication")
        if ((flags and 16) != 0) add("pregnant")
    }
    return if (names.isEmpty()) "no medical flags" else names.joinToString(", ")
}

private fun agoText(timestamp: Long, nowSec: Long): String {
    val m = ((nowSec - timestamp) / 60).toInt().coerceAtLeast(0)
    return if (m >= 60) "${m / 60} h ${m % 60} min ago" else "$m min ago"
}

private fun rebaseToNow(list: List<SimulatedReport>, nowSec: Long): List<SimulatedReport> {
    if (list.isEmpty()) return list
    val shift = (nowSec - 300) - list.maxOf { it.timestamp }
    return list.map { it.copy(timestamp = it.timestamp + shift) }
}

private fun fallbackReports(): List<SimulatedReport> {
    val now = System.currentTimeMillis() / 1000
    return listOf(
        SimulatedReport(1, 9.40, 76.40, 10, 6, 3, 3, 4, now - 1200, 1, "Kuttanad"),
        SimulatedReport(2, 9.32, 76.61, 15, 2, 4, 2, 3, now - 2400, 0, "Chengannur"),
        SimulatedReport(3, 10.11, 76.35, 8, 9, 1, 4, 5, now - 600, 2, "Aluva"),
        SimulatedReport(4, 10.31, 76.33, 20, 4, 0, 1, 1, now - 5400, 3, "Chalakudy"),
        SimulatedReport(5, 9.38, 76.78, 12, 3, 8, 3, 3, now - 900, 1, "Ranni")
    )
}

private fun loadReports(context: Context): Pair<List<SimulatedReport>, Boolean> {
    return try {
        val lines = context.assets.open("synthetic_sos_200.csv")
            .bufferedReader().readLines().drop(1)
        val parsed = lines.mapNotNull { SimulatedReport.fromCsvLine(it) }
        if (parsed.isEmpty()) Pair(fallbackReports(), true)
        else Pair(rebaseToNow(parsed, System.currentTimeMillis() / 1000), false)
    } catch (e: Exception) {
        Pair(fallbackReports(), true)
    }
}

@Composable
fun ResponderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val loaded = remember { loadReports(context) }
    val usingFallback = loaded.second
    // Simple sort for the skeleton. Real scoring comes in the scoring item.
    val reports = remember {
        loaded.first.sortedWith(
            compareByDescending<SimulatedReport> { it.urgency }
                .thenByDescending { it.headcount }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text("Responder mode", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (usingFallback) "SIMULATED DATA (built-in sample, CSV not found)"
            else "SIMULATED DATA (200-record test set)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            "${reports.size} reports, ${reports.sumOf { it.headcount }} people",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        IngestDebugPanel()
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reports, key = { it.packetId }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Urgency ${r.urgency}  |  ${r.area}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("${r.headcount} people  |  water: ${WATER.getOrElse(r.waterLevel) { "?" }}")
                        Text(medicalText(r.medicalFlags))
                        Text(
                            "${r.hopCount} hops  |  accuracy ${r.accuracyM} m  |  ${agoText(r.timestamp, nowSec)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
