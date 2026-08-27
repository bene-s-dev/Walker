package com.benewalker.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel
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
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // Heute vs. Vortag
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Heute vs. Vortag",
                value = if (state.todayVsYesterdayDiffSec >= 0) "+${formatSecDetailed(state.todayVsYesterdayDiffSec)}" else "-${formatSecDetailed(-state.todayVsYesterdayDiffSec)}",
                subtitle = "Tagesdifferenz",
                isPositive = state.todayVsYesterdayDiffSec >= 0,
                icon = Icons.Outlined.CompareArrows,
                onClick = onAnalyticsClick
            )

            // 7-Tage Schnitt
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "7-Tage Schnitt",
                value = formatSecDetailed(state.avg7DaysSec),
                subtitle = "Ø pro Tag",
                icon = Icons.Outlined.Timeline,
                onClick = onAnalyticsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 30-Tage Schnitt
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "30-Tage Schnitt",
                value = formatSecDetailed(state.avg30DaysSec),
                subtitle = "Ø pro Tag",
                icon = Icons.Outlined.DateRange,
                onClick = onAnalyticsClick
            )

            // Rekord am Stück
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Rekord am Stück",
                value = formatSecDetailed(state.allTimeSingleRecordSec),
                subtitle = "All-Time Rekord",
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
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean? = null,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Datumsauswahl
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gehzeit erfassen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manuelle Eingabe für den gewählten Tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            viewModel.setFormDate(today)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Heute", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            viewModel.setFormDate(yesterday)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Gestern", fontSize = 12.sp)
                    }
                }
            }

            // Datumswähler Feld
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Datum", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(state.formDate, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Icon(Icons.Filled.EditCalendar, contentDescription = "Datum ändern", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                }
            }

            // 1. Gehen Block (Primary Color)
            AndroidM3SessionInput(
                title = "1. Gehen (Vormittag / Erste Einheit)",
                icon = Icons.Outlined.WbSunny,
                themeColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                min = state.morningMin,
                sec = state.morningSec,
                onMinChange = { viewModel.updateFormFields(morningMin = it) },
                onSecChange = { viewModel.updateFormFields(morningSec = it) },
                onAddSeconds = { viewModel.addQuickSeconds("morning", it) },
                onClear = { viewModel.clearFormField("morning") }
            )

            // 2. Gehen Block (Tertiary Color)
            AndroidM3SessionInput(
                title = "2. Gehen (Nachmittag / Zweite Einheit)",
                icon = Icons.Outlined.NightsStay,
                themeColor = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                min = state.eveningMin,
                sec = state.eveningSec,
                onMinChange = { viewModel.updateFormFields(eveningMin = it) },
                onSecChange = { viewModel.updateFormFields(eveningSec = it) },
                onAddSeconds = { viewModel.addQuickSeconds("evening", it) },
                onClear = { viewModel.clearFormField("evening") }
            )

            // Speichern Action
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    themeColor: Color,
    containerColor: Color,
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
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = themeColor, modifier = Modifier.size(18.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, color = themeColor, fontSize = 13.sp)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = themeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = formatSecToMinSec(totalSec),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor
                    )
                }
            }

            // Standard Outlined TextFields
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = min,
                    onValueChange = onMinChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Minuten") },
                    placeholder = { Text("0") },
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    suffix = { Text("min", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        focusedLabelColor = themeColor
                    )
                )
                OutlinedTextField(
                    value = sec,
                    onValueChange = onSecChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Sekunden") },
                    placeholder = { Text("0") },
                    leadingIcon = { Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    suffix = { Text("sek", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        focusedLabelColor = themeColor
                    )
                )
            }

            // Material 3 Quick Add Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(15 to "+15s", 30 to "+30s", 45 to "+45s", 60 to "+1m").forEach { (s, label) ->
                    SuggestionChip(
                        onClick = { onAddSeconds(s) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = themeColor
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = themeColor.copy(alpha = 0.3f)
                        )
                    )
                }

                FilledTonalIconButton(
                    onClick = onClear,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = "Leeren", modifier = Modifier.size(16.dp))
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Date & Split times (takes remaining width)
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = record.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (record.morningSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "1.: ${formatSecToMinSec(record.morningSeconds)}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (record.eveningSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "2.: ${formatSecToMinSec(record.eveningSeconds)}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (record.morningSeconds == 0 && record.eveningSeconds == 0) {
                        Text(
                            text = "Keine Einheiten",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Right: Total time & Action Buttons (fixed width, always aligned and fully visible)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = formatSecDetailed(record.totalSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Gesamt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Edit Button (Fixed 36dp)
                FilledTonalIconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Bearbeiten",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete Button (Fixed 36dp)
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Löschen",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
