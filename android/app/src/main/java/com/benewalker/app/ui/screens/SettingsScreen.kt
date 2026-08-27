package com.benewalker.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.health.connect.client.HealthConnectClient
import com.benewalker.app.ui.HcStatus
import com.benewalker.app.ui.WalkViewModel
import kotlinx.coroutines.launch

fun openHealthConnectSettings(context: Context) {
    try {
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            context.startActivity(intent)
        } catch (e2: Exception) {
            try {
                val intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
                context.startActivity(intent)
            } catch (e3: Exception) {
                Toast.makeText(context, "Health Connect Systemeinstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: WalkViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    // Health Connect Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.healthConnectManager.createPermissionContract()
    ) { grantedPermissions ->
        viewModel.checkHealthConnectStatus()
        if (viewModel.healthConnectManager.walkingPermissions.all { it in grantedPermissions }) {
            viewModel.syncWithHealthConnect()
            Toast.makeText(context, "Health Connect Berechtigung erteilt!", Toast.LENGTH_SHORT).show()
        }
    }

    // File Picker for JSON Import
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        val count = viewModel.importJson(content)
                        if (count > 0) {
                            Toast.makeText(context, "✓ $count Gehzeiten-Einträge erfolgreich importiert!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Keine gültigen Datensätze im Backup gefunden", Toast.LENGTH_LONG).show()
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
        // 1. KATEGORIE: ERSCHEINUNGSBILD
        item {
            Text(
                text = "ERSCHEINUNGSBILD",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Theme Mode Selector (System, Hell, Dunkel)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Design-Modus", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = uiState.themeMode == "system",
                                onClick = { viewModel.setThemeMode("system") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) {
                                Text("System", fontSize = 12.sp)
                            }
                            SegmentedButton(
                                selected = uiState.themeMode == "light",
                                onClick = { viewModel.setThemeMode("light") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) {
                                Text("Hell", fontSize = 12.sp)
                            }
                            SegmentedButton(
                                selected = uiState.themeMode == "dark",
                                onClick = { viewModel.setThemeMode("dark") },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) {
                                Text("Dunkel", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Dynamic Color (Material You) Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Dynamic Color (Material You)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "Farben an dein Smartphone-Wallpaper anpassen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Switch(
                            checked = uiState.useDynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }
        }

        // 2. KATEGORIE: GARMIN & HEALTH CONNECT
        item {
            Text(
                text = "GARMIN & HEALTH CONNECT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Automatische Geherfassung", fontWeight = FontWeight.Bold)
                                Text(
                                    text = when (uiState.hcStatus) {
                                        HcStatus.SYNCING -> "Synchronisiert gerade..."
                                        HcStatus.PERMISSION_NEEDED -> "Berechtigung erforderlich"
                                        HcStatus.UNAVAILABLE -> "Health Connect nicht verfügbar"
                                        HcStatus.ERROR -> uiState.syncErrorMessage ?: "Sync-Fehler"
                                        else -> "Aktiv • Sync beim App-Start"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.hcStatus == HcStatus.PERMISSION_NEEDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        if (uiState.hcStatus == HcStatus.PERMISSION_NEEDED) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(viewModel.healthConnectManager.walkingPermissions)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Erlauben", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Button: Health Connect Systemeinstellungen öffnen
                    OutlinedButton(
                        onClick = { openHealthConnectSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Health Connect Einstellungen öffnen")
                    }

                    // Button: Letzte 30 Tage jetzt synchronisieren
                    FilledTonalButton(
                        onClick = { viewModel.syncWithHealthConnect(days = 30) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = uiState.hcStatus != HcStatus.SYNCING
                    ) {
                        if (uiState.hcStatus == HcStatus.SYNCING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synchronisiere...")
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Letzte 30 Tage jetzt synchronisieren")
                        }
                    }

                    Text(
                        "Anleitung:\n1. Öffne die Garmin Connect App auf deinem Smartphone.\n2. Gehe auf Einstellungen → Verknüpfte Apps → Health Connect aktivieren.\n3. Sobald deine Garmin-Uhr synchronisiert, liest BeneWalker die Gehzeiten beim Öffnen der App automatisch ein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 3. KATEGORIE: BACKUP & DATENVERWALTUNG
        item {
            Text(
                text = "BACKUP & DATEN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    // Export
                    ListItem(
                        headlineContent = { Text("Daten exportieren", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("JSON-Backup teilen oder speichern") },
                        leadingContent = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Import
                    ListItem(
                        headlineContent = { Text("Daten importieren", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Aus JSON-Datei wiederherstellen (Web-App & APK Backups)") },
                        leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                        modifier = Modifier.clickable {
                            importFileLauncher.launch(arrayOf("*/*"))
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Reset
                    ListItem(
                        headlineContent = { Text("Alle Daten zurücksetzen", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Löscht alle gespeicherten Gehzeiten unwiderruflich") },
                        leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showResetDialog = true
                        }
                    )
                }
            }
        }

        // 4. KATEGORIE: APP INFO
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BeneWalker • Native Edition", fontWeight = FontWeight.Bold)
                    Text("Modern Android Development (Jetpack Compose & Material Design 3)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
