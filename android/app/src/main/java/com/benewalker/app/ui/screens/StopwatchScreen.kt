package com.benewalker.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.benewalker.app.ui.WalkViewModel
import java.util.Locale

@Composable
fun StopwatchScreen(
    viewModel: WalkViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPerm = fine || coarse
        viewModel.setLocationPermissionGranted(hasPerm)
        if (!hasPerm) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val hours = uiState.stopwatchElapsedSec / 3600
    val minutes = (uiState.stopwatchElapsedSec % 3600) / 60
    val seconds = uiState.stopwatchElapsedSec % 60
    val formattedTime = if (hours > 0) {
        String.format(Locale.GERMAN, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.GERMAN, "%02d:%02d", minutes, seconds)
    }

    val distanceKm = uiState.gpsDistanceMeters / 1000.0
    val formattedDistance = String.format(Locale.GERMAN, "%.2f km", distanceKm)

    val paceMin = uiState.gpsAvgPaceMinPerKm.toInt()
    val paceSec = ((uiState.gpsAvgPaceMinPerKm - paceMin) * 60).toInt().coerceIn(0, 59)
    val formattedPace = if (uiState.gpsAvgPaceMinPerKm in 2.0..30.0) {
        String.format(Locale.GERMAN, "%02d:%02d /km", paceMin, paceSec)
    } else {
        "--:-- /km"
    }

    val formattedSpeed = String.format(Locale.GERMAN, "%.1f km/h", uiState.gpsAvgSpeedKmh)

    var splitsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Row: Target Selector on Left | Minimalist Sound Toggles (5min/1min, 30s) & Speaker on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Target Selector (1. Gehen / 2. Gehen)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { viewModel.setStopwatchTarget("morning") },
                    shape = RoundedCornerShape(9.dp),
                    color = if (uiState.stopwatchTarget == "morning") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (uiState.stopwatchTarget == "morning") {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            text = "1. Gehen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (uiState.stopwatchTarget == "morning") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = { viewModel.setStopwatchTarget("evening") },
                    shape = RoundedCornerShape(9.dp),
                    color = if (uiState.stopwatchTarget == "evening") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (uiState.stopwatchTarget == "evening") {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(
                            text = "2. Gehen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (uiState.stopwatchTarget == "evening") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Unified Sound Controls Card [ 1min/5min | 30s | 🔊/🔇 ]
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Intervall (1min / 5min)
                Surface(
                    onClick = {
                        val next = if (uiState.stopwatchVoiceIntervalMin == 1) 5 else 1
                        viewModel.setStopwatchVoiceInterval(next)
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = if (uiState.stopwatchSoundEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = "${uiState.stopwatchVoiceIntervalMin}min",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.stopwatchSoundEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                    )
                }

                // Button 2: 30s Signal
                Surface(
                    onClick = { viewModel.setStopwatchBeep30s(!uiState.stopwatchBeep30s) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (uiState.stopwatchSoundEnabled && uiState.stopwatchBeep30s) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = "30s",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.stopwatchSoundEnabled && uiState.stopwatchBeep30s) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                    )
                }

                // Button 3: Lautsprecher An/Aus
                Surface(
                    onClick = { viewModel.setStopwatchSoundEnabled(!uiState.stopwatchSoundEnabled) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (uiState.stopwatchSoundEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.stopwatchSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Audio",
                            modifier = Modifier.size(15.dp),
                            tint = if (uiState.stopwatchSoundEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Open-Source OpenStreetMap Map (Standard Medium Height 205dp)
        OsmTrainingMap(
            modifier = Modifier.height(205.dp),
            routePoints = uiState.gpsRoutePoints,
            currentLat = uiState.gpsCurrentLat,
            currentLon = uiState.gpsCurrentLon,
            accuracy = uiState.gpsAccuracy,
            isTracking = uiState.stopwatchRunning,
            hasLocationPermission = uiState.hasLocationPermission,
            onPermissionRequested = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        // 4 Uniform Equal-Sized Telemetry Cards in 2x2 Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: GEHZEIT
            UniformTelemetryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timer,
                title = "GEHZEIT",
                value = formattedTime,
                subtitle = if (uiState.stopwatchRunning) "Aufzeichnung aktiv" else "Bereit",
                highlighted = uiState.stopwatchRunning
            )

            // Card 2: DISTANZ
            UniformTelemetryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Straighten,
                title = "DISTANZ",
                value = formattedDistance,
                subtitle = "${uiState.gpsRoutePoints.size} GPS-Punkte"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 3: PACE
            UniformTelemetryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Speed,
                title = "Ø PACE",
                value = formattedPace,
                subtitle = "Minuten / km"
            )

            // Card 4: TEMPO
            UniformTelemetryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.ElectricBolt,
                title = "Ø TEMPO",
                value = formattedSpeed,
                subtitle = "Kilometer / Std."
            )
        }

        // Kilometer & Zeitvergleich (Splits) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { splitsExpanded = !splitsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QueryStats,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Kilometer & Zeitvergleich",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.kilometerSplits.isEmpty())
                                    "Aktuell: ${(distanceKm * 1000).toInt()}m / 1000m"
                                else
                                    "${uiState.kilometerSplits.size} km abgeschlossen",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(onClick = { splitsExpanded = !splitsExpanded }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (splitsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Splits aufklappen",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val currentKmFraction = (distanceKm - distanceKm.toInt()).toFloat()
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { currentKmFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                AnimatedVisibility(
                    visible = splitsExpanded || uiState.kilometerSplits.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (uiState.kilometerSplits.isEmpty()) {
                            Text(
                                text = "Sobald 1 km erreicht ist, erscheint hier der Zeitvergleich für jeden Kilometer.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        } else {
                            uiState.kilometerSplits.forEach { split ->
                                val splitMin = split.splitSeconds / 60
                                val splitSec = split.splitSeconds % 60
                                val timeFormatted = String.format(Locale.GERMAN, "%02d:%02d", splitMin, splitSec)

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(7.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("KM ${split.kmNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("$timeFormatted Min.", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("${split.paceString} /km", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Reset, Play/Pause, Save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedIconButton(
                onClick = { viewModel.resetStopwatch() },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                enabled = uiState.stopwatchElapsedSec > 0
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Zurücksetzen")
            }

            FilledIconButton(
                onClick = { viewModel.toggleStopwatch() },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (uiState.stopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (uiState.stopwatchRunning) "Pause" else "Start",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Save Button (Opens confirmation dialog to choose 1. or 2. Gehen)
            FilledTonalIconButton(
                onClick = { showSaveConfirmDialog = true },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                enabled = uiState.stopwatchElapsedSec > 0
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "Speichern")
            }
        }
    }

    // Save Selection Dialog (Training 1 / Training 2 Choice)
    if (showSaveConfirmDialog) {
        val todayRecord = uiState.todayRecord
        val morningSec = todayRecord?.morningSeconds ?: 0
        val eveningSec = todayRecord?.eveningSeconds ?: 0

        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = {
                Text(
                    text = "Training speichern",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Dieses Training ($formattedTime${if (distanceKm > 0.01) ", $formattedDistance" else ""}) als Einheit speichern:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: 1. Gehen (Nur anbieten wenn 1. Gehen noch frei ist, oder beide bereits belegt sind)
                    if (morningSec == 0 || eveningSec > 0) {
                        Surface(
                            onClick = {
                                viewModel.saveStopwatchToToday(targetChoice = "morning")
                                showSaveConfirmDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Als 1. Gehen speichern", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    if (morningSec > 0) {
                                        Text("Bereits: ${morningSec / 60}m ${morningSec % 60}s (wird überschrieben)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                    } else {
                                        Text("Noch frei für heute", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Option 2: 2. Gehen
                    Surface(
                        onClick = {
                            viewModel.saveStopwatchToToday(targetChoice = "evening")
                            showSaveConfirmDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Als 2. Gehen speichern", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                if (eveningSec > 0) {
                                    Text("Bereits: ${eveningSec / 60}m ${eveningSec % 60}s (wird überschrieben)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                } else {
                                    Text(if (morningSec > 0) "1. Gehen abgeschlossen • Jetzt 2. Gehen sichern" else "Noch frei für heute", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun UniformTelemetryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    highlighted: Boolean = false
) {
    Card(
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 9.5.sp,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}
