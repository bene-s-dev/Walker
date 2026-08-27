package com.benewalker.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.ui.WalkViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: WalkViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }

    // File Picker for JSON Import
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val success = viewModel.importJson(content)
                        if (success) {
                            Toast.makeText(context, "✓ Backup erfolgreich importiert!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ungültiges Backup-Format", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Garmin & Health Connect Section
        item {
            Text("Garmin & Health Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Automatische Geherfassung", fontWeight = FontWeight.Bold)
                            Text("Liest Garmin-Aktivitäten vom Typ \"Gehen\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        "Anleitung:\n1. Öffne die Garmin Connect App auf deinem Handy.\n2. Gehe auf Einstellungen → Verknüpfte Apps → Health Connect aktivieren.\n3. Sobald deine Garmin-Uhr synchronisiert, liest BeneWalker die Zeiten sekundengenau ein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    FilledTonalButton(
                        onClick = { viewModel.syncWithHealthConnect(days = 30) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Letzte 30 Tage jetzt synchronisieren")
                    }
                }
            }
        }

        // Backup & Daten
        item {
            Text("Backup & Datenverwaltung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Export
                    ListItem(
                        headlineContent = { Text("Daten exportieren", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("JSON-Backup teilen oder speichern") },
                        leadingContent = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                val json = viewModel.exportJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    putExtra(Intent.EXTRA_TITLE, "BeneWalker_Backup.json")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Backup exportieren"))
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Import
                    ListItem(
                        headlineContent = { Text("Daten importieren", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Aus einer JSON-Datei wiederherstellen") },
                        leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.clickable {
                            importFileLauncher.launch("application/json")
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Reset
                    ListItem(
                        headlineContent = { Text("Alle Daten zurücksetzen", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Löscht alle gespeicherten Gehzeiten") },
                        leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showResetDialog = true
                        }
                    )
                }
            }
        }

        // App Info
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BeneWalker • Native Edition", fontWeight = FontWeight.Bold)
                    Text("Modern Android Development (Jetpack Compose & Material 3 / Material You)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Alle Daten löschen?") },
            text = { Text("Möchtest du wirklich alle aufgezeichneten Gehzeiten unwiderruflich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                        Toast.makeText(context, "Alle Daten gelöscht", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
