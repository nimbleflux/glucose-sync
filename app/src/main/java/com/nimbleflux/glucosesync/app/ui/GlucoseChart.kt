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

    val values = history.map { it.glucoseMmol }
    val minVal = (values.minOrNull() ?: 0.0).coerceAtMost(lowThreshold - 1.0)
    val maxVal = (values.maxOrNull() ?: 22.0).coerceAtLeast(highThreshold + 1.0)
    val range = maxVal - minVal

    val now = System.currentTimeMillis() / 1000
    val windowSec = 43200.0
    val timeStart = now - windowSec

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

        for (i in 0..4) {
            val x = paddingLeft + chartW * i / 4
            drawLine(gridColor, Offset(x, paddingTop), Offset(x, paddingTop + chartH), strokeWidth = 1f)
        }

        val linePath = Path()
        val fillPath = Path()
        for (i in history.indices) {
            val x = xForTimestamp(history[i].timestamp)
            val y = yForValue(history[i].glucoseMmol)
            if (i == 0) { linePath.moveTo(x, y); fillPath.moveTo(x, y) }
            else { linePath.lineTo(x, y); fillPath.lineTo(x, y) }
        }
        fillPath.lineTo(xForTimestamp(history.last().timestamp), paddingTop + chartH)
        fillPath.lineTo(xForTimestamp(history.first().timestamp), paddingTop + chartH)
        fillPath.close()

        drawPath(fillPath, fillColor)
        drawPath(linePath, lineColor, style = Stroke(width = 3f))

        val lastX = xForTimestamp(history.last().timestamp)
        val lastY = yForValue(history.last().glucoseMmol)
        drawCircle(lineColor, radius = 5f, center = Offset(lastX, lastY))
        drawCircle(Color.White, radius = 2.5f, center = Offset(lastX, lastY))

        val xLabelCount = 5
        for (i in 0 until xLabelCount) {
            val frac = i.toFloat() / (xLabelCount - 1)
            val ts = timeStart + windowSec * frac
            val x = paddingLeft + chartW * frac
            val closestIdx = history.indices.minByOrNull { Math.abs(history[it].timestamp - ts) } ?: continue
            drawContext.canvas.nativeCanvas.drawText(
                sdf.format(Date(history[closestIdx].timestamp * 1000)),
                x,
                paddingTop + chartH + textSizePx + 4f,
                axisPaintCenter
            )
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

            drawCircle(Color.White, radius = 8f, center = Offset(px, py))
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
