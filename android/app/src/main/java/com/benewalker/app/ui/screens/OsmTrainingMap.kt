package com.benewalker.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.benewalker.app.service.GpsPoint
import com.benewalker.app.service.splitRouteSegments
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun createBlueLocationBitmap(context: Context): Bitmap {
    val sizePx = (24 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer subtle translucent aura
    paint.color = AndroidColor.parseColor("#40007AFF")
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    // White ring
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2.5f, paint)

    // Bright Blue solid center
    paint.color = AndroidColor.parseColor("#007AFF")
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 5f, paint)

    return bitmap
}

@Composable
fun OsmTrainingMap(
    modifier: Modifier = Modifier,
    routePoints: List<GpsPoint>,
    currentLat: Double?,
    currentLon: Double?,
    accuracy: Float,
    isTracking: Boolean,
    onPermissionRequested: (() -> Unit)? = null,
    hasLocationPermission: Boolean = true
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    var followLocation by remember { mutableStateOf(true) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val polylineOverlays = remember { mutableListOf<Polyline>() }
    var hasCenteredInitially by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_prefs", 0))
    }

    // Reactively update Polylines per segment and center on position
    LaunchedEffect(routePoints, currentLat, currentLon, followLocation) {
        val mapView = mapViewRef ?: return@LaunchedEffect

        // Clean up previous polylines
        polylineOverlays.forEach { mapView.overlayManager.remove(it) }
        polylineOverlays.clear()

        val segments = splitRouteSegments(routePoints)
        val locIndex = locationOverlayRef?.let { mapView.overlayManager.indexOf(it) } ?: -1

        for (seg in segments) {
            if (seg.size >= 2) {
                val poly = Polyline(mapView).apply {
                    outlinePaint.color = primaryColor
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(seg.map { GeoPoint(it.latitude, it.longitude) })
                }
                if (locIndex >= 0) {
                    mapView.overlayManager.add(locIndex, poly)
                } else {
                    mapView.overlayManager.add(poly)
                }
                polylineOverlays.add(poly)
            }
        }
        mapView.invalidate()

        if (currentLat != null && currentLon != null) {
            val currentGeo = GeoPoint(currentLat, currentLon)
            if (followLocation || !hasCenteredInitially) {
                mapView.controller.animateTo(currentGeo)
                if (!hasCenteredInitially) {
                    mapView.controller.setZoom(17.5)
                    hasCenteredInitially = true
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (!hasLocationPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "GPS-Standortzugriff erforderlich",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Erforderlich für Live-Karte und Kilometerberechnung.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onPermissionRequested?.invoke() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Standort erlauben", fontSize = 12.sp)
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(17.5)

                        // User Location Marker Overlay with Blue Dot
                        val provider = GpsMyLocationProvider(ctx)
                        val blueIcon = createBlueLocationBitmap(ctx)
                        val locOverlay = MyLocationNewOverlay(provider, this).apply {
                            enableMyLocation()
                            setDrawAccuracyEnabled(true)
                            setPersonIcon(blueIcon)
                            setDirectionIcon(blueIcon)
                            setPersonHotspot(blueIcon.width / 2f, blueIcon.height / 2f)
                            setDirectionArrow(blueIcon, blueIcon)
                            runOnFirstFix {
                                post {
                                    if (myLocation != null) {
                                        controller.animateTo(myLocation)
                                        controller.setZoom(17.5)
                                    }
                                }
                            }
                        }
                        overlayManager.add(locOverlay)
                        locationOverlayRef = locOverlay

                        if (currentLat != null && currentLon != null) {
                            val targetGeo = GeoPoint(currentLat, currentLon)
                            controller.setCenter(targetGeo)
                            hasCenteredInitially = true
                        } else {
                            controller.setCenter(GeoPoint(52.5200, 13.4050))
                        }

                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapViewRef = mapView
                    if (currentLat != null && currentLon != null && (followLocation || !hasCenteredInitially)) {
                        val currentGeo = GeoPoint(currentLat, currentLon)
                        mapView.controller.animateTo(currentGeo)
                        hasCenteredInitially = true
                    }
                }
            )

            // Top-left GPS Status pill directly inside map
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (currentLat != null) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (currentLat != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (currentLat != null) {
                            if (accuracy > 0) "GPS ±${accuracy.toInt()}m" else "GPS Aktiv"
                        } else "Suche GPS...",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Map Controls (Right Side: Zoom & Recenter)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom In
                SmallFloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                }

                // Zoom Out
                SmallFloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                }

                // Center Location Button
                SmallFloatingActionButton(
                    onClick = {
                        followLocation = true
                        val targetLoc = if (currentLat != null && currentLon != null) {
                            GeoPoint(currentLat, currentLon)
                        } else {
                            locationOverlayRef?.myLocation
                        }
                        if (targetLoc != null) {
                            mapViewRef?.controller?.animateTo(targetLoc)
                            mapViewRef?.controller?.setZoom(18.0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Zentrieren", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
