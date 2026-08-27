package com.benewalker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Leichtes haptisches Feedback beim Scrollen über Listenelemente
    var lastScrolledIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex != lastScrolledIndex && listState.isScrollInProgress) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastScrolledIndex = listState.firstVisibleItemIndex
        }
    }

    val displayedChartRecords = remember(uiState.records, chartRange) {
        when (chartRange) {
            "7" -> uiState.records.take(7).reversed()
            "14" -> uiState.records.take(14).reversed()
            "30" -> uiState.records.take(30).reversed()
            else -> uiState.records.reversed()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // 1. Die 4 Stat-Karten oben (farblich abgehoben ohne harte Border)
        item {
            StatCardsGrid(
                state = uiState,
                onAnalyticsClick = onNavigateToData
            )
        }

        // 2. HAUPTDIAGRAMM (Ohne Trendlinie, ohne Durchschnittszeit, mit interaktivem Tippen)
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
            // Header: Nur noch der Titel (Durchschnittszeit entfernt)
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

            // Legend: 1. Gehen und 2. Gehen (Trendlinie entfernt)
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

            // Canvas Chart mit direktem Tipp-Support auf die Balken
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
    val maxSec = (records.maxOfOrNull { it.totalSeconds } ?: 1).coerceAtLeast(60).toFloat()
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val haptic = LocalHapticFeedback.current

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
            .padding(top = 10.dp, bottom = 4.dp)
            .pointerInput(records) {
                detectTapGestures { offset ->
                    val width = size.width
                    val barSpacing = width / (totalBars * 1.4f + 0.4f)
                    val barWidth = barSpacing * 0.85f

                    // Hit test to find tapped bar
                    records.forEachIndexed { index, record ->
                        val barX = (index * 1.4f + 0.4f) * barSpacing
                        if (offset.x >= barX - 8.dp.toPx() && offset.x <= barX + barWidth + 8.dp.toPx()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBarClick(record)
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val chartBottom = size.height - 24.dp.toPx() // Space for date labels
        val barSpacing = width / (totalBars * 1.4f + 0.4f)
        val barWidth = barSpacing * 0.85f

        // Draw Bars
        records.forEachIndexed { index, record ->
            val x = (index * 1.4f + 0.4f) * barSpacing
            val centerX = x + barWidth / 2

            val morningRatio = record.morningSeconds / maxSec
            val eveningRatio = record.eveningSeconds / maxSec

            val morningHeight = morningRatio * chartBottom
            val eveningHeight = eveningRatio * chartBottom
            val isSelected = selectedRecord?.date == record.date

            // Background highlight if selected
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
                        isFakeBoldText = isSelected
                    }
                    drawText(shortDate, centerX, size.height - 4.dp.toPx(), paint)
                }
            }
        }
    }
}
