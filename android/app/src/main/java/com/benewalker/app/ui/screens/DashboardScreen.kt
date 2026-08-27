package com.benewalker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.theme.AmberMorning
import com.benewalker.app.ui.theme.IndigoEvening

@Composable
fun DashboardScreen(
    viewModel: WalkViewModel,
    onNavigateToData: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var chartRange by remember { mutableStateOf("7") } // "7", "14", "30", "all"
    var selectedBarRecord by remember { mutableStateOf<WalkRecord?>(null) }

    val displayedChartRecords = remember(uiState.records, chartRange) {
        when (chartRange) {
            "7" -> uiState.records.take(7).reversed()
            "14" -> uiState.records.take(14).reversed()
            "30" -> uiState.records.take(30).reversed()
            else -> uiState.records.reversed()
        }
    }

    val avgChartSeconds = remember(displayedChartRecords) {
        if (displayedChartRecords.isNotEmpty()) displayedChartRecords.map { it.totalSeconds }.average().toInt() else 0
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Die 4 Stat-Karten oben
        item {
            StatCardsGrid(
                state = uiState,
                onAnalyticsClick = onNavigateToData
            )
        }

        // 2. HAUPTDIAGRAMM mit intelligenter Tagesbeschriftung unten
        item {
            DashboardChartSection(
                records = displayedChartRecords,
                avgSeconds = avgChartSeconds,
                selectedRange = chartRange,
                onRangeChange = { 
                    chartRange = it
                    selectedBarRecord = null
                },
                selectedRecord = selectedBarRecord,
                onBarClick = { selectedBarRecord = it }
            )
        }
    }
}

@Composable
fun DashboardChartSection(
    records: List<WalkRecord>,
    avgSeconds: Int,
    selectedRange: String,
    onRangeChange: (String) -> Unit,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit
) {
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
            // Header & Range Selector
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Gehzeiten-Statistik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "Ø ${formatSecDetailed(avgSeconds)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Range Buttons (7 Tage, 14 Tage, 30 Tage, Alle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("7" to "7 Tage", "14" to "14 Tage", "30" to "30 Tage", "all" to "Alle").forEach { (key, label) ->
                        val isSelected = selectedRange == key
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onRangeChange(key) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem(color = AmberMorning, label = "1. Gehen")
                LegendItem(color = IndigoEvening, label = "2. Gehen")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                    Text("Ø Schnitt", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Selected Bar Tooltip
            if (selectedRecord != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selectedRecord.date, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                "1. Gehen: ${formatSecToMinSec(selectedRecord.morningSeconds)} | 2. Gehen: ${formatSecToMinSec(selectedRecord.eveningSeconds)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatSecDetailed(selectedRecord.totalSeconds),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Canvas Chart with Date Labels
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keine Daten für das Diagramm vorhanden", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            } else {
                DashboardInteractiveBarChart(
                    records = records,
                    avgSeconds = avgSeconds,
                    selectedRecord = selectedRecord,
                    onBarClick = onBarClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardInteractiveBarChart(
    records: List<WalkRecord>,
    avgSeconds: Int,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxSec = (records.maxOfOrNull { it.totalSeconds } ?: 1).coerceAtLeast(60).toFloat()
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    // Calculate smart label interval so dates don't overlap
    val totalBars = records.size
    val labelInterval = when {
        totalBars <= 7 -> 1
        totalBars <= 14 -> 2
        totalBars <= 30 -> 5
        else -> 7
    }

    Canvas(
        modifier = modifier
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        val width = size.width
        val chartBottom = size.height - 24.dp.toPx() // Reserve 24dp for X-axis date labels
        val barSpacing = width / (totalBars * 1.4f + 0.4f)
        val barWidth = barSpacing * 0.85f

        // Draw Average Dashed Line
        if (avgSeconds > 0) {
            val avgY = chartBottom - (avgSeconds / maxSec) * chartBottom
            drawLine(
                color = primaryColor.copy(alpha = 0.5f),
                start = Offset(0f, avgY),
                end = Offset(width, avgY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )
        }

        // Draw Bars & Date Labels
        records.forEachIndexed { index, record ->
            val x = (index * 1.4f + 0.4f) * barSpacing

            val morningRatio = record.morningSeconds / maxSec
            val eveningRatio = record.eveningSeconds / maxSec

            val morningHeight = morningRatio * chartBottom
            val eveningHeight = eveningRatio * chartBottom
            val isSelected = selectedRecord?.date == record.date

            // Background highlight if selected
            if (isSelected) {
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.15f),
                    topLeft = Offset(x - 2.dp.toPx(), 0f),
                    size = Size(barWidth + 4.dp.toPx(), chartBottom),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }

            // Morning Bar (Bottom)
            if (morningHeight > 0) {
                drawRoundRect(
                    color = AmberMorning,
                    topLeft = Offset(x, chartBottom - morningHeight),
                    size = Size(barWidth, morningHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Evening Bar (Stacked on top)
            if (eveningHeight > 0) {
                drawRoundRect(
                    color = IndigoEvening,
                    topLeft = Offset(x, chartBottom - morningHeight - eveningHeight),
                    size = Size(barWidth, eveningHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Date Label on X-axis (Smart interval: not all days to avoid clutter)
            val shouldShowLabel = (index % labelInterval == 0) || (index == totalBars - 1)
            if (shouldShowLabel) {
                val dateParts = record.date.split("-")
                val shortDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}." else record.date

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(
                            (labelTextColor.alpha * 255).toInt(),
                            (labelTextColor.red * 255).toInt(),
                            (labelTextColor.green * 255).toInt(),
                            (labelTextColor.blue * 255).toInt()
                        )
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(shortDate, x + barWidth / 2, size.height - 4.dp.toPx(), paint)
                }
            }
        }
    }
}
