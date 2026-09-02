package com.benewalker.app.ui.screens

import android.graphics.Paint
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.benewalker.app.service.GpsPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private fun getSpeedColor(speedKmh: Double): Color {
    return when {
        speedKmh < 3.2 -> Color(0xFF00E676) // Grün (Gemütlich / Langsam)
        speedKmh < 4.8 -> Color(0xFFFFD600) // Gelb (Normales Gehtempo)
        speedKmh < 6.2 -> Color(0xFFFF9100) // Orange (Zügiges Gehen)
        else -> Color(0xFFFF1744)           // Rot (Sehr schnell / Sprint)
    }
}

private fun getSpeedColorInt(speedKmh: Double): Int {
    return when {
        speedKmh < 3.2 -> android.graphics.Color.parseColor("#00E676")
        speedKmh < 4.8 -> android.graphics.Color.parseColor("#FFD600")
        speedKmh < 6.2 -> android.graphics.Color.parseColor("#FF9100")
        else -> android.graphics.Color.parseColor("#FF1744")
    }
}

/**
 * Ultra-lightweight, 120-FPS Compose Canvas Vector Preview for LazyColumn list items.
 * Renders instantaneously without creating heavy MapView instances in memory.
 */
@Composable
fun OsmRouteSnippetMap(
    modifier: Modifier = Modifier,
    routePoints: List<GpsPoint>,
    height: Dp = 150.dp,
    allowFullscreen: Boolean = true
) {
    if (routePoints.size < 2) return

    var showFullscreenDialog by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    // Sample down points to max 120 points for instant 0.1ms render
    val simplifiedPoints = remember(routePoints) {
        if (routePoints.size <= 120) {
            routePoints
        } else {
            val step = routePoints.size / 120.0
            val sampled = mutableListOf<GpsPoint>()
            var idx = 0.0
            while (idx < routePoints.size) {
                sampled.add(routePoints[idx.toInt()])
                idx += step
            }
            if (sampled.last() != routePoints.last()) {
                sampled.add(routePoints.last())
            }
            // Ensure resume points are preserved in sampled so pause gaps are never lost in preview
            for (p in routePoints) {
                if (p.isResumePoint && sampled.none { it.timestamp == p.timestamp }) {
                    val insertIdx = sampled.indexOfFirst { it.timestamp > p.timestamp }
                    if (insertIdx >= 0) sampled.add(insertIdx, p) else sampled.add(p)
                }
            }
            sampled
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
            .clickable { showFullscreenDialog = true }
    ) {
        // Fast Hardware-Accelerated Vector Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 24.dp.toPx()

            // 1. Subtle Map Grid Background
            val gridStep = 28.dp.toPx()
            val gridLineColor = Color.Gray.copy(alpha = 0.12f)
            var gx = 0f
            while (gx < w) {
                drawLine(gridLineColor, start = Offset(gx, 0f), end = Offset(gx, h), strokeWidth = 1.dp.toPx())
                gx += gridStep
            }
            var gy = 0f
            while (gy < h) {
                drawLine(gridLineColor, start = Offset(0f, gy), end = Offset(w, gy), strokeWidth = 1.dp.toPx())
                gy += gridStep
            }

            if (simplifiedPoints.size < 2) return@Canvas

            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            for (p in simplifiedPoints) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)

            val drawWidth = w - 2 * pad
            val drawHeight = h - 2 * pad

            val scaleX = drawWidth / lonSpan
            val scaleY = drawHeight / latSpan
            val scale = minOf(scaleX, scaleY)

            val offsetX = pad + (drawWidth - lonSpan * scale) / 2.0
            val offsetY = pad + (drawHeight - latSpan * scale) / 2.0

            fun toOffset(p: GpsPoint): Offset {
                val x = (offsetX + (p.longitude - minLon) * scale).toFloat()
                // Invert Y because latitude goes north (up) but screen Y goes down
                val y = (offsetY + (maxLat - p.latitude) * scale).toFloat()
                return Offset(x, y)
            }

            // 2. Draw Glow / Track Underlay
            val fullPath = Path()
            var hasMoved = false
            for (i in simplifiedPoints.indices) {
                val pt = toOffset(simplifiedPoints[i])
                val isGap = simplifiedPoints[i].isResumePoint || (i > 0 && (simplifiedPoints[i].timestamp - simplifiedPoints[i - 1].timestamp) > 25_000L)
                if (!hasMoved || isGap) {
                    fullPath.moveTo(pt.x, pt.y)
                    hasMoved = true
                } else {
                    fullPath.lineTo(pt.x, pt.y)
                }
            }

            // Shadow/Glow
            drawPath(
                path = fullPath,
                color = primaryColor.copy(alpha = 0.25f),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. Draw Speed-Colored Route Segments
            for (i in 0 until simplifiedPoints.size - 1) {
                val p1 = simplifiedPoints[i]
                val p2 = simplifiedPoints[i + 1]
                val isGap = p2.isResumePoint || ((p2.timestamp - p1.timestamp) > 25_000L)
                if (isGap) {
                    // Do NOT connect pre-pause point with post-pause point!
                    continue
                }
                val o1 = toOffset(p1)
                val o2 = toOffset(p2)

                val results = FloatArray(1)
                Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
                val distM = results[0]
                val timeSec = (p2.timestamp - p1.timestamp) / 1000.0
                val speedKmh = if (timeSec in 0.4..60.0 && distM > 0.3) (distM / timeSec) * 3.6 else 4.2

                drawLine(
                    color = getSpeedColor(speedKmh),
                    start = o1,
                    end = o2,
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Draw Start & Finish Indicators
            val startOff = toOffset(simplifiedPoints.first())
            val endOff = toOffset(simplifiedPoints.last())

            // Start Dot (Green)
            drawCircle(Color.White, radius = 6.5.dp.toPx(), center = startOff)
            drawCircle(Color(0xFF00E676), radius = 4.5.dp.toPx(), center = startOff)

            // Finish Dot (Red)
            drawCircle(Color.White, radius = 6.5.dp.toPx(), center = endOff)
            drawCircle(Color(0xFFFF1744), radius = 4.5.dp.toPx(), center = endOff)
        }

        // Top-Left Speed Legend Pill
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SpeedLegendDot(Color(0xFF00E676), "<3.2")
                SpeedLegendDot(Color(0xFFFFD600), "3-4.8")
                SpeedLegendDot(Color(0xFFFF9100), "4.8-6.2")
                SpeedLegendDot(Color(0xFFFF1744), ">6.2")
            }
        }

        // Top-Right Fullscreen / Map Open Action Pill
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clickable { showFullscreenDialog = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = "Karte öffnen",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Karte",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // Interactive OpenStreetMap dialog (only rendered on demand when user clicks)
    if (showFullscreenDialog) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            Configuration.getInstance().userAgentValue = context.packageName
        }

        Dialog(
            onDismissRequest = { showFullscreenDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
                                renderSpeedColoredRoute(this, routePoints)
                            }
                        }
                    )

                    // Close Button
                    IconButton(
                        onClick = { showFullscreenDialog = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }

                    // Legend at Bottom
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SpeedLegendDot(Color(0xFF00E676), "Gemütlich (<3.2 km/h)")
                            SpeedLegendDot(Color(0xFFFFD600), "Normal (3.2-4.8)")
                            SpeedLegendDot(Color(0xFFFF9100), "Zügig (4.8-6.2)")
                            SpeedLegendDot(Color(0xFFFF1744), "Sprint (>6.2)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedLegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun renderSpeedColoredRoute(mapView: MapView, routePoints: List<GpsPoint>) {
    if (routePoints.size < 2) return

    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE

    // Calculate speeds and draw colored segments
    for (i in 0 until routePoints.size - 1) {
        val p1 = routePoints[i]
        val p2 = routePoints[i + 1]
        val isGap = p2.isResumePoint || ((p2.timestamp - p1.timestamp) > 25_000L)

        minLat = minOf(minLat, p1.latitude, p2.latitude)
        maxLat = maxOf(maxLat, p1.latitude, p2.latitude)
        minLon = minOf(minLon, p1.longitude, p2.longitude)
        maxLon = maxOf(maxLon, p1.longitude, p2.longitude)

        if (isGap) {
            // Do NOT connect pre-pause point with post-pause point!
            continue
        }

        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        val distMeters = results[0]
        val timeSec = (p2.timestamp - p1.timestamp) / 1000.0

        val speedKmh = if (timeSec in 0.5..60.0 && distMeters > 0.5) {
            (distMeters / timeSec) * 3.6
        } else {
            4.2
        }

        val segmentPoly = Polyline(mapView).apply {
            outlinePaint.color = getSpeedColorInt(speedKmh)
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            setPoints(listOf(GeoPoint(p1.latitude, p1.longitude), GeoPoint(p2.latitude, p2.longitude)))
        }
        mapView.overlayManager.add(segmentPoly)
    }

    // Start Marker (Green Flag / Pin)
    val startMarker = Marker(mapView).apply {
        position = GeoPoint(routePoints.first().latitude, routePoints.first().longitude)
        title = "Start"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    }
    mapView.overlayManager.add(startMarker)

    // End Marker (Red Flag / Pin)
    val endMarker = Marker(mapView).apply {
        position = GeoPoint(routePoints.last().latitude, routePoints.last().longitude)
        title = "Ziel"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    }
    mapView.overlayManager.add(endMarker)

    // Zoom and center on bounding box
    val latPad = ((maxLat - minLat).coerceAtLeast(0.001)) * 0.18
    val lonPad = ((maxLon - minLon).coerceAtLeast(0.001)) * 0.18

    val box = BoundingBox(
        maxLat + latPad,
        maxLon + lonPad,
        minLat - latPad,
        minLon - lonPad
    )

    mapView.post {
        try {
            mapView.zoomToBoundingBox(box, false, 32)
        } catch (_: Exception) {
            val centerLat = (minLat + maxLat) / 2.0
            val centerLon = (minLon + maxLon) / 2.0
            mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
            mapView.controller.setZoom(16.0)
        }
    }
}
