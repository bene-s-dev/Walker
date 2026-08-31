package com.benewalker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel

@Composable
fun DataScreen(
    viewModel: WalkViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var recordToDelete by remember { mutableStateOf<String?>(null) }
    var recordToEdit by remember { mutableStateOf<WalkRecord?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // 1. Eintrags-Formular (Neues Material 3 Design)
        item {
            WalkEntryFormCard(
                state = uiState,
                viewModel = viewModel
            )
        }

        // 2. Verlauf Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Verlauf",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "${uiState.records.size} Einträge",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Verlauf Liste
        if (uiState.records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine Gehzeiten eingetragen.\nNutze das Formular oben, um deine Einheiten zu speichern!",
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
                    onEdit = { recordToEdit = record },
                    onDelete = { recordToDelete = record.date }
                )
            }
        }
    }

    // Direktes Bearbeiten-Dialog (EditRecordDialog)
    if (recordToEdit != null) {
        val record = recordToEdit!!
        EditRecordDialog(
            record = record,
            onDismiss = { recordToEdit = null },
            onSave = { morningSec, eveningSec, morningDistMeters, eveningDistMeters ->
                viewModel.updateRecordDirectly(record.date, morningSec, eveningSec, morningDistMeters, eveningDistMeters)
                recordToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (recordToDelete != null) {
        val date = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Eintrag löschen?") },
            text = { Text("Möchtest du den Gehzeiten-Eintrag vom $date wirklich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecord(date)
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun EditRecordDialog(
    record: WalkRecord,
    onDismiss: () -> Unit,
    onSave: (morningSec: Int, eveningSec: Int, morningDistMeters: Double, eveningDistMeters: Double) -> Unit
) {
    var morningMin by remember { mutableStateOf(if (record.morningSeconds > 0) (record.morningSeconds / 60).toString() else "") }
    var morningSec by remember { mutableStateOf(if (record.morningSeconds > 0) (record.morningSeconds % 60).toString() else "") }
    var morningKm by remember { mutableStateOf(if (record.morningDistanceMeters > 0) String.format(java.util.Locale.GERMAN, "%.2f", record.morningDistanceMeters / 1000.0) else "") }
    var eveningMin by remember { mutableStateOf(if (record.eveningSeconds > 0) (record.eveningSeconds / 60).toString() else "") }
    var eveningSec by remember { mutableStateOf(if (record.eveningSeconds > 0) (record.eveningSeconds % 60).toString() else "") }
    var eveningKm by remember { mutableStateOf(if (record.eveningDistanceMeters > 0) String.format(java.util.Locale.GERMAN, "%.2f", record.eveningDistanceMeters / 1000.0) else "") }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Eintrag bearbeiten", fontWeight = FontWeight.Bold)
                Text(record.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Gehen Input
                AndroidM3SessionInput(
                    title = "1. Gehen",
                    subtitle = "Vormittag",
                    icon = Icons.Outlined.WbSunny,
                    themeColor = primaryColor,
                    min = morningMin,
                    sec = morningSec,
                    km = morningKm,
                    onMinChange = { morningMin = it },
                    onSecChange = { morningSec = it },
                    onKmChange = { morningKm = it },
                    onAddSeconds = { s ->
                        val curr = (morningMin.toIntOrNull() ?: 0) * 60 + (morningSec.toIntOrNull() ?: 0)
                        val next = (curr + s).coerceAtLeast(0)
                        morningMin = (next / 60).toString()
                        morningSec = (next % 60).toString()
                    },
                    onClear = {
                        morningMin = ""
                        morningSec = ""
                        morningKm = ""
                    }
                )

                // 2. Gehen Input
                AndroidM3SessionInput(
                    title = "2. Gehen",
                    subtitle = "Nachmittag",
                    icon = Icons.Outlined.NightsStay,
                    themeColor = tertiaryColor,
                    min = eveningMin,
                    sec = eveningSec,
                    km = eveningKm,
                    onMinChange = { eveningMin = it },
                    onSecChange = { eveningSec = it },
                    onKmChange = { eveningKm = it },
                    onAddSeconds = { s ->
                        val curr = (eveningMin.toIntOrNull() ?: 0) * 60 + (eveningSec.toIntOrNull() ?: 0)
                        val next = (curr + s).coerceAtLeast(0)
                        eveningMin = (next / 60).toString()
                        eveningSec = (next % 60).toString()
                    },
                    onClear = {
                        eveningMin = ""
                        eveningSec = ""
                        eveningKm = ""
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mSec = (morningMin.toIntOrNull() ?: 0) * 60 + (morningSec.toIntOrNull() ?: 0)
                    val eSec = (eveningMin.toIntOrNull() ?: 0) * 60 + (eveningSec.toIntOrNull() ?: 0)
                    val mKm = morningKm.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val eKm = eveningKm.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val mDistMeters = if (mKm > 0.0) mKm * 1000.0 else (if (mSec > 0) record.morningDistanceMeters else 0.0)
                    val eDistMeters = if (eKm > 0.0) eKm * 1000.0 else (if (eSec > 0) record.eveningDistanceMeters else 0.0)
                    onSave(mSec, eSec, mDistMeters, eDistMeters)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
