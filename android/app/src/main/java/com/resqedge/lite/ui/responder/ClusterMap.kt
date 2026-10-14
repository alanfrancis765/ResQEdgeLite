package com.resqedge.lite.ui.responder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqedge.lite.model.Cluster
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val M_PER_DEG = 111_000.0
private val NICE_METRES = intArrayOf(
    1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000
)

/** Not a street map. Circles show where clusters are, sized by people and coloured by urgency. */
@Composable
fun ClusterMap(clusters: List<Cluster>, modifier: Modifier = Modifier) {
    if (clusters.isEmpty()) {
        Text("No packets yet. Open Reports and load simulated packets.", modifier = modifier)
    } else {
        ClusterMapContent(clusters, modifier)
    }
}

@Composable
private fun ClusterMapContent(clusters: List<Cluster>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val lat0 = remember(clusters) { clusters.map { it.centerLat }.average() }
    val lon0 = remember(clusters) { clusters.map { it.centerLon }.average() }
    val pts = remember(clusters) {
        val kx = M_PER_DEG * cos(Math.toRadians(lat0))
        clusters.map {
            Offset(
                ((it.centerLon - lon0) * kx).toFloat(),
                ((it.centerLat - lat0) * M_PER_DEG).toFloat()
            )
        }
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var cx by remember { mutableStateOf(0f) }      // view centre, metres east
    var cy by remember { mutableStateOf(0f) }      // view centre, metres north
    var scale by remember { mutableStateOf(1f) }   // pixels per metre
    var selected by remember { mutableStateOf<Int?>(null) }

    fun fitAll() {
        if (viewSize == IntSize.Zero) return
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val spanX = max(maxX - minX, 200f)
        val spanY = max(maxY - minY, 200f)
        scale = min(viewSize.width / spanX, viewSize.height / spanY) * 0.8f
        cx = (minX + maxX) / 2f
        cy = (minY + maxY) / 2f
    }

    fun focusTop() {
        if (viewSize == IntSize.Zero) return
        scale = viewSize.width / 400f   // about 400 m across
        cx = pts[0].x
        cy = pts[0].y
    }

    LaunchedEffect(viewSize) { if (viewSize != IntSize.Zero) fitAll() }

    val maxScore = clusters.maxOf { it.topScore }.coerceAtLeast(1.0)
    val colorLow = Color(0xFFF9A825)
    val colorHigh = Color(0xFFB71C1C)
    val sel = selected?.takeIf { it < clusters.size }

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { fitAll() }) { Text("Fit all") }
            Button(onClick = { focusTop() }) { Text("Zoom to top cluster") }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val w = viewSize.width / 2f
                        val h = viewSize.height / 2f
                        val wx = cx + (centroid.x - w) / scale
                        val wy = cy - (centroid.y - h) / scale
                        scale = (scale * zoom).coerceIn(0.002f, 20f)
                        cx = wx - (centroid.x - w) / scale - pan.x / scale
                        cy = wy + (centroid.y - h) / scale + pan.y / scale
                    }
                }
                .pointerInput(pts) {
                    detectTapGestures { tap ->
                        var best = -1
                        var bestDist = Float.MAX_VALUE
                        pts.forEachIndexed { i, p ->
                            val sx = viewSize.width / 2f + (p.x - cx) * scale
                            val sy = viewSize.height / 2f - (p.y - cy) * scale
                            val d = hypot(sx - tap.x, sy - tap.y)
                            if (d < bestDist) { bestDist = d; best = i }
                        }
                        selected = if (best >= 0 && bestDist < 32.dp.toPx()) best else null
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            // Pass 1: Draw circles
            pts.forEachIndexed { i, p ->
                val sx = w / 2f + (p.x - cx) * scale
                val sy = h / 2f - (p.y - cy) * scale
                if (sx < -60f || sx > w + 60f || sy < -60f || sy > h + 60f) return@forEachIndexed
                val c = clusters[i]
                val r = (10f + 3f * sqrt(c.totalPeople.toFloat())).dp.toPx()
                val t = (c.topScore / maxScore).toFloat().coerceIn(0f, 1f)
                val centre = Offset(sx, sy)
                drawCircle(
                    color = lerp(colorLow, colorHigh, t).copy(alpha = 0.88f),
                    radius = r,
                    center = centre
                )
                if (sel == i) {
                    drawCircle(
                        color = Color.White, radius = r + 4.dp.toPx(),
                        center = centre, style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            // Pass 2: Draw rank numbers
            pts.forEachIndexed { i, p ->
                val sx = w / 2f + (p.x - cx) * scale
                val sy = h / 2f - (p.y - cy) * scale
                if (sx < -60f || sx > w + 60f || sy < -60f || sy > h + 60f) return@forEachIndexed
                val c = clusters[i]
                val t = (c.topScore / maxScore).toFloat().coerceIn(0f, 1f)
                if (i < 10 || sel == i || clusters.size <= 25) {
                    val label = measurer.measure(
                        "${i + 1}",
                        TextStyle(
                            color = if (t < 0.5f) Color.Black else Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    )
                    drawText(
                        label,
                        topLeft = Offset(sx - label.size.width / 2f, sy - label.size.height / 2f)
                    )
                }
            }
            // scale bar
            val metres = 100.dp.toPx() / scale
            val nice = NICE_METRES.lastOrNull { it <= metres } ?: NICE_METRES.first()
            val x0 = 16.dp.toPx()
            val y0 = h - 16.dp.toPx()
            drawLine(
                color = Color.White, start = Offset(x0, y0),
                end = Offset(x0 + nice * scale, y0), strokeWidth = 3.dp.toPx()
            )
            val text = if (nice >= 1000) "${nice / 1000} km" else "$nice m"
            val l = measurer.measure(text, TextStyle(color = Color.White, fontSize = 12.sp))
            drawText(l, topLeft = Offset(x0, y0 - l.size.height - 4.dp.toPx()))
        }
        Spacer(Modifier.height(8.dp))
        if (sel != null) {
            ClusterCard(sel + 1, clusters[sel])
        } else {
            Text(
                "Tap a circle for details. Circle size = people, colour = urgency. Not a street map.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
