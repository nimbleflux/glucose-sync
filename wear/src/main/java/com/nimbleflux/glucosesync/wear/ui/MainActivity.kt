package com.nimbleflux.glucosesync.wear.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TitleCard
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import com.nimbleflux.glucosesync.wear.repository.WatchGlucoseState
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val repo by lazy { GlucoseRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by repo.state.collectAsState(initial = WatchGlucoseState())

            RequestFreshDataOnResume()

            if (state.glucose > 0.0 && !state.isStale) {
                GlucoseDashboard(state)
            } else if (state.glucose > 0.0 && state.isStale) {
                StaleGlucoseScreen(state)
            } else {
                WaitingForDataScreen()
            }
        }
    }

    @Composable
    private fun RequestFreshDataOnResume() {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    requestFreshData()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    private fun requestFreshData() {
        try {
            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                val messageClient = Wearable.getMessageClient(this)
                for (node in nodes) {
                    messageClient.sendMessage("/request_glucose", node.id, ByteArray(0))
                }
            }
        } catch (_: Exception) { }
    }
}

@Composable
private fun GlucoseDashboard(state: WatchGlucoseState) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { GlucoseHero(state) }

        if (state.history.size >= 4) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                WatchSparkline(
                    history = state.history,
                    highThreshold = state.highThreshold,
                    lowThreshold = state.lowThreshold,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(60.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))
            StatsRow(state)
        }

        if (state.iob != null || state.basalRate != null || state.lastBolus != null || state.remainingDose != null) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                PumpCard(state)
            }
        }

        if (state.batteryPercent != null) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                BatteryChip(state)
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))
            LastUpdatedText(state.timestamp)
        }
    }
}

@Composable
private fun GlucoseHero(state: WatchGlucoseState) {
    val inRange = state.glucose in state.lowThreshold..state.highThreshold
    val nearBoundary = !inRange && (
        state.glucose >= state.lowThreshold - 0.5 && state.glucose <= state.lowThreshold ||
        state.glucose <= state.highThreshold + 0.5 && state.glucose >= state.highThreshold
    )
    val color = when {
        inRange -> MaterialTheme.colors.primary
        nearBoundary -> Color(0xFFFF9800)
        else -> MaterialTheme.colors.error
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            if (state.trend.isNotEmpty()) {
                Text(
                    text = state.trend,
                    fontSize = 24.sp,
                    color = color,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                text = String.format("%.1f", state.glucose),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.unit,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            state.delta?.let { delta ->
                Spacer(modifier = Modifier.width(6.dp))
                val deltaColor = when {
                    delta > 0.5 || delta < -0.5 -> MaterialTheme.colors.error
                    delta > 0.1 || delta < -0.1 -> Color(0xFFFF9800)
                    else -> MaterialTheme.colors.primary
                }
                Text(
                    text = (if (delta >= 0) "+" else "") + String.format("%.1f", delta),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = deltaColor
                )
            }
        }
    }
}

@Composable
private fun StatsRow(state: WatchGlucoseState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        state.timeInRange?.let { tir ->
            StatChip(
                label = "TIR",
                value = String.format("%.0f%%", tir * 100),
                color = when {
                    tir >= 0.7 -> MaterialTheme.colors.primary
                    tir >= 0.5 -> Color(0xFFFF9800)
                    else -> MaterialTheme.colors.error
                }
            )
        }
        state.averageGlucose?.let { avg ->
            StatChip(
                label = "Avg",
                value = String.format("%.1f", avg),
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        if (state.history.isNotEmpty()) {
            val high = state.history.maxOf { it.second }
            val low = state.history.minOf { it.second }
            StatChip(
                label = "Range",
                value = String.format("%.1f / %.1f", low, high),
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PumpCard(state: WatchGlucoseState) {
    val parts = mutableListOf<String>()
    state.iob?.let { parts.add("IOB ${String.format("%.1f", it)}U") }
    state.basalRate?.let { parts.add("Basal ${String.format("%.1f", it)}U/h") }
    state.lastBolus?.let { parts.add("Bolus ${String.format("%.1f", it)}U") }
    state.remainingDose?.let { parts.add("Res ${String.format("%.0f", it)}U") }

    TitleCard(
        onClick = { },
        title = {
            Text(
                text = "Pump",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.primary
            )
        },
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Text(
            text = parts.joinToString("  ·  "),
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurfaceVariant
        )
    }
}

@Composable
private fun BatteryChip(state: WatchGlucoseState) {
    state.batteryPercent?.let { battery ->
        Chip(
            onClick = { },
            label = {
                Text(
                    text = "Sensor Battery",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            },
            secondaryLabel = {
                Text(
                    text = String.format("%.0f%%", battery * 100),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        battery > 0.5 -> MaterialTheme.colors.primary
                        battery > 0.2 -> Color(0xFFFF9800)
                        else -> MaterialTheme.colors.error
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(0.92f),
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

@Composable
private fun LastUpdatedText(timestamp: Long) {
    val now = System.currentTimeMillis() / 1000
    val diffSec = now - timestamp
    val text = when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> String.format("%d min ago", diffSec / 60)
        else -> String.format("%d h ago", diffSec / 3600)
    }
    Text(
        text = text,
        fontSize = 10.sp,
        color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun StaleGlucoseScreen(state: WatchGlucoseState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
        Spacer(modifier = Modifier.height(8.dp))
        LastUpdatedText(state.timestamp)
    }
}

@Composable
private fun WaitingForDataScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_glucosesync),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            indicatorColor = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.waiting_for_data),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.waiting_for_data_hint),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
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
    val neutralColor = MaterialTheme.colors.primary
    val warnColor = Color(0xFFFF9800)
    val alertColor = MaterialTheme.colors.error

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

        fun yForValue(v: Double): Float = h - ((v - minVal) / range * h).toFloat()
        fun xForTimestamp(ts: Long): Float {
            val rel = (ts - history.first().first).toFloat() / historyDuration.toFloat()
            return w * rel.coerceIn(0f, 1f)
        }

        val highY = yForValue(highThreshold)
        drawRect(highColor, Offset(0f, 0f), Size(w, highY))
        val lowY = yForValue(lowThreshold)
        drawRect(lowColor, Offset(0f, lowY), Size(w, h - lowY))

        val fillPath = Path()
        for (i in history.indices) {
            val x = xForTimestamp(history[i].first)
            val y = yForValue(history[i].second)
            if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
        }
        fillPath.lineTo(xForTimestamp(history.last().first), h)
        fillPath.lineTo(xForTimestamp(history.first().first), h)
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
            drawLine(segmentColor, Offset(xForTimestamp(history[i - 1].first), yForValue(prev)),
                Offset(xForTimestamp(history[i].first), yForValue(curr)), strokeWidth = 2f)
        }

        val lastX = xForTimestamp(history.last().first)
        val lastY = yForValue(history.last().second)
        drawCircle(neutralColor, radius = 3f, center = Offset(lastX, lastY))
        drawCircle(Color.White, radius = 1.5f, center = Offset(lastX, lastY))

        val timeStart = history.first().first
        val timeEnd = history.last().first
        val stepSec = if (historyDuration > 5400) 1800L else 900L
        val firstLabel = ((timeStart + stepSec - 1) / stepSec) * stepSec
        var t = firstLabel
        while (t < timeEnd) {
            val x = xForTimestamp(t)
            val cal = Calendar.getInstance().apply { timeInMillis = t * 1000 }
            val label = String.format("%d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            drawContext.canvas.nativeCanvas.drawText(label, x, h + labelArea - 2.dp.toPx(), timeLabelPaint)
            t += stepSec
        }
    }
}
