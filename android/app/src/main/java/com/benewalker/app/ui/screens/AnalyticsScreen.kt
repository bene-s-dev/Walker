package com.benewalker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.theme.AmberMorning
import com.benewalker.app.ui.theme.IndigoEvening

@Composable
fun AnalyticsScreen(
    viewModel: WalkViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRange by remember { mutableStateOf("7") } // "7", "30", "all"

    val displayedRecords = remember(uiState.records, selectedRange) {
        when (selectedRange) {
            "7" -> uiState.records.take(7).reversed()
            "30" -> uiState.records.take(30).reversed()
            else -> uiState.records.reversed()
        }
    }

    val totalSecondsInRange = remember(displayedRecords) {
        displayedRecords.sumOf { it.totalSeconds }
    }

    val avgSecondsInRange = remember(displayedRecords) {
        if (displayedRecords.isNotEmpty()) totalSecondsInRange / displayedRecords.size else 0
    }

    val maxDayRecord = remember(displayedRecords) {
        displayedRecords.maxByOrNull { it.totalSeconds }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Range Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = selectedRange == "7",
                        onClick = { selectedRange = "7" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("7 Tage")
                    }
                    SegmentedButton(
                        selected = selectedRange == "30",
                        onClick = { selectedRange = "30" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("30 Tage")
                    }
                    SegmentedButton(
                        selected = selectedRange == "all",
                        onClick = { selectedRange = "all" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Alle")
                    }
                }
            }
        }

        // M3 Bar Chart Card
        item {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gehzeiten Diagramm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendItem(color = AmberMorning, label = "1. Gehen")
                            LegendItem(color = IndigoEvening, label = "2. Gehen")
                        }
                    }

                    if (displayedRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Keine Daten im gewählten Zeitraum", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        WalkBarChart(
                            records = displayedRecords,
                            avgSeconds = avgSecondsInRange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
        }

        // Summary Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Gesamtdauer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatSecDetailed(totalSecondsInRange), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${displayedRecords.size} Tage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Tages-Schnitt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatSecDetailed(avgSecondsInRange), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Ø pro Tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // Best Day Highlight Card
        if (maxDayRecord != null) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Bester Tag im Zeitraum", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(maxDayRecord.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(
                            formatSecDetailed(maxDayRecord.totalSeconds),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WalkBarChart(
    records: List<WalkRecord>,
    avgSeconds: Int,
    modifier: Modifier = Modifier
) {
    val maxSec = (records.maxOfOrNull { it.totalSeconds } ?: 1).coerceAtLeast(60).toFloat()

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.padding(vertical = 12.dp)) {
        val width = size.width
        val height = size.height
        val barCount = records.size
        val barSpacing = width / (barCount * 1.5f + 0.5f)
        val barWidth = barSpacing * 0.9f

        // Draw Average Line
        if (avgSeconds > 0) {
            val avgY = height - (avgSeconds / maxSec) * height
            drawLine(
                color = primaryColor.copy(alpha = 0.5f),
                start = Offset(0f, avgY),
                end = Offset(width, avgY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )
        }

        // Draw Bars
        records.forEachIndexed { index, record ->
            val x = (index * 1.5f + 0.5f) * barSpacing

            val morningRatio = record.morningSeconds / maxSec
            val eveningRatio = record.eveningSeconds / maxSec

            val morningHeight = morningRatio * height
            val eveningHeight = eveningRatio * height

            // Morning Bar (Bottom)
            if (morningHeight > 0) {
                drawRoundRect(
                    color = AmberMorning,
                    topLeft = Offset(x, height - morningHeight),
                    size = Size(barWidth, morningHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Evening Bar (Stacked on top)
            if (eveningHeight > 0) {
                drawRoundRect(
                    color = IndigoEvening,
                    topLeft = Offset(x, height - morningHeight - eveningHeight),
                    size = Size(barWidth, eveningHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
    }
}
