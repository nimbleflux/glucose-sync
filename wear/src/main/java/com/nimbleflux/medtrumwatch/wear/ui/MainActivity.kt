package com.nimbleflux.medtrumwatch.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nimbleflux.medtrumwatch.wear.R
import com.nimbleflux.medtrumwatch.wear.repository.GlucoseRepository
import com.nimbleflux.medtrumwatch.wear.repository.WatchGlucoseState

class MainActivity : ComponentActivity() {

    private val repo by lazy { GlucoseRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by repo.state.collectAsState(initial = WatchGlucoseState())

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (state.glucose > 0.0 && !state.isStale) {
                    Text(
                        text = String.format("%.1f", state.glucose),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.glucose < state.lowThreshold || state.glucose > state.highThreshold)
                            MaterialTheme.colors.error else MaterialTheme.colors.primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.unit,
                            fontSize = 11.sp,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        if (state.trend.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = state.trend,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (state.history.size >= 4) {
                        Spacer(modifier = Modifier.height(8.dp))
                        WatchSparkline(
                            history = state.history,
                            highThreshold = state.highThreshold,
                            lowThreshold = state.lowThreshold,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(56.dp)
                        )
                    }

                    if (state.isDemo) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.glucose_demo),
                            fontSize = 9.sp,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                } else if (state.glucose > 0.0 && state.isStale) {
                    Text(
                        text = String.format("%.1f", state.glucose),
                        fontSize = 28.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.glucose_stale),
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.error
                    )
                } else {
                    Text(
                        text = stringResource(R.string.waiting_for_data),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchSparkline(
    history: List<Pair<Long, Double>>,
    highThreshold: Double,
    lowThreshold: Double,
    modifier: Modifier = Modifier
) {
    val fillColor = MaterialTheme.colors.primary.copy(alpha = 0.15f)
    val highColor = MaterialTheme.colors.error.copy(alpha = 0.12f)
    val lowColor = MaterialTheme.colors.error.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colors.onSurfaceVariant

    val values = history.map { it.second }
    val minVal = (values.minOrNull() ?: 0.0).coerceAtMost(lowThreshold - 1.0)
    val maxVal = (values.maxOrNull() ?: 22.0).coerceAtLeast(highThreshold + 1.0)
    val range = maxVal - minVal

    val timeLabelPaint = android.graphics.Paint().apply {
        color = (labelColor.red * 255).toInt().shl(16) or (labelColor.green * 255).toInt().shl(8) or (labelColor.blue * 255).toInt() or 0xFF000000.toInt()
        textSize = 8f * android.content.res.Resources.getSystem().displayMetrics.density
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    val neutralColor = MaterialTheme.colors.primary
    val warnColor = Color(0xFFFF9800)
    val alertColor = MaterialTheme.colors.error

    val historyDuration = (history.last().first - history.first().first).toDouble()
    val intervalSec = if (history.size > 1) historyDuration / (history.size - 1) else 900.0
    val minutesPerPoint = intervalSec / 60.0
    val fastPerMin = 0.15
    val rapidPerMin = 0.25
    val fastThreshold = fastPerMin * minutesPerPoint
    val rapidThreshold = rapidPerMin * minutesPerPoint

    Canvas(modifier = modifier) {
        val w = size.width
        val labelArea = 14.dp.toPx()
        val h = size.height - labelArea

        fun yForValue(v: Double): Float {
            return h - ((v - minVal) / range * h).toFloat()
        }

        fun xForIndex(i: Int): Float {
            return w * i / (history.size - 1)
        }

        val highY = yForValue(highThreshold)
        drawRect(highColor, Offset(0f, 0f), Size(w, highY))

        val lowY = yForValue(lowThreshold)
        drawRect(lowColor, Offset(0f, lowY), Size(w, h - lowY))

        val fillPath = Path()
        for (i in history.indices) {
            val x = xForIndex(i)
            val y = yForValue(history[i].second)
            if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
        }
        fillPath.lineTo(xForIndex(history.size - 1), h)
        fillPath.lineTo(xForIndex(0), h)
        fillPath.close()
        drawPath(fillPath, fillColor)

        for (i in 1 until history.size) {
            val prev = history[i - 1].second
            val curr = history[i].second
            val delta = curr - prev
            val segmentColor = when {
                delta > rapidThreshold -> alertColor
                delta > fastThreshold -> warnColor
                delta < -rapidThreshold -> alertColor
                delta < -fastThreshold -> warnColor
                else -> neutralColor
            }
            val x0 = xForIndex(i - 1)
            val y0 = yForValue(prev)
            val x1 = xForIndex(i)
            val y1 = yForValue(curr)
            drawLine(segmentColor, Offset(x0, y0), Offset(x1, y1), strokeWidth = 2f)
        }

        val lastX = xForIndex(history.size - 1)
        val lastY = yForValue(history.last().second)
        drawCircle(neutralColor, radius = 3f, center = Offset(lastX, lastY))
        drawCircle(Color.White, radius = 1.5f, center = Offset(lastX, lastY))

        val timeLabels = minOf(3, history.size)
        for (i in 0 until timeLabels) {
            val idx = history.size * i / (timeLabels - 1)
            val x = xForIndex(idx.coerceAtMost(history.size - 1))
            val ts = history[idx.coerceAtMost(history.size - 1)].first * 1000
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            val label = String.format("%d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            drawContext.canvas.nativeCanvas.drawText(
                label, x, h + labelArea - 2.dp.toPx(), timeLabelPaint
            )
        }
    }
}
