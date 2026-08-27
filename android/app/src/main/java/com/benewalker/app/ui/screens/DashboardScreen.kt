package com.benewalker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.ui.WalkUiState
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.theme.TrendGreenDark
import com.benewalker.app.ui.theme.TrendGreenLight

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
        // 1. Die 4 Stat-Karten oben (stark abgehoben mit M3 Elevation & Borders)
        item {
            StatCardsGrid(
                state = uiState,
                onAnalyticsClick = onNavigateToData
            )
        }

        // 2. HAUPTDIAGRAMM mit hochsichtbarer leuchtender Trendlinie
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
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trendColor = if (isDark) TrendGreenDark else TrendGreenLight

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Range Selector
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Ø ${formatSecDetailed(avgSeconds)}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null
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
            }

            // Legend mit hochsichtbarem Trend-Indikator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = primaryColor, label = "1. Gehen")
                LegendItem(color = tertiaryColor, label = "2. Gehen")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(trendColor)
                    )
                    Text("Trend", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = trendColor)
                }
            }

            // Selected Bar Tooltip
            if (selectedRecord != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selectedRecord.date, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "1. Gehen: ${formatSecToMinSec(selectedRecord.morningSeconds)} | 2. Gehen: ${formatSecToMinSec(selectedRecord.eveningSeconds)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatSecDetailed(selectedRecord.totalSeconds),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = primaryColor
                        )
                    }
                }
            }

            // Canvas Chart with Ultra-Visible Trend Line and Date Labels
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
                    trendColor = trendColor,
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
    trendColor: Color,
    selectedRecord: WalkRecord?,
    onBarClick: (WalkRecord?) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxSec = (records.maxOfOrNull { it.totalSeconds } ?: 1).coerceAtLeast(60).toFloat()
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Calculate smart label interval so dates don't overlap
    val totalBars = records.size
    val labelInterval = when {
        totalBars <= 7 -> 1
        totalBars <= 14 -> 2
        totalBars <= 30 -> 5
        else -> 7
    }

    // Starke Glättung (Gaussian Double-Pass) zur zuverlässigen Herausrechnung von Ausreißern und Ruhetagen
    val smoothedValues = remember(records) {
        if (records.isEmpty()) return@remember emptyList()
        val raw = records.map { it.totalSeconds.toFloat() }
        val n = raw.size

        // Pass 1: Symmetrischer Gaussian-Filter (Window = 5, Gewichte [1, 3, 6, 3, 1])
        val weights = floatArrayOf(1f, 3f, 6f, 3f, 1f)
        val halfWin = weights.size / 2
        val pass1 = raw.indices.map { i ->
            var sum = 0f
            var weightSum = 0f
            for (offset in -halfWin..halfWin) {
                val idx = (i + offset).coerceIn(0, n - 1)
                val w = weights[offset + halfWin]
                sum += raw[idx] * w
                weightSum += w
            }
            sum / weightSum
        }

        // Pass 2: Harmonischer Ausgleich für fließenden Trend-Verlauf
        pass1.indices.map { i ->
            val prev = pass1.getOrElse(i - 1) { pass1[i] }
            val curr = pass1[i]
            val next = pass1.getOrElse(i + 1) { pass1[i] }
            0.25f * prev + 0.5f * curr + 0.25f * next
        }
    }

    Canvas(
        modifier = modifier
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        val width = size.width
        val chartBottom = size.height - 24.dp.toPx() // Space for date labels
        val barSpacing = width / (totalBars * 1.4f + 0.4f)
        val barWidth = barSpacing * 0.85f

        val points = mutableListOf<Offset>()

        // 1. Draw Bars & collect points for trend curve
        records.forEachIndexed { index, record ->
            val x = (index * 1.4f + 0.4f) * barSpacing
            val centerX = x + barWidth / 2

            val morningRatio = record.morningSeconds / maxSec
            val eveningRatio = record.eveningSeconds / maxSec

            val morningHeight = morningRatio * chartBottom
            val eveningHeight = eveningRatio * chartBottom
            val isSelected = selectedRecord?.date == record.date

            // Smoothed Y point
            val smoothVal = smoothedValues.getOrElse(index) { record.totalSeconds.toFloat() }
            val smoothY = chartBottom - (smoothVal / maxSec) * chartBottom
            points.add(Offset(centerX, smoothY.coerceIn(8.dp.toPx(), chartBottom)))

            // Background highlight if selected
            if (isSelected) {
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.2f),
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

            // Date Label on X-axis (Smart interval)
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
                        textSize = 9.5.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(shortDate, centerX, size.height - 4.dp.toPx(), paint)
                }
            }
        }

        // 2. Draw Highly Visible Smoothed Trend Curve (Bezier Path)
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }

            // Trend Path Stroke (3.5dp)
            drawPath(
                path = path,
                color = trendColor,
                style = Stroke(
                    width = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Distinct Glowing Dots on data points
            points.forEach { pt ->
                // Outer circle / halo
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = 5.dp.toPx(),
                    center = pt
                )
                // Inner bright green dot
                drawCircle(
                    color = trendColor,
                    radius = 3.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
