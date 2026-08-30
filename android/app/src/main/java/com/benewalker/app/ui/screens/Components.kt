package com.benewalker.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.service.GpsPoint
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val routeJsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseGpsRoute(routeJson: String?): List<GpsPoint> {
    if (routeJson.isNullOrBlank()) return emptyList()
    return try {
        routeJsonParser.decodeFromString<List<GpsPoint>>(routeJson)
    } catch (_: Exception) {
        emptyList()
    }
}

fun formatSecToMinSec(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d min", m, s)
}

fun formatSecDetailed(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun StatCardsGrid(
    state: WalkUiState,
    onAnalyticsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Heute
            val todayTotal = state.todayRecord?.totalSeconds ?: 0
            val morningDone = (state.todayRecord?.morningSeconds ?: 0) > 0
            val eveningDone = (state.todayRecord?.eveningSeconds ?: 0) > 0
            val sessionStatus = when {
                morningDone && eveningDone -> "2 von 2 Einheiten"
                morningDone || eveningDone -> "1 von 2 Einheiten"
                else -> "Gesamtzeit"
            }
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Heute",
                value = formatSecDetailed(todayTotal),
                subtitle = sessionStatus,
                icon = Icons.Outlined.Today,
                onClick = onAnalyticsClick
            )

            // 2. 7-Tage Schnitt
            val diff7 = state.diff7DaysSec
            val diff7Text = when {
                diff7 > 0 -> "+${formatSecToMinSec(diff7)}"
                diff7 < 0 -> "-${formatSecToMinSec(-diff7)}"
                else -> "±0 min"
            }
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "7-Tage Schnitt",
                value = formatSecDetailed(state.avg7DaysSec),
                changeText = diff7Text,
                isPositive = if (diff7 > 0) true else if (diff7 < 0) false else null,
                subtitle = "vs. Vorwoche",
                icon = Icons.Outlined.Timeline,
                onClick = onAnalyticsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 3. 30-Tage Schnitt
            val diff30 = state.diff30DaysSec
            val diff30Text = when {
                diff30 > 0 -> "+${formatSecToMinSec(diff30)}"
                diff30 < 0 -> "-${formatSecToMinSec(-diff30)}"
                else -> "±0 min"
            }
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "30-Tage Schnitt",
                value = formatSecDetailed(state.avg30DaysSec),
                changeText = diff30Text,
                isPositive = if (diff30 > 0) true else if (diff30 < 0) false else null,
                subtitle = "vs. Vormonat",
                icon = Icons.Outlined.DateRange,
                onClick = onAnalyticsClick
            )

            // 4. Rekord am Stück
            val recordDiff = state.todayMaxSingleSec - state.allTimeSingleRecordSec
            val recordChangeText = when {
                state.todayMaxSingleSec >= state.allTimeSingleRecordSec && state.todayMaxSingleSec > 0 -> "★ Rekord!"
                state.todayMaxSingleSec > 0 -> "-${formatSecToMinSec(-recordDiff)}"
                else -> "±0 min"
            }
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Rekord am Stück",
                value = formatSecDetailed(state.allTimeSingleRecordSec),
                changeText = recordChangeText,
                isPositive = if (recordDiff >= 0 && state.todayMaxSingleSec > 0) true else null,
                subtitle = "heute",
                icon = Icons.Outlined.EmojiEvents,
                onClick = onAnalyticsClick
            )
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    changeText: String? = null,
    subtitle: String,
    icon: ImageVector,
    isPositive: Boolean? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            
            if (changeText != null) {
                val badgeColor = when (isPositive) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.outline
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = badgeColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = changeText,
                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WalkEntryFormCard(
    state: WalkUiState,
    viewModel: WalkViewModel
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Titel und Datumsauswahl
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Gehzeit erfassen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Datumswähler Feld
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val parts = state.formDate.split("-")
                                    val y = parts.getOrNull(0)?.toIntOrNull() ?: LocalDate.now().year
                                    val m = (parts.getOrNull(1)?.toIntOrNull() ?: LocalDate.now().monthValue) - 1
                                    val d = parts.getOrNull(2)?.toIntOrNull() ?: LocalDate.now().dayOfMonth

                                    DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                        val selected = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                        viewModel.setFormDate(selected)
                                    }, y, m, d).show()
                                }
                        ) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Datum", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(state.formDate, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Quick Buttons (Heute / Gestern)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                            FilterChip(
                                selected = state.formDate == todayStr,
                                onClick = { viewModel.setFormDate(todayStr) },
                                label = { Text("Heute", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                                shape = RoundedCornerShape(10.dp)
                            )
                            FilterChip(
                                selected = state.formDate == yesterdayStr,
                                onClick = { viewModel.setFormDate(yesterdayStr) },
                                label = { Text("Gestern", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 1. Gehen Block (Primary)
            AndroidM3SessionInput(
                title = "1. Gehen",
                subtitle = "Vormittag / 1. Einheit",
                icon = Icons.Outlined.WbSunny,
                themeColor = MaterialTheme.colorScheme.primary,
                min = state.morningMin,
                sec = state.morningSec,
                onMinChange = { viewModel.updateFormFields(morningMin = it) },
                onSecChange = { viewModel.updateFormFields(morningSec = it) },
                onAddSeconds = { viewModel.addQuickSeconds("morning", it) },
                onClear = { viewModel.clearFormField("morning") }
            )

            // 2. Gehen Block (Tertiary)
            AndroidM3SessionInput(
                title = "2. Gehen",
                subtitle = "Nachmittag / 2. Einheit",
                icon = Icons.Outlined.NightsStay,
                themeColor = MaterialTheme.colorScheme.tertiary,
                min = state.eveningMin,
                sec = state.eveningSec,
                onMinChange = { viewModel.updateFormFields(eveningMin = it) },
                onSecChange = { viewModel.updateFormFields(eveningSec = it) },
                onAddSeconds = { viewModel.addQuickSeconds("evening", it) },
                onClear = { viewModel.clearFormField("evening") }
            )

            // Speichern Action Button
            Button(
                onClick = { viewModel.saveForm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.formSuccessFeedback) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("✓ Erfolgreich gespeichert!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gehzeit speichern", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AndroidM3SessionInput(
    title: String,
    subtitle: String,
    icon: ImageVector,
    themeColor: Color,
    min: String,
    sec: String,
    onMinChange: (String) -> Unit,
    onSecChange: (String) -> Unit,
    onAddSeconds: (Int) -> Unit,
    onClear: () -> Unit
) {
    val totalSec = (min.toIntOrNull() ?: 0) * 60 + (sec.toIntOrNull() ?: 0)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header mit Titel und Gesamtdauer-Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = themeColor, modifier = Modifier.size(20.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Bold, color = themeColor, fontSize = 14.sp, maxLines = 1)
                        Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = themeColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = formatSecToMinSec(totalSec),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor,
                        maxLines = 1
                    )
                }
            }

            // Standard Outlined TextFields (Minuten & Sekunden)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = min,
                    onValueChange = onMinChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Minuten", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text("0") },
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    suffix = { Text("min", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        focusedLabelColor = themeColor,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
                OutlinedTextField(
                    value = sec,
                    onValueChange = onSecChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Sekunden", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text("0") },
                    leadingIcon = { Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    suffix = { Text("sek", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        focusedLabelColor = themeColor,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            }

            // Material 3 Quick Add Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(15 to "+15s", 30 to "+30s", 45 to "+45s", 60 to "+1m").forEach { (s, label) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAddSeconds(s) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onClear() },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Leeren", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun GarminBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF1B2329)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.benewalker.app.R.drawable.ic_garmin),
                contentDescription = "Garmin",
                tint = Color.Unspecified,
                modifier = Modifier.height(9.dp)
            )
        }
    }
}

@Composable
fun HistoryItemCard(
    record: WalkRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val morningPoints = remember(record.morningRouteJson) { parseGpsRoute(record.morningRouteJson) }
    val eveningPoints = remember(record.eveningRouteJson) { parseGpsRoute(record.eveningRouteJson) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Header: Date, Garmin badge, Total time & Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (record.source == "garmin_health_connect") {
                        GarminBadge()
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = formatSecDetailed(record.totalSeconds),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Edit Button
                    FilledTonalIconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Bearbeiten",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete Button
                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Löschen",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Training Session 1: 1. Gehen (Vormittag)
            if (record.morningSeconds > 0) {
                TrainingSessionCard(
                    title = "1. Gehen (Vormittag)",
                    icon = Icons.Outlined.WbSunny,
                    chipColor = MaterialTheme.colorScheme.primaryContainer,
                    chipTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    durationSec = record.morningSeconds,
                    distanceMeters = record.morningDistanceMeters,
                    routePoints = morningPoints
                )
            }

            // Training Session 2: 2. Gehen (Nachmittag)
            if (record.eveningSeconds > 0) {
                TrainingSessionCard(
                    title = "2. Gehen (Nachmittag)",
                    icon = Icons.Outlined.NightsStay,
                    chipColor = MaterialTheme.colorScheme.tertiaryContainer,
                    chipTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    durationSec = record.eveningSeconds,
                    distanceMeters = record.eveningDistanceMeters,
                    routePoints = eveningPoints
                )
            }

            if (record.morningSeconds == 0 && record.eveningSeconds == 0) {
                Text(
                    text = "Keine Einheiten an diesem Tag",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun TrainingSessionCard(
    title: String,
    icon: ImageVector,
    chipColor: Color,
    chipTextColor: Color,
    durationSec: Int,
    distanceMeters: Double,
    routePoints: List<GpsPoint>
) {
    val distanceKm = distanceMeters / 1000.0
    val avgSpeedKmh = if (durationSec > 0 && distanceMeters > 0) (distanceKm / (durationSec / 3600.0)) else 0.0
    val avgPaceMinPerKm = if (distanceKm > 0.05 && durationSec > 0) (durationSec / 60.0) / distanceKm else 0.0
    val paceMin = avgPaceMinPerKm.toInt()
    val paceSec = ((avgPaceMinPerKm - paceMin) * 60).toInt().coerceIn(0, 59)
    val paceFormatted = if (avgPaceMinPerKm in 2.0..30.0) String.format(java.util.Locale.GERMAN, "%02d:%02d /km", paceMin, paceSec) else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title & Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = chipTextColor)
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = chipColor
                ) {
                    Text(
                        text = formatSecToMinSec(durationSec),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipTextColor
                    )
                }
            }

            // Metrics row (Distance, Pace, Speed)
            if (distanceKm > 0.01) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Distance
                    SessionSmallMetric(
                        modifier = Modifier.weight(1f),
                        label = "Distanz",
                        value = String.format(java.util.Locale.GERMAN, "%.2f km", distanceKm)
                    )

                    // Pace
                    if (paceFormatted != null) {
                        SessionSmallMetric(
                            modifier = Modifier.weight(1f),
                            label = "Ø Pace",
                            value = paceFormatted
                        )
                    }

                    // Speed
                    SessionSmallMetric(
                        modifier = Modifier.weight(1f),
                        label = "Ø Tempo",
                        value = String.format(java.util.Locale.GERMAN, "%.1f km/h", avgSpeedKmh)
                    )
                }
            }

            // Speed-Colored OpenStreetMap Route Snippet or Grey Placeholder Map
            if (routePoints.size >= 2) {
                OsmRouteSnippetMap(
                    routePoints = routePoints,
                    height = 150.dp,
                    allowFullscreen = true
                )
            } else {
                GreyPlaceholderMap(height = 110.dp)
            }
        }
    }
}

@Composable
fun GreyPlaceholderMap(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 110.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 28.dp.toPx()
            val lineColor = Color.Gray.copy(alpha = 0.15f)
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += step
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Keine GPS-Strecke aufgezeichnet",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionSmallMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
