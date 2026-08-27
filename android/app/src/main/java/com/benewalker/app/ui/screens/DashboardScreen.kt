package com.benewalker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel

@Composable
fun DashboardScreen(
    viewModel: WalkViewModel,
    onNavigateToData: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var chartRange by remember { mutableStateOf("all") } // Standardmäßig auf "Alle"
    var selectedBarRecord by remember { mutableStateOf<WalkRecord?>(null) }

    val displayedChartRecords = remember(uiState.records, chartRange) {
        when (chartRange) {
            "7" -> uiState.records.take(7).reversed()
            "14" -> uiState.records.take(14).reversed()
            "30" -> uiState.records.take(30).reversed()
            else -> uiState.records.reversed()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // 1. Die 4 Stat-Karten oben (farblich abgehoben)
        item {
            StatCardsGrid(
                state = uiState,
                onAnalyticsClick = onNavigateToData
            )
        }

        // 2. HAUPTDIAGRAMM mit Y-Achsen Minuten-Angaben, ohne Überlappungen
        item {
            DashboardChartSection(
                records = displayedChartRecords,
                selectedRange = chartRange,
                onRangeChange = { 
                    chartRange = it
                    selectedBarRecord = null
                },
                selectedRecord = selectedBarRecord,
                onBarClick = { record ->
                    selectedBarRecord = if (selectedBarRecord?.date == record?.date) null else record
                }
            )
        }
    }
}

@Composable
fun DashboardChartSection(
    records: List<WalkRecord>,
    selectedRange: String,
    onRangeChange: (String) -> Unit,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Titel
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
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Gehzeiten-Statistik",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Legend: 1. Gehen und 2. Gehen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = primaryColor, label = "1. Gehen")
                LegendItem(color = tertiaryColor, label = "2. Gehen")
            }

            // Detail-Anzeige bei Tippen auf einen Balken
            AnimatedVisibility(
                visible = selectedRecord != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedRecord != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = selectedRecord.date,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (selectedRecord.morningSeconds > 0) {
                                        Text(
                                            "1. Gehen: ${formatSecToMinSec(selectedRecord.morningSeconds)}",
                                            fontSize = 11.sp,
                                            color = primaryColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (selectedRecord.eveningSeconds > 0) {
                                        Text(
                                            "2. Gehen: ${formatSecToMinSec(selectedRecord.eveningSeconds)}",
                                            fontSize = 11.sp,
                                            color = tertiaryColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatSecDetailed(selectedRecord.totalSeconds),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = primaryColor
                                )
                                Text(
                                    text = "Gesamt",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Canvas Chart mit Y-Achsen Minutenangaben und überlappungsfreier Datumsachse
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
                    primaryColor = primaryColor,
                    tertiaryColor = tertiaryColor,
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
    primaryColor: Color,
    tertiaryColor: Color,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxRawSec = (records.maxOfOrNull { it.totalSeconds } ?: 1).coerceAtLeast(60)
    // Round maxSec up to clean minute steps for the Y-axis (e.g. 10m, 15m, 20m, 30m)
    val maxMinutes = ((maxRawSec + 59) / 60)
    val stepMinutes = when {
        maxMinutes <= 10 -> 2
        maxMinutes <= 20 -> 5
        maxMinutes <= 40 -> 10
        maxMinutes <= 75 -> 15
        else -> 20
    }
    val yMaxMinutes = (((maxMinutes + stepMinutes - 1) / stepMinutes) * stepMinutes).coerceAtLeast(stepMinutes)
    val maxSec = (yMaxMinutes * 60).toFloat()

    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val haptic = LocalHapticFeedback.current

    val totalBars = records.size
    val labelInterval = when {
        totalBars <= 7 -> 1
        totalBars <= 14 -> 2
        totalBars <= 30 -> 5
        else -> 7
    }

    // Exakte Berechnung der Datumsbeschriftungen (Verhindert Überlappungen am rechten Rand)
    val indicesToLabel = remember(records, totalBars, labelInterval) {
        val list = mutableSetOf<Int>()
        var lastAdded = -99
        for (i in 0 until totalBars) {
            if (i % labelInterval == 0) {
                list.add(i)
                lastAdded = i
            }
        }
        if (totalBars > 0) {
            val lastIdx = totalBars - 1
            // Nur hinzufügen wenn mindestens (labelInterval/2 + 1) Balken Abstand zum letzten Label
            if (lastIdx - lastAdded >= (labelInterval / 2 + 1)) {
                list.add(lastIdx)
            }
        }
        list
    }

    var lastScrubbedIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .pointerInput(records) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            val leftPadding = 32.dp.toPx()
                            val chartWidth = size.width - leftPadding
                            if (totalBars > 0 && chartWidth > 0) {
                                val barSpacing = chartWidth / (totalBars * 1.4f + 0.4f)
                                val relativeX = change.position.x - leftPadding
                                val barIndex = ((relativeX / barSpacing - 0.4f) / 1.4f).toInt().coerceIn(0, totalBars - 1)
                                if (barIndex != lastScrubbedIndex && barIndex in records.indices) {
                                    lastScrubbedIndex = barIndex
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onBarClick(records[barIndex])
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val leftPadding = 32.dp.toPx() // Left space for minute labels
        val chartBottom = size.height - 22.dp.toPx() // Bottom space for date labels
        val chartWidth = width - leftPadding
        val barSpacing = chartWidth / (totalBars * 1.4f + 0.4f)
        val barWidth = barSpacing * 0.85f

        // 1. Draw Y-Axis Minute Grid Lines & Labels
        val numGridSteps = yMaxMinutes / stepMinutes
        for (step in 1..numGridSteps) {
            val minVal = step * stepMinutes
            val yPos = chartBottom - (minVal * 60f / maxSec) * chartBottom

            // Dotted horizontal grid line
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            // Y-Axis minute label on left
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (labelTextColor.alpha * 180).toInt(),
                        (labelTextColor.red * 255).toInt(),
                        (labelTextColor.green * 255).toInt(),
                        (labelTextColor.blue * 255).toInt()
                    )
                    textSize = 8.5.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
                drawText("${minVal}m", leftPadding - 4.dp.toPx(), yPos + 3.dp.toPx(), paint)
            }
        }

        // 2. Draw Bars & Date Labels
        records.forEachIndexed { index, record ->
            val x = leftPadding + (index * 1.4f + 0.4f) * barSpacing
            val centerX = x + barWidth / 2

            val morningRatio = record.morningSeconds / maxSec
            val eveningRatio = record.eveningSeconds / maxSec

            val morningHeight = morningRatio * chartBottom
            val eveningHeight = eveningRatio * chartBottom
            val isSelected = selectedRecord?.date == record.date

            // Selection Background Highlight
            if (isSelected) {
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.25f),
                    topLeft = Offset(x - 3.dp.toPx(), 0f),
                    size = Size(barWidth + 6.dp.toPx(), chartBottom),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }

            // Morning Bar (Bottom - Primary)
            if (morningHeight > 0) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(x, chartBottom - morningHeight),
                    size = Size(barWidth, morningHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Evening Bar (Stacked on top - Tertiary)
            if (eveningHeight > 0) {
                drawRoundRect(
                    color = tertiaryColor,
                    topLeft = Offset(x, chartBottom - morningHeight - eveningHeight),
                    size = Size(barWidth, eveningHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Date Label on X-axis (Überlappungsfrei)
            if (indicesToLabel.contains(index)) {
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
                        isFakeBoldText = isSelected
                    }
                    drawText(shortDate, centerX, size.height - 3.dp.toPx(), paint)
                }
            }
        }
    }
}
