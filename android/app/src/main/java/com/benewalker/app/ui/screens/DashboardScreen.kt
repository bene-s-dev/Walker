package com.benewalker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.theme.ChartEveningColor
import com.benewalker.app.ui.theme.ChartMorningColor
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: WalkViewModel,
    onNavigateToData: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var chartRange by remember { mutableStateOf("all") } // "7", "14", "30", "all"
    var chartMetric by remember { mutableStateOf("duration") } // "duration" (Gehzeit) or "distance" (Distanz)
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
        // 1. Die 4 Stat-Karten oben
        item {
            StatCardsGrid(
                state = uiState,
                onAnalyticsClick = onNavigateToData
            )
        }

        // 2. HAUPTDIAGRAMM mit umschaltbarer Metrik (Gehzeit / Distanz)
        item {
            DashboardChartSection(
                records = displayedChartRecords,
                selectedRange = chartRange,
                onRangeChange = { 
                    chartRange = it
                    selectedBarRecord = null
                },
                metricMode = chartMetric,
                onMetricChange = {
                    chartMetric = it
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
    metricMode: String,
    onMetricChange: (String) -> Unit,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val morningColor = ChartMorningColor // Strahlendes Frisches Grün
    val eveningColor = ChartEveningColor // Leuchtendes Sonnenuntergangs-Orange

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
            // Header: Titel & Metrik-Umschalter (Gehzeit vs. Distanz)
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
                        imageVector = if (metricMode == "distance") Icons.Outlined.Straighten else Icons.Filled.Insights,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (metricMode == "distance") "Distanz-Statistik" else "Gehzeiten-Statistik",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Metrik Toggle: [ ⏱️ Zeit | 📏 Distanz ]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Option 1: Zeit
                    Surface(
                        onClick = { onMetricChange("duration") },
                        shape = RoundedCornerShape(10.dp),
                        color = if (metricMode == "duration") primaryColor else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (metricMode == "duration") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Zeit",
                                fontSize = 11.sp,
                                fontWeight = if (metricMode == "duration") FontWeight.Bold else FontWeight.Medium,
                                color = if (metricMode == "duration") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2: Distanz
                    Surface(
                        onClick = { onMetricChange("distance") },
                        shape = RoundedCornerShape(10.dp),
                        color = if (metricMode == "distance") primaryColor else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (metricMode == "distance") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "km",
                                fontSize = 11.sp,
                                fontWeight = if (metricMode == "distance") FontWeight.Bold else FontWeight.Medium,
                                color = if (metricMode == "distance") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

            // Legend: 1. Gehen (Grün) und 2. Gehen (Orange)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = morningColor, label = "1. Gehen (Vormittag)")
                LegendItem(color = eveningColor, label = "2. Gehen (Nachmittag)")
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
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = selectedRecord.date,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (selectedRecord.morningSeconds > 0 || selectedRecord.morningDistanceMeters > 0) {
                                        val mLabel = if (metricMode == "distance") {
                                            String.format(Locale.GERMAN, "1. Gehen: %.2f km", selectedRecord.morningDistanceMeters / 1000.0)
                                        } else {
                                            "1. Gehen: ${formatSecToMinSec(selectedRecord.morningSeconds)}"
                                        }
                                        Text(
                                            mLabel,
                                            fontSize = 11.5.sp,
                                            color = morningColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (selectedRecord.eveningSeconds > 0 || selectedRecord.eveningDistanceMeters > 0) {
                                        val eLabel = if (metricMode == "distance") {
                                            String.format(Locale.GERMAN, "2. Gehen: %.2f km", selectedRecord.eveningDistanceMeters / 1000.0)
                                        } else {
                                            "2. Gehen: ${formatSecToMinSec(selectedRecord.eveningSeconds)}"
                                        }
                                        Text(
                                            eLabel,
                                            fontSize = 11.5.sp,
                                            color = eveningColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val totalValueStr = if (metricMode == "distance") {
                                    val totKm = (selectedRecord.morningDistanceMeters + selectedRecord.eveningDistanceMeters) / 1000.0
                                    String.format(Locale.GERMAN, "%.2f km", totKm)
                                } else {
                                    formatSecDetailed(selectedRecord.totalSeconds)
                                }
                                Text(
                                    text = totalValueStr,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (metricMode == "distance") "Gesamtdistanz" else "Gesamtzeit",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Canvas Chart mit dynamischer Y-Achsen Skalierung (Minuten oder Kilometer)
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
                    metricMode = metricMode,
                    morningColor = morningColor,
                    eveningColor = eveningColor,
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
    metricMode: String,
    morningColor: Color,
    eveningColor: Color,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDistanceMode = metricMode == "distance"

    // Calculate Y-Axis Max & Step depending on metricMode
    val (yMaxVal, stepVal, yAxisUnit) = if (isDistanceMode) {
        val maxRawKm = (records.maxOfOrNull {
            maxOf(it.morningDistanceMeters, it.eveningDistanceMeters) / 1000.0
        } ?: 1.0).coerceAtLeast(0.5)

        val step = when {
            maxRawKm <= 1.0 -> 0.2
            maxRawKm <= 2.5 -> 0.5
            maxRawKm <= 5.0 -> 1.0
            maxRawKm <= 10.0 -> 2.0
            else -> 5.0
        }
        val yMax = (Math.ceil(maxRawKm / step) * step).coerceAtLeast(step)
        Triple(yMax.toFloat(), step.toFloat(), "km")
    } else {
        val maxRawSec = (records.maxOfOrNull { maxOf(it.morningSeconds, it.eveningSeconds) } ?: 1).coerceAtLeast(60)
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
        Triple(maxSec, (stepMinutes * 60).toFloat(), "m")
    }

    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val selectionLineColor = MaterialTheme.colorScheme.primary
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
            .pointerInput(records, metricMode) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            val leftPadding = 36.dp.toPx()
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
        val leftPadding = 36.dp.toPx() // Left space for labels
        val chartBottom = size.height - 22.dp.toPx() // Bottom space for date labels
        val chartWidth = width - leftPadding
        val barSpacing = chartWidth / (totalBars * 1.4f + 0.4f)
        val daySlotWidth = barSpacing * 0.85f
        val gap = (daySlotWidth * 0.12f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())
        val unitBarWidth = ((daySlotWidth - gap) / 2f).coerceAtLeast(1.dp.toPx())

        // 1. Draw Y-Axis Grid Lines & Labels
        val numGridSteps = (yMaxVal / stepVal).toInt()
        for (step in 1..numGridSteps) {
            val curVal = step * stepVal
            val yPos = chartBottom - (curVal / yMaxVal) * chartBottom

            // Dotted horizontal grid line
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            // Y-Axis label on left
            val labelText = if (isDistanceMode) {
                if (curVal % 1.0f == 0f) "${curVal.toInt()}km" else String.format(Locale.GERMAN, "%.1fkm", curVal)
            } else {
                val minVal = (curVal / 60).toInt()
                "${minVal}m"
            }

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
                drawText(labelText, leftPadding - 4.dp.toPx(), yPos + 3.dp.toPx(), paint)
            }
        }

        // 2. Draw Bars & Date Labels
        records.forEachIndexed { index, record ->
            val x = leftPadding + (index * 1.4f + 0.4f) * barSpacing
            val centerX = x + daySlotWidth / 2
            val morningX = x
            val eveningX = x + unitBarWidth + gap

            val morningValue = if (isDistanceMode) (record.morningDistanceMeters / 1000.0).toFloat() else record.morningSeconds.toFloat()
            val eveningValue = if (isDistanceMode) (record.eveningDistanceMeters / 1000.0).toFloat() else record.eveningSeconds.toFloat()

            val morningHeight = ((morningValue / yMaxVal) * chartBottom).coerceIn(0f, chartBottom)
            val eveningHeight = ((eveningValue / yMaxVal) * chartBottom).coerceIn(0f, chartBottom)
            val isSelected = selectedRecord?.date == record.date

            // Selection Thin Vertical Scrub Line & Indicator
            if (isSelected) {
                drawLine(
                    color = selectionLineColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, chartBottom),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                )
                drawCircle(
                    color = selectionLineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, 4.dp.toPx())
                )
            }

            // Morning Bar (Left - 1. Gehen: Grün)
            if (morningHeight > 0) {
                drawRoundRect(
                    color = morningColor,
                    topLeft = Offset(morningX, chartBottom - morningHeight),
                    size = Size(unitBarWidth, morningHeight),
                    cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                )
            }

            // Evening Bar (Right - 2. Gehen: Orange)
            if (eveningHeight > 0) {
                drawRoundRect(
                    color = eveningColor,
                    topLeft = Offset(eveningX, chartBottom - eveningHeight),
                    size = Size(unitBarWidth, eveningHeight),
                    cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
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
