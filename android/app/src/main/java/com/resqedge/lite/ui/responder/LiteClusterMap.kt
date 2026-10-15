package com.resqedge.lite.ui.responder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.resqedge.lite.model.BaseMapData
import com.resqedge.lite.model.Cluster
import com.resqedge.lite.model.Facility
import com.resqedge.lite.model.FacilityKind
import com.resqedge.lite.model.RoadClass
import com.resqedge.lite.model.deepestWater
import com.resqedge.lite.model.headline
import com.resqedge.lite.model.nearestFacility
import com.resqedge.lite.model.needsSummary
import com.resqedge.lite.model.waterName
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val M_PER_DEG = 111_000.0
private val NICE_METRES = intArrayOf(
    1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000
)

private data class ProjectedRoad(
    val roadClass: RoadClass,
    val pts: FloatArray,
    val minX: Float, val maxX: Float, val minY: Float, val maxY: Float
)

private data class ProjectedWaterPolygon(
    val outer: FloatArray,
    val inners: List<FloatArray>,
    val minX: Float, val maxX: Float, val minY: Float, val maxY: Float
)

private data class ProjectedWaterLine(
    val pts: FloatArray,
    val minX: Float, val maxX: Float, val minY: Float, val maxY: Float
)

private data class ProjectedPlace(
    val name: String,
    val kind: String,
    val x: Float,
    val y: Float
)

private data class ProjectedFacility(
    val facility: Facility,
    val x: Float,
    val y: Float
)

private data class ProjectedBaseMap(
    val roads: List<ProjectedRoad>,
    val waterPolygons: List<ProjectedWaterPolygon>,
    val waterLines: List<ProjectedWaterLine>,
    val places: List<ProjectedPlace>,
    val facilities: List<ProjectedFacility>
)

@Composable
fun LiteClusterMap(clusters: List<Cluster>, modifier: Modifier = Modifier) {
    val baseMapState by rememberBaseMapState()
    val baseMapData = (baseMapState as? BaseMapState.Loaded)?.data
    val noData = baseMapState is BaseMapState.NoData

    if (clusters.isEmpty() && (baseMapData == null || baseMapData.isEmpty)) {
        Column(modifier = modifier) {
            Text("No packets yet. Open Reports and load simulated packets.")
            Spacer(Modifier.height(4.dp))
            Text(
                if (noData) "No basemap data. See docs/lite-map-data.md"
                else "Lite offline map pack. Shows water, roads, towns, hospitals and schools for prepared areas only.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        LiteClusterMapContent(clusters, baseMapData, noData, modifier)
    }
}

@Composable
private fun LiteClusterMapContent(
    clusters: List<Cluster>,
    baseMapData: BaseMapData?,
    noData: Boolean,
    modifier: Modifier
) {
    val measurer = rememberTextMeasurer()

    val lat0 = remember(clusters, baseMapData) {
        if (clusters.isNotEmpty()) {
            clusters.map { it.centerLat }.average()
        } else if (baseMapData != null && baseMapData.places.isNotEmpty()) {
            baseMapData.places.map { it.lat }.average()
        } else {
            9.5
        }
    }
    val lon0 = remember(clusters, baseMapData) {
        if (clusters.isNotEmpty()) {
            clusters.map { it.centerLon }.average()
        } else if (baseMapData != null && baseMapData.places.isNotEmpty()) {
            baseMapData.places.map { it.lon }.average()
        } else {
            76.5
        }
    }

    val kx = remember(lat0) { M_PER_DEG * cos(Math.toRadians(lat0)) }

    val clusterPts = remember(clusters, lat0, lon0) {
        clusters.map {
            Offset(
                ((it.centerLon - lon0) * kx).toFloat(),
                ((it.centerLat - lat0) * M_PER_DEG).toFloat()
            )
        }
    }

    val projectedMap = remember(baseMapData, lat0, lon0) {
        if (baseMapData == null) null
        else {
            val pRoads = baseMapData.roads.mapNotNull { r ->
                if (r.points.size < 2) return@mapNotNull null
                val arr = FloatArray(r.points.size * 2)
                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                r.points.forEachIndexed { i, pt ->
                    val x = ((pt.lon - lon0) * kx).toFloat()
                    val y = ((pt.lat - lat0) * M_PER_DEG).toFloat()
                    arr[i * 2] = x
                    arr[i * 2 + 1] = y
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
                ProjectedRoad(r.roadClass, arr, minX, maxX, minY, maxY)
            }

            val pPolys = baseMapData.waterPolygons.mapNotNull { wp ->
                if (wp.outer.size < 3) return@mapNotNull null
                val outerArr = FloatArray(wp.outer.size * 2)
                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                wp.outer.forEachIndexed { i, pt ->
                    val x = ((pt.lon - lon0) * kx).toFloat()
                    val y = ((pt.lat - lat0) * M_PER_DEG).toFloat()
                    outerArr[i * 2] = x
                    outerArr[i * 2 + 1] = y
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
                val innersArrs = wp.inners.mapNotNull { inner ->
                    if (inner.size < 3) null
                    else {
                        val arr = FloatArray(inner.size * 2)
                        inner.forEachIndexed { i, pt ->
                            arr[i * 2] = ((pt.lon - lon0) * kx).toFloat()
                            arr[i * 2 + 1] = ((pt.lat - lat0) * M_PER_DEG).toFloat()
                        }
                        arr
                    }
                }
                ProjectedWaterPolygon(outerArr, innersArrs, minX, maxX, minY, maxY)
            }

            val pWaterLines = baseMapData.waterLines.mapNotNull { wl ->
                if (wl.points.size < 2) return@mapNotNull null
                val arr = FloatArray(wl.points.size * 2)
                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                wl.points.forEachIndexed { i, pt ->
                    val x = ((pt.lon - lon0) * kx).toFloat()
                    val y = ((pt.lat - lat0) * M_PER_DEG).toFloat()
                    arr[i * 2] = x
                    arr[i * 2 + 1] = y
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
                ProjectedWaterLine(arr, minX, maxX, minY, maxY)
            }

            val pPlaces = baseMapData.places.map { p ->
                ProjectedPlace(
                    p.name, p.kind,
                    ((p.lon - lon0) * kx).toFloat(),
                    ((p.lat - lat0) * M_PER_DEG).toFloat()
                )
            }

            val pFacilities = baseMapData.facilities.map { f ->
                ProjectedFacility(
                    f,
                    ((f.lon - lon0) * kx).toFloat(),
                    ((f.lat - lat0) * M_PER_DEG).toFloat()
                )
            }

            ProjectedBaseMap(pRoads, pPolys, pWaterLines, pPlaces, pFacilities)
        }
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var cx by remember { mutableStateOf(0f) }
    var cy by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var selected by remember { mutableStateOf<Int?>(null) }

    fun fitAll() {
        if (viewSize == IntSize.Zero) return
        val allPts = if (clusterPts.isNotEmpty()) {
            clusterPts
        } else if (projectedMap != null && projectedMap.places.isNotEmpty()) {
            projectedMap.places.map { Offset(it.x, it.y) }
        } else return

        val minX = allPts.minOf { it.x }; val maxX = allPts.maxOf { it.x }
        val minY = allPts.minOf { it.y }; val maxY = allPts.maxOf { it.y }
        val spanX = max(maxX - minX, 200f)
        val spanY = max(maxY - minY, 200f)
        scale = min(viewSize.width / spanX, viewSize.height / spanY) * 0.8f
        cx = (minX + maxX) / 2f
        cy = (minY + maxY) / 2f
    }

    fun focusTop() {
        if (viewSize == IntSize.Zero || clusterPts.isEmpty()) return
        scale = viewSize.width / 400f
        cx = clusterPts[0].x
        cy = clusterPts[0].y
    }

    LaunchedEffect(viewSize) { if (viewSize != IntSize.Zero) fitAll() }

    val maxScore = clusters.maxOfOrNull { it.topScore }?.coerceAtLeast(1.0) ?: 1.0
    val colorLow = Color(0xFFF9A825)
    val colorHigh = Color(0xFFB71C1C)
    val sel = selected?.takeIf { it < clusters.size }

    val pathReuse = remember { Path() }

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
                .background(Color(0xFFF2EFE9))
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
                .pointerInput(clusterPts) {
                    detectTapGestures { tap ->
                        var best = -1
                        var bestDist = Float.MAX_VALUE
                        clusterPts.forEachIndexed { i, p ->
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

            val viewWidthM = w / scale
            val minVx = cx - (w / 2f) / scale
            val maxVx = cx + (w / 2f) / scale
            val minVy = cy - (h / 2f) / scale
            val maxVy = cy + (h / 2f) / scale

            val showTownsOnly = viewWidthM > 20_000f
            val showMajorRoads = viewWidthM <= 20_000f
            val showVillages = viewWidthM <= 20_000f
            val showMinorRoads = viewWidthM < 2_000f
            val showFacilities = viewWidthM < 2_000f
            val showAllPlaces = viewWidthM < 2_000f

            fun toSx(x: Float) = w / 2f + (x - cx) * scale
            fun toSy(y: Float) = h / 2f - (y - cy) * scale

            // 1. Water Polygons
            projectedMap?.waterPolygons?.forEach { wp ->
                if (wp.maxX < minVx || wp.minX > maxVx || wp.maxY < minVy || wp.minY > maxVy) return@forEach
                pathReuse.reset()
                for (i in 0 until wp.outer.size step 2) {
                    val sx = toSx(wp.outer[i])
                    val sy = toSy(wp.outer[i + 1])
                    if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                }
                pathReuse.close()
                for (inner in wp.inners) {
                    for (i in 0 until inner.size step 2) {
                        val sx = toSx(inner[i])
                        val sy = toSy(inner[i + 1])
                        if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                    }
                    pathReuse.close()
                }
                drawPath(pathReuse, color = Color(0xFFAAD3DF))
            }

            // 2. Waterways
            projectedMap?.waterLines?.forEach { wl ->
                if (wl.maxX < minVx || wl.minX > maxVx || wl.maxY < minVy || wl.minY > maxVy) return@forEach
                pathReuse.reset()
                for (i in 0 until wl.pts.size step 2) {
                    val sx = toSx(wl.pts[i])
                    val sy = toSy(wl.pts[i + 1])
                    if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                }
                drawPath(
                    pathReuse,
                    color = Color(0xFF8CBBD0),
                    style = Stroke(width = max(1.5f, 1.5.dp.toPx()))
                )
            }

            // 3. Roads
            if (showMajorRoads || showMinorRoads) {
                val visibleRoads = projectedMap?.roads?.filter { r ->
                    !(r.maxX < minVx || r.minX > maxVx || r.maxY < minVy || r.minY > maxVy)
                } ?: emptyList()

                val totalSegments = visibleRoads.sumOf { it.pts.size / 2 - 1 }
                if (totalSegments <= 4000) {
                    if (showMinorRoads) {
                        val minor = visibleRoads.filter { it.roadClass == RoadClass.MINOR }
                        // Outline
                        minor.forEach { r ->
                            pathReuse.reset()
                            for (i in 0 until r.pts.size step 2) {
                                val sx = toSx(r.pts[i]); val sy = toSy(r.pts[i + 1])
                                if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                            }
                            drawPath(pathReuse, Color(0xFFC9C5BD), style = Stroke(width = 3.dp.toPx()))
                        }
                        // Inner
                        minor.forEach { r ->
                            pathReuse.reset()
                            for (i in 0 until r.pts.size step 2) {
                                val sx = toSx(r.pts[i]); val sy = toSy(r.pts[i + 1])
                                if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                            }
                            drawPath(pathReuse, Color(0xFFFFFFFF), style = Stroke(width = 1.5.dp.toPx()))
                        }
                    }

                    if (showMajorRoads) {
                        val major = visibleRoads.filter { it.roadClass == RoadClass.MAJOR }
                        // Outline
                        major.forEach { r ->
                            pathReuse.reset()
                            for (i in 0 until r.pts.size step 2) {
                                val sx = toSx(r.pts[i]); val sy = toSy(r.pts[i + 1])
                                if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                            }
                            drawPath(pathReuse, Color(0xFFC9A24E), style = Stroke(width = 4.dp.toPx()))
                        }
                        // Inner
                        major.forEach { r ->
                            pathReuse.reset()
                            for (i in 0 until r.pts.size step 2) {
                                val sx = toSx(r.pts[i]); val sy = toSy(r.pts[i + 1])
                                if (i == 0) pathReuse.moveTo(sx, sy) else pathReuse.lineTo(sx, sy)
                            }
                            drawPath(pathReuse, Color(0xFFF7D488), style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
            }

            // 4. Place & Facility Labels collision tracking
            val drawnLabelBoxes = mutableListOf<Rect>()

            // Draw Places
            projectedMap?.places?.forEach { p ->
                if (p.x < minVx || p.x > maxVx || p.y < minVy || p.y > maxVy) return@forEach
                val kind = p.kind.lowercase()
                val allowed = if (showTownsOnly) {
                    kind == "city" || kind == "town"
                } else if (showVillages) {
                    kind == "city" || kind == "town" || kind == "village" || kind == "suburb"
                } else {
                    true
                }
                if (!allowed) return@forEach

                val sx = toSx(p.x); val sy = toSy(p.y)
                val textLayout = measurer.measure(
                    p.name,
                    TextStyle(color = Color(0xFF3C3C3C), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
                val tw = textLayout.size.width.toFloat()
                val th = textLayout.size.height.toFloat()
                val box = Rect(sx - tw / 2f, sy - th / 2f, sx + tw / 2f, sy + th / 2f)

                if (box.left >= 0 && box.right <= w && box.top >= 0 && box.bottom <= h) {
                    if (drawnLabelBoxes.none { it.overlaps(box) }) {
                        drawText(textLayout, topLeft = Offset(box.left, box.top))
                        drawnLabelBoxes.add(box)
                    }
                }
            }

            // Draw Facilities
            if (showFacilities) {
                projectedMap?.facilities?.forEach { pf ->
                    if (pf.x < minVx || pf.x > maxVx || pf.y < minVy || pf.y > maxVy) return@forEach
                    val sx = toSx(pf.x); val sy = toSy(pf.y)

                    when (pf.facility.kind) {
                        FacilityKind.HOSPITAL, FacilityKind.CLINIC -> {
                            val r = 6.dp.toPx()
                            drawCircle(Color.White, radius = r, center = Offset(sx, sy))
                            drawCircle(Color(0xFF2E7D32), radius = r, center = Offset(sx, sy), style = Stroke(1.dp.toPx()))
                            drawLine(
                                Color(0xFF2E7D32),
                                start = Offset(sx - 3.dp.toPx(), sy),
                                end = Offset(sx + 3.dp.toPx(), sy),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                Color(0xFF2E7D32),
                                start = Offset(sx, sy - 3.dp.toPx()),
                                end = Offset(sx, sy + 3.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        FacilityKind.SCHOOL -> {
                            val s = 8.dp.toPx()
                            drawRect(
                                Color(0xFF1976D2),
                                topLeft = Offset(sx - s / 2f, sy - s / 2f),
                                size = Size(s, s)
                            )
                        }
                    }

                    if (pf.facility.name != null) {
                        val textLayout = measurer.measure(
                            pf.facility.name,
                            TextStyle(color = Color(0xFF222222), fontSize = 10.sp, fontWeight = FontWeight.Normal)
                        )
                        val tw = textLayout.size.width.toFloat()
                        val th = textLayout.size.height.toFloat()
                        val box = Rect(sx + 8.dp.toPx(), sy - th / 2f, sx + 8.dp.toPx() + tw, sy + th / 2f)

                        if (box.left >= 0 && box.right <= w && box.top >= 0 && box.bottom <= h) {
                            if (drawnLabelBoxes.none { it.overlaps(box) }) {
                                drawText(textLayout, topLeft = Offset(box.left, box.top))
                                drawnLabelBoxes.add(box)
                            }
                        }
                    }
                }
            }

            // 5. Cluster Circles
            clusterPts.forEachIndexed { i, p ->
                val sx = toSx(p.x); val sy = toSy(p.y)
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
                drawCircle(
                    color = Color(0xFF3A1A1A),
                    radius = r,
                    center = centre,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                if (sel == i) {
                    drawCircle(
                        color = Color.White, radius = r + 4.dp.toPx(),
                        center = centre, style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 6. Cluster Rank Numbers
            clusterPts.forEachIndexed { i, p ->
                val sx = toSx(p.x); val sy = toSy(p.y)
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

            // 7. Scale bar & Attribution
            val metres = 100.dp.toPx() / scale
            val nice = NICE_METRES.lastOrNull { it <= metres } ?: NICE_METRES.first()
            val x0 = 16.dp.toPx()
            val y0 = h - 16.dp.toPx()
            drawLine(
                color = Color(0xFF333333), start = Offset(x0, y0),
                end = Offset(x0 + nice * scale, y0), strokeWidth = 3.dp.toPx()
            )
            val text = if (nice >= 1000) "${nice / 1000} km" else "$nice m"
            val l = measurer.measure(text, TextStyle(color = Color(0xFF333333), fontSize = 12.sp))
            drawText(l, topLeft = Offset(x0, y0 - l.size.height - 4.dp.toPx()))

            val attr = measurer.measure("(c) OpenStreetMap contributors", TextStyle(color = Color(0xFF555555), fontSize = 10.sp))
            drawText(attr, topLeft = Offset(w - attr.size.width - 12.dp.toPx(), h - attr.size.height - 8.dp.toPx()))
        }

        Spacer(Modifier.height(8.dp))
        if (sel != null) {
            val selectedCluster = clusters[sel]
            val nearest = if (baseMapData != null && baseMapData.facilities.isNotEmpty()) {
                nearestFacility(
                    selectedCluster.centerLat,
                    selectedCluster.centerLon,
                    baseMapData.facilities,
                    setOf(FacilityKind.HOSPITAL, FacilityKind.CLINIC)
                )
            } else null

            LiteClusterCard(sel + 1, selectedCluster, nearest)
        } else {
            Text(
                if (noData) "No basemap data. See docs/lite-map-data.md"
                else "Lite offline map pack. Shows water, roads, towns, hospitals and schools for prepared areas only.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LiteClusterCard(
    rank: Int,
    c: Cluster,
    nearestHospital: Pair<Facility, Double>?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("#$rank  ${c.headline()}", style = MaterialTheme.typography.titleMedium)
            Text("Deepest water: ${waterName(c.deepestWater)}  |  top score ${c.topScore.toInt()}")
            Text(c.needsSummary())
            Text(
                String.format(Locale.US, "%.4f, %.4f", c.centerLat, c.centerLon),
                style = MaterialTheme.typography.bodySmall
            )
            if (nearestHospital != null) {
                val name = nearestHospital.first.name ?: "unnamed"
                val distM = nearestHospital.second
                val distStr = if (distM >= 1000.0) {
                    String.format(Locale.US, "%.1f km", distM / 1000.0)
                } else {
                    "${distM.toInt()} m"
                }
                Text(
                    "Nearest hospital: $name, $distStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
