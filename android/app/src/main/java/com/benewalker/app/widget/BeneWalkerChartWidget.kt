package com.benewalker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.benewalker.app.MainActivity
import com.benewalker.app.R
import com.benewalker.app.data.WalkDatabase
import com.benewalker.app.data.WalkRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BeneWalkerChartWidget : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BeneWalkerChartWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, BeneWalkerChartWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val database = WalkDatabase.getInstance(context)
        val walkDao = database.walkDao()

        CoroutineScope(Dispatchers.IO).launch {
            val records = walkDao.getAllRecords()
            val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val todayRecord = records.find { it.date == todayStr }
            val allRecords = records.reversed()
            val chartBitmap = renderChartBitmap(context, allRecords)

            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_chart)

                    // 1. Header: Today's time badge
                    val todayTotal = todayRecord?.totalSeconds ?: 0
                    val todayMinutes = todayTotal / 60
                    val todaySeconds = todayTotal % 60
                    views.setTextViewText(R.id.widget_today_badge, "Heute: ${String.format("%02d:%02d", todayMinutes, todaySeconds)} min")

                    // 2. Chart Bitmap
                    views.setImageViewBitmap(R.id.widget_chart_image, chartBitmap)

                    // 3. Footer Summary
                    val avg30 = if (records.isNotEmpty()) records.take(30).map { it.totalSeconds }.average().toInt() else 0
                    val avg30Min = avg30 / 60
                    val totalDays = records.size
                    views.setTextViewText(R.id.widget_footer_text, "Gesamtverlauf: $totalDays Tage erfasst • Ø 30 Tage: ${avg30Min} min")

                    // 4. Click anywhere -> Open App
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    private fun renderChartBitmap(context: Context, records: List<WalkRecord>): Bitmap {
        val width = 720
        val height = 280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (records.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.parseColor("#889690")
                textSize = 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Noch keine Gehzeiten vorhanden", width / 2f, height / 2f, emptyPaint)
            return bitmap
        }

        val primaryColor = Color.parseColor("#78D8AF") // Material You Mint
        val tertiaryColor = Color.parseColor("#84D4DE") // Material You Teal/Cyan
        val gridColor = Color.parseColor("#33443F")
        val labelColor = Color.parseColor("#A2B2AC")

        val totalBars = records.size
        val maxRawSec = (records.maxOfOrNull { maxOf(it.morningSeconds, it.eveningSeconds) } ?: 1).coerceAtLeast(60)
        val maxMinutes = ((maxRawSec + 59) / 60)
        val stepMinutes = when {
            maxMinutes <= 15 -> 5
            maxMinutes <= 30 -> 10
            maxMinutes <= 60 -> 15
            else -> 20
        }
        val yMaxMinutes = (((maxMinutes + stepMinutes - 1) / stepMinutes) * stepMinutes).coerceAtLeast(stepMinutes)
        val maxSec = (yMaxMinutes * 60).toFloat()

        val leftPadding = 55f
        val chartBottom = height - 40f
        val chartWidth = width - leftPadding - 15f
        val barSpacing = chartWidth / (totalBars * 1.35f + 0.35f)
        val daySlotWidth = barSpacing * 0.85f
        val gap = (daySlotWidth * 0.12f).coerceIn(1.5f, 4f)
        val unitBarWidth = ((daySlotWidth - gap) / 2f).coerceAtLeast(1.5f)

        val labelInterval = when {
            totalBars <= 7 -> 1
            totalBars <= 14 -> 2
            totalBars <= 28 -> 4
            totalBars <= 45 -> 7
            else -> 10
        }

        val indicesToLabel = mutableSetOf<Int>()
        var lastAdded = -99
        for (i in 0 until totalBars) {
            if (i % labelInterval == 0) {
                indicesToLabel.add(i)
                lastAdded = i
            }
        }
        if (totalBars > 0) {
            val lastIdx = totalBars - 1
            if (lastIdx - lastAdded >= (labelInterval / 2 + 1)) {
                indicesToLabel.add(lastIdx)
            }
        }

        // Draw Y-Axis Dotted Grid Lines & Minute Labels
        val gridPaint = Paint().apply {
            color = gridColor
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = labelColor
            textSize = 18f
            isAntiAlias = true
        }

        val numSteps = yMaxMinutes / stepMinutes
        for (i in 1..numSteps) {
            val minVal = i * stepMinutes
            val yPos = chartBottom - (minVal * 60f / maxSec) * chartBottom
            canvas.drawLine(leftPadding, yPos, width - 10f, yPos, gridPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${minVal}m", leftPadding - 8f, yPos + 6f, textPaint)
        }

        // Draw Bars & Dates
        val primaryPaint = Paint().apply {
            color = primaryColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val tertiaryPaint = Paint().apply {
            color = tertiaryColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        records.forEachIndexed { index, record ->
            val x = leftPadding + (index * 1.35f + 0.35f) * barSpacing
            val centerX = x + daySlotWidth / 2f
            val morningX = x
            val eveningX = x + unitBarWidth + gap

            val morningRatio = record.morningSeconds / maxSec
            val eveningRatio = record.eveningSeconds / maxSec

            val morningHeight = morningRatio * chartBottom
            val eveningHeight = eveningRatio * chartBottom

            // 1. Morning Bar (Left - Primary)
            if (morningHeight > 0) {
                val rect = RectF(morningX, chartBottom - morningHeight, morningX + unitBarWidth, chartBottom)
                canvas.drawRoundRect(rect, 4f, 4f, primaryPaint)
            }

            // 2. Evening Bar (Right - Tertiary)
            if (eveningHeight > 0) {
                val rect = RectF(eveningX, chartBottom - eveningHeight, eveningX + unitBarWidth, chartBottom)
                canvas.drawRoundRect(rect, 4f, 4f, tertiaryPaint)
            }

            // 3. Date label on X-axis (Smart non-overlapping)
            if (indicesToLabel.contains(index)) {
                val parts = record.date.split("-")
                val shortDate = if (parts.size == 3) "${parts[2]}.${parts[1]}." else record.date
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(shortDate, centerX, height - 12f, textPaint)
            }
        }

        return bitmap
    }
}
