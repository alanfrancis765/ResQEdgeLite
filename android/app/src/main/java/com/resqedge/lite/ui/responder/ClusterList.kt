package com.resqedge.lite.ui.responder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resqedge.lite.model.Cluster
import com.resqedge.lite.model.deepestWater
import com.resqedge.lite.model.headline
import com.resqedge.lite.model.needsSummary
import com.resqedge.lite.model.waterName
import java.util.Locale

@Composable
fun ClusterList(clusters: List<Cluster>, modifier: Modifier = Modifier) {
    if (clusters.isEmpty()) {
        Text("No packets yet. Open Reports and load simulated packets.", modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(clusters) { index, c -> ClusterCard(index + 1, c) }
    }
}

@Composable
fun ClusterCard(rank: Int, c: Cluster, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("#$rank  ${c.headline()}", style = MaterialTheme.typography.titleMedium)
            Text("Deepest water: ${waterName(c.deepestWater)}  |  top score ${c.topScore.toInt()}")
            Text(c.needsSummary())
            Text(
                String.format(Locale.US, "%.4f, %.4f", c.centerLat, c.centerLon),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
