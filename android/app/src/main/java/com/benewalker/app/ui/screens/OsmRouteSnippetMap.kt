package com.benewalker.app.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.benewalker.app.service.GpsPoint
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private fun getSpeedColor(speedKmh: Double): Int {
    return when {
        speedKmh < 3.2 -> AndroidColor.parseColor("#00E676") // Grün (Gemütlich / Langsam)
        speedKmh < 4.8 -> AndroidColor.parseColor("#FFD600") // Gelb (Normales Gehtempo)
        speedKmh < 6.2 -> AndroidColor.parseColor("#FF9100") // Orange (Zügiges Gehen)
        else -> AndroidColor.parseColor("#FF1744")           // Rot (Sehr schnell / Sprint)
    }
}

@Composable
fun OsmRouteSnippetMap(
    modifier: Modifier = Modifier,
    routePoints: List<GpsPoint>,
    height: Dp = 160.dp,
    allowFullscreen: Boolean = true
) {
    if (routePoints.size < 2) return

    val context = LocalContext.current
    var showFullscreenDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                    renderSpeedColoredRoute(this, routePoints)
                }
            }
        )

        // Speed Legend Pill (Top-Left)
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
                SpeedLegendDot(Color(0xFFFF1744), ">6.2 km/h")
            }
        }

        // Fullscreen Toggle Button (Top-Right)
        if (allowFullscreen) {
            SmallFloatingActionButton(
                onClick = { showFullscreenDialog = true },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Vollbild Karte", modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showFullscreenDialog) {
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

        minLat = minOf(minLat, p1.latitude, p2.latitude)
        maxLat = maxOf(maxLat, p1.latitude, p2.latitude)
        minLon = minOf(minLon, p1.longitude, p2.longitude)
        maxLon = maxOf(maxLon, p1.longitude, p2.longitude)

        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        val distMeters = results[0]
        val timeSec = (p2.timestamp - p1.timestamp) / 1000.0

        val speedKmh = if (timeSec in 0.5..60.0 && distMeters > 0.5) {
            (distMeters / timeSec) * 3.6
        } else {
            4.2 // Default walking speed fallback
        }

        val segmentPoly = Polyline(mapView).apply {
            outlinePaint.color = getSpeedColor(speedKmh)
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
