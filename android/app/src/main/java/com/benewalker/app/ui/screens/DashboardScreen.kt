package com.benewalker.app.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.ui.HcStatus
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.theme.AmberMorning
import com.benewalker.app.ui.theme.IndigoEvening
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
fun DashboardScreen(
    viewModel: WalkViewModel,
    onNavigateToStopwatch: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Health Connect Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.healthConnectManager.createPermissionContract()
    ) { grantedPermissions ->
        viewModel.checkHealthConnectStatus()
        if (viewModel.healthConnectManager.walkingPermissions.all { it in grantedPermissions }) {
            viewModel.syncWithHealthConnect()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Garmin & Health Connect Auto-Sync Card
        item {
            HealthConnectCard(
                state = uiState,
                onSyncClick = { viewModel.syncWithHealthConnect() },
                onRequestPermission = {
                    permissionLauncher.launch(viewModel.healthConnectManager.walkingPermissions)
                }
            )
        }

        // 2. Stat Cards Grid (4 Metriken)
        item {
            StatCardsGrid(
                state = uiState,
                onAnalyticsClick = onNavigateToAnalytics
            )
        }

        // 3. Eintrags-Formular (1. & 2. Gehen)
        item {
            WalkEntryFormCard(
                state = uiState,
                viewModel = viewModel
            )
        }

        // 4. Historie Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Verlauf (${uiState.records.size} Tage)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Historie Liste
        if (uiState.records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine Gehzeiten erfasst.\nTrage oben deine erste Einheit ein oder synchronisiere mit Garmin!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(uiState.records, key = { it.date }) { record ->
                HistoryItemCard(
                    record = record,
                    onEdit = { viewModel.setFormDate(record.date) },
                    onDelete = { viewModel.deleteRecord(record.date) }
                )
            }
        }
    }
}

@Composable
fun HealthConnectCard(
    state: WalkUiState,
    onSyncClick: () -> Unit,
    onRequestPermission: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsWalk,
                        contentDescription = "Garmin",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Garmin Auto-Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = when (state.hcStatus) {
                            HcStatus.SYNCING -> "Synchronisiert Geheinheiten..."
                            HcStatus.PERMISSION_NEEDED -> "Berechtigung für Health Connect fehlt"
                            HcStatus.UNAVAILABLE -> "Health Connect nicht verfügbar"
                            HcStatus.ERROR -> state.syncErrorMessage ?: "Sync-Fehler"
                            else -> if (state.lastSyncTime != null) "Aktiv • Nur Aktivität \"Gehen\"" else "Bereit zum Synchronisieren"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            when (state.hcStatus) {
                HcStatus.PERMISSION_NEEDED -> {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Erlauben", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HcStatus.SYNCING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                }
                else -> {
                    FilledTonalButton(
                        onClick = onSyncClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCardsGrid(
    state: WalkUiState,
    onAnalyticsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Heute vs. Vortag
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Heute vs. Vortag",
                value = if (state.todayVsYesterdayDiffSec >= 0) "+${formatSecDetailed(state.todayVsYesterdayDiffSec)}" else "-${formatSecDetailed(-state.todayVsYesterdayDiffSec)}",
                subtitle = "Tagesdifferenz",
                isPositive = state.todayVsYesterdayDiffSec >= 0,
                onClick = onAnalyticsClick
            )

            // 7-Tage Schnitt
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "7-Tage Schnitt",
                value = formatSecDetailed(state.avg7DaysSec),
                subtitle = "Ø pro Tag",
                onClick = onAnalyticsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 30-Tage Schnitt
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "30-Tage Schnitt",
                value = formatSecDetailed(state.avg30DaysSec),
                subtitle = "Ø pro Tag",
                onClick = onAnalyticsClick
            )

            // Rekord am Stück
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Rekord am Stück",
                value = formatSecDetailed(state.allTimeSingleRecordSec),
                subtitle = "All-Time Rekord",
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
    subtitle: String,
    isPositive: Boolean? = null,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPositive == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Form Header mit Datum
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gehzeit eintragen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Quick Date Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            viewModel.setFormDate(today)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Heute", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            viewModel.setFormDate(yesterday)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Gestern", fontSize = 11.sp)
                    }
                }
            }

            // Datumsauswahl Feld
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val parts = state.formDate.split("-")
                        val y = parts.getOrNull(0)?.toIntOrNull() ?: LocalDate.now().year
                        val m = (parts.getOrNull(1)?.toIntOrNull() ?: LocalDate.now().monthValue) - 1
                        val d = parts.getOrNull(2)?.toIntOrNull() ?: LocalDate.now().dayOfMonth

                        DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            val selected = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                            viewModel.setFormDate(selected)
                        }, y, m, d).show()
                    },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = "Datum", modifier = Modifier.size(18.dp))
                        Text(text = "Datum: ${state.formDate}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }

            // 1. Gehen Box
            WalkSessionInputBox(
                title = "1. Gehen",
                accentColor = AmberMorning,
                min = state.morningMin,
                sec = state.morningSec,
                onMinChange = { viewModel.updateFormFields(morningMin = it) },
                onSecChange = { viewModel.updateFormFields(morningSec = it) },
                onAddSeconds = { viewModel.addQuickSeconds("morning", it) },
                onClear = { viewModel.clearFormField("morning") }
            )

            // 2. Gehen Box
            WalkSessionInputBox(
                title = "2. Gehen",
                accentColor = IndigoEvening,
                min = state.eveningMin,
                sec = state.eveningSec,
                onMinChange = { viewModel.updateFormFields(eveningMin = it) },
                onSecChange = { viewModel.updateFormFields(eveningSec = it) },
                onAddSeconds = { viewModel.addQuickSeconds("evening", it) },
                onClear = { viewModel.clearFormField("evening") }
            )

            // Speichern Button
            Button(
                onClick = { viewModel.saveForm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.formSuccessFeedback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.formSuccessFeedback) {
                    Icon(Icons.Filled.Check, contentDescription = "Gespeichert", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("✓ Gespeichert!", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = "Speichern", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gehzeit speichern", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WalkSessionInputBox(
    title: String,
    accentColor: Color,
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
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, color = accentColor, fontSize = 14.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = formatSecToMinSec(totalSec),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = min,
                    onValueChange = onMinChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Minuten", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = sec,
                    onValueChange = onSecChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Sekunden", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Quick Add Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(15 to "+15s", 30 to "+30s", 45 to "+45s", 60 to "+1m").forEach { (s, label) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAddSeconds(s) },
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .width(28.dp)
                        .clickable { onClear() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = "0", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    record: WalkRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (record.source == "garmin_health_connect") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Garmin",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (record.morningSeconds > 0) {
                        Text(
                            text = "1. Gehen: ${formatSecToMinSec(record.morningSeconds)}",
                            fontSize = 12.sp,
                            color = AmberMorning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (record.eveningSeconds > 0) {
                        Text(
                            text = "2. Gehen: ${formatSecToMinSec(record.eveningSeconds)}",
                            fontSize = 12.sp,
                            color = IndigoEvening,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatSecDetailed(record.totalSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "Gesamt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Löschen", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
