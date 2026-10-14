package com.resqedge.lite.ui.responder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.resqedge.lite.model.Cluster
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File
import kotlin.math.*

const val MAP_FILE_NAME = "region.map"

@Composable
fun StreetClusterMap(clusters: List<Cluster>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapsDir = remember { File(context.getExternalFilesDir("maps"), MAP_FILE_NAME) }
    var fileExists by remember { mutableStateOf(mapsDir.exists()) }
    var importMsg by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    mapsDir.parentFile?.mkdirs()
                    mapsDir.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                fileExists = mapsDir.exists()
                importMsg = "Imported (${mapsDir.length() / 1024 / 1024} MB)"
            } catch (e: Exception) {
                importMsg = "Error: ${e.message}"
            }
        }
    }

    if (!fileExists) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Offline map file not found", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please place region.map in:\nAndroid/data/com.resqedge.lite/files/maps/region.map\nor click below to import.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                    Text("Import map file")
                }
                if (importMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(importMsg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    val mapFile = remember { MapFile(mapsDir) }
    val mapBBox = remember { mapFile.boundingBox() }
    val coversArea = remember(clusters, mapBBox) {
        if (clusters.isEmpty()) true
        else clusters.all { c ->
            c.centerLat >= mapBBox.minLatitude && c.centerLat <= mapBBox.maxLatitude &&
            c.centerLon >= mapBBox.minLongitude && c.centerLon <= mapBBox.maxLongitude
        }
    }

    val measurer = rememberTextMeasurer()
    val maxScore = remember(clusters) { clusters.maxOfOrNull { it.topScore }?.coerceAtLeast(1.0) ?: 1.0 }
    val colorLow = Color(0xFFF9A825)
    val colorHigh = Color(0xFFB71C1C)
    val sel = selected?.takeIf { it < clusters.size }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                mapViewRef?.let { mv ->
                    if (clusters.isNotEmpty()) {
                        val first = clusters[0]
                        mv.model.mapViewPosition.setCenter(LatLong(first.centerLat, first.centerLon))
                        mv.model.mapViewPosition.zoomLevel = 16.toByte()
                    }
                }
            }) { Text("Zoom to top cluster") }
            OutlinedButton(onClick = {
                mapViewRef?.let { mv ->
                    if (clusters.isNotEmpty()) {
                        val latAvg = clusters.map { it.centerLat }.average()
                        val lonAvg = clusters.map { it.centerLon }.average()
                        mv.model.mapViewPosition.setCenter(LatLong(latAvg, lonAvg))
                        mv.model.mapViewPosition.zoomLevel = 12.toByte()
                    }
                }
            }) { Text("Fit all") }
        }
        if (!coversArea) {
            Text("Map file does not cover this area", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        isClickable = true
                        model.mapViewPosition.setCenter(LatLong(clusters.firstOrNull()?.centerLat ?: 9.5, clusters.firstOrNull()?.centerLon ?: 76.4))
                        model.mapViewPosition.zoomLevel = 12.toByte()

                        val tileCache = AndroidUtil.createTileCache(
                            ctx, "mapcache", model.displayModel.tileSize, 1f,
                            model.frameBufferModel.overdrawFactor
                        )
                        val tileRendererLayer = TileRendererLayer(
                            tileCache, mapFile, model.mapViewPosition, AndroidGraphicFactory.INSTANCE
                        ).apply {
                            setXmlRenderTheme(MapsforgeThemes.DEFAULT)
                        }
                        layerManager.layers.add(tileRendererLayer)
                        mapViewRef = this
                    }
                },
                onRelease = { mv ->
                    mv.destroy()
                }
            )

            val mv = mapViewRef
            if (mv != null) {
                var mapCenter by remember { mutableStateOf(mv.model.mapViewPosition.center) }
                var zoom by remember { mutableStateOf(mv.model.mapViewPosition.zoomLevel) }

                DisposableEffect(mv) {
                    val observer = Observer {
                        mapCenter = mv.model.mapViewPosition.center
                        zoom = mv.model.mapViewPosition.zoomLevel
                    }
                    mv.model.mapViewPosition.addObserver(observer)
                    onDispose {
                        mv.model.mapViewPosition.removeObserver(observer)
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(clusters, mv, mapCenter, zoom) {
                            detectTapGestures { tap ->
                                val pos = mv.model.mapViewPosition
                                val currentZoom = pos.zoomLevel
                                val tileSize = mv.model.displayModel.tileSize
                                val mapSize = MercatorProjection.getMapSize(currentZoom, tileSize)
                                val center = pos.center
                                val centerPxX = MercatorProjection.longitudeToPixelX(center.longitude, mapSize)
                                val centerPxY = MercatorProjection.latitudeToPixelY(center.latitude, mapSize)

                                var best = -1
                                var bestDist = Float.MAX_VALUE
                                clusters.forEachIndexed { i, c ->
                                    val ptPxX = MercatorProjection.longitudeToPixelX(c.centerLon, mapSize)
                                    val ptPxY = MercatorProjection.latitudeToPixelY(c.centerLat, mapSize)
                                    val sx = size.width / 2f + (ptPxX - centerPxX).toFloat()
                                    val sy = size.height / 2f + (ptPxY - centerPxY).toFloat()
                                    val d = hypot(sx - tap.x, sy - tap.y)
                                    if (d < bestDist) {
                                        bestDist = d
                                        best = i
                                    }
                                }
                                selected = if (best >= 0 && bestDist < 32.dp.toPx()) best else null
                            }
                        }
                ) {
                    val pos = mv.model.mapViewPosition
                    val currentZoom = pos.zoomLevel
                    val tileSize = mv.model.displayModel.tileSize
                    val mapSize = MercatorProjection.getMapSize(currentZoom, tileSize)
                    val center = pos.center
                    val centerPxX = MercatorProjection.longitudeToPixelX(center.longitude, mapSize)
                    val centerPxY = MercatorProjection.latitudeToPixelY(center.latitude, mapSize)
                    val w = size.width
                    val h = size.height

                    // Pass 1: Draw circles
                    clusters.forEachIndexed { i, c ->
                        val ptPxX = MercatorProjection.longitudeToPixelX(c.centerLon, mapSize)
                        val ptPxY = MercatorProjection.latitudeToPixelY(c.centerLat, mapSize)
                        val sx = w / 2f + (ptPxX - centerPxX).toFloat()
                        val sy = h / 2f + (ptPxY - centerPxY).toFloat()
                        if (sx < -60f || sx > w + 60f || sy < -60f || sy > h + 60f) return@forEachIndexed

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
                    clusters.forEachIndexed { i, c ->
                        val ptPxX = MercatorProjection.longitudeToPixelX(c.centerLon, mapSize)
                        val ptPxY = MercatorProjection.latitudeToPixelY(c.centerLat, mapSize)
                        val sx = w / 2f + (ptPxX - centerPxX).toFloat()
                        val sy = h / 2f + (ptPxY - centerPxY).toFloat()
                        if (sx < -60f || sx > w + 60f || sy < -60f || sy > h + 60f) return@forEachIndexed

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

                    // Attribution line at bottom-right
                    val attrText = "© OpenStreetMap contributors, ODbL"
                    val attrLayout = measurer.measure(attrText, TextStyle(color = Color.DarkGray, fontSize = 10.sp))
                    drawText(
                        attrLayout,
                        topLeft = Offset(w - attrLayout.size.width - 8.dp.toPx(), h - attrLayout.size.height - 4.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Offline street map from a map pack stored on this phone.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        if (sel != null) {
            ClusterCard(sel + 1, clusters[sel])
        }
    }
}
