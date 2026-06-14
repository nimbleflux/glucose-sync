package com.nimbleflux.glucosesync.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GlucoseChart(
    history: List<GlucoseHistoryPoint>,
    highThreshold: Double,
    lowThreshold: Double,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) return

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.12f)
    val highColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    val lowColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tooltipBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    val tooltipFg = MaterialTheme.colorScheme.onSurface
    val alertColor = MaterialTheme.colorScheme.error
    val warnColor = MaterialTheme.colorScheme.secondary
    val dotColor = MaterialTheme.colorScheme.onPrimary

    val values = history.map { it.glucoseMmol }
    val minVal = (values.minOrNull() ?: 0.0).coerceAtMost(lowThreshold - 1.0)
    val maxVal = (values.maxOrNull() ?: 22.0).coerceAtLeast(highThreshold + 1.0)
    val range = maxVal - minVal

    val now = System.currentTimeMillis() / 1000
    val windowSec = 86400.0
    val earliestData = history.firstOrNull()?.timestamp?.toDouble() ?: (now - windowSec)
    val timeStart = maxOf(now - windowSec, earliestData)

    val density = LocalDensity.current
    val textSizePx: Float = with(density) { 11.sp.toPx() }
    val tooltipTextPx: Float = with(density) { 12.sp.toPx() }
    val tooltipLabelPx: Float = with(density) { 10.sp.toPx() }

    val paddingLeft = 44f
    val paddingRight = 12f
    val paddingTop = 16f
    val paddingBottom = 28f

    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    var hoveredIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(history) {
                detectTapGestures { offset ->
                    val chartW = size.width - paddingLeft - paddingRight
                    val idx = findClosestIndex(offset.x, history, paddingLeft, chartW, timeStart, windowSec)
                    hoveredIndex = if (idx >= 0) idx else -1
                }
            }
            .pointerInput(history) {
                detectDragGestures { change, _ ->
                    val chartW = size.width - paddingLeft - paddingRight
                    val idx = findClosestIndex(change.position.x, history, paddingLeft, chartW, timeStart, windowSec)
                    hoveredIndex = if (idx >= 0) idx else -1
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val chartW = w - paddingLeft - paddingRight
        val chartH = h - paddingTop - paddingBottom

        fun yForValue(v: Double): Float {
            return paddingTop + chartH - ((v - minVal) / range * chartH).toFloat()
        }

        fun xForTimestamp(ts: Long): Float {
            val rel = (ts - timeStart) / windowSec
            return paddingLeft + (chartW * rel).toFloat().coerceIn(0f, chartW)
        }

        val axisPaint = android.graphics.Paint().apply {
            textSize = textSizePx
            isAntiAlias = true
            color = labelColor.toArgb()
        }
        val axisPaintCenter = android.graphics.Paint().apply {
            textSize = textSizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            color = labelColor.toArgb()
        }

        drawRect(
            color = highColor,
            topLeft = Offset(paddingLeft, paddingTop),
            size = Size(chartW, yForValue(highThreshold) - paddingTop)
        )
        drawRect(
            color = lowColor,
            topLeft = Offset(paddingLeft, yForValue(lowThreshold)),
            size = Size(chartW, paddingTop + chartH - yForValue(lowThreshold))
        )

        val ySteps = 4
        val yStep = range / ySteps
        for (i in 0..ySteps) {
            val v = minVal + yStep * i
            val y = yForValue(v)
            drawLine(gridColor, Offset(paddingLeft, y), Offset(w - paddingRight, y), strokeWidth = 1f)
            axisPaint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.0f", v),
                paddingLeft - 8f,
                y + textSizePx / 3,
                axisPaint
            )
        }

        val stepSec = 21600L
        val firstLabelTs = ((timeStart.toLong() + stepSec - 1) / stepSec) * stepSec
        var gridTs = firstLabelTs
        while (gridTs < now) {
            val x = xForTimestamp(gridTs)
            drawLine(gridColor, Offset(x, paddingTop), Offset(x, paddingTop + chartH), strokeWidth = 1f)
            gridTs += stepSec
        }

        val fillPath = Path()
        for (i in history.indices) {
            val x = xForTimestamp(history[i].timestamp)
            val y = yForValue(history[i].glucoseMmol)
            if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
        }
        fillPath.lineTo(xForTimestamp(history.last().timestamp), paddingTop + chartH)
        fillPath.lineTo(xForTimestamp(history.first().timestamp), paddingTop + chartH)
        fillPath.close()
        drawPath(fillPath, fillColor)

        val stableColor = lineColor
        val intervalSec = if (history.size > 1) {
            (history.last().timestamp - history.first().timestamp).toDouble() / (history.size - 1)
        } else 300.0
        val minutesPerPoint = intervalSec / 60.0
        val fastThreshold = 0.15 * minutesPerPoint
        val rapidThreshold = 0.25 * minutesPerPoint

        for (i in 1 until history.size) {
            val prev = history[i - 1]
            val curr = history[i]
            val delta = curr.glucoseMmol - prev.glucoseMmol
            val segColor = when {
                delta > rapidThreshold -> alertColor
                delta > fastThreshold -> warnColor
                delta < -rapidThreshold -> alertColor
                delta < -fastThreshold -> warnColor
                else -> stableColor
            }
            drawLine(
                segColor,
                Offset(xForTimestamp(prev.timestamp), yForValue(prev.glucoseMmol)),
                Offset(xForTimestamp(curr.timestamp), yForValue(curr.glucoseMmol)),
                strokeWidth = 3f
            )
        }

        for (i in history.indices) {
            val x = xForTimestamp(history[i].timestamp)
            val y = yForValue(history[i].glucoseMmol)
            drawCircle(stableColor, radius = 2.5f, center = Offset(x, y))
        }

        val lastX = xForTimestamp(history.last().timestamp)
        val lastY = yForValue(history.last().glucoseMmol)
        drawCircle(stableColor, radius = 5f, center = Offset(lastX, lastY))
        drawCircle(dotColor, radius = 2.5f, center = Offset(lastX, lastY))

        var labelTs = firstLabelTs
        while (labelTs < now) {
            val x = xForTimestamp(labelTs)
            val cal = Calendar.getInstance().apply { timeInMillis = labelTs * 1000 }
            val label = String.format("%d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            drawContext.canvas.nativeCanvas.drawText(
                label, x, paddingTop + chartH + textSizePx + 4f, axisPaintCenter
            )
            labelTs += stepSec
        }

        if (hoveredIndex in history.indices) {
            val point = history[hoveredIndex]
            val px = xForTimestamp(point.timestamp)
            val py = yForValue(point.glucoseMmol)

            drawLine(
                gridColor.copy(alpha = 0.3f),
                Offset(px, paddingTop),
                Offset(px, paddingTop + chartH),
                strokeWidth = 1f
            )

            drawCircle(dotColor, radius = 8f, center = Offset(px, py))
            drawCircle(lineColor, radius = 6f, center = Offset(px, py))

            val valueText = String.format("%.1f", point.glucoseMmol)
            val timeText = sdf.format(Date(point.timestamp * 1000))

            val valuePaint = android.graphics.Paint().apply {
                textSize = tooltipTextPx
                isAntiAlias = true
                color = tooltipFg.toArgb()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val labelPaint = android.graphics.Paint().apply {
                textSize = tooltipLabelPx
                isAntiAlias = true
                color = labelColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val valWidth = valuePaint.measureText(valueText)
            val timeWidth = labelPaint.measureText(timeText)
            val boxW = maxOf(valWidth, timeWidth) + 24f
            val boxH = tooltipTextPx + tooltipLabelPx + 20f

            val tooltipX = px.coerceIn(boxW / 2 + 4f, w - boxW / 2 - 4f)
            val tooltipY = (py - boxH - 12f).coerceAtLeast(4f)

            drawRoundRect(
                color = tooltipBg,
                topLeft = Offset(tooltipX - boxW / 2, tooltipY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(8f, 8f)
            )

            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.drawText(
                valueText,
                tooltipX,
                tooltipY + tooltipTextPx + 6f,
                valuePaint
            )
            nativeCanvas.drawText(
                timeText,
                tooltipX,
                tooltipY + tooltipTextPx + tooltipLabelPx + 12f,
                labelPaint
            )
        }
    }
}

private fun findClosestIndex(x: Float, history: List<GlucoseHistoryPoint>, paddingLeft: Float, chartW: Float, timeStart: Double, windowSec: Double): Int {
    if (history.isEmpty()) return -1
    val rel = ((x - paddingLeft).toDouble() / chartW).coerceIn(0.0, 1.0)
    val targetTs = timeStart + windowSec * rel
    return history.indices.minByOrNull { Math.abs(history[it].timestamp - targetTs) } ?: -1
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
