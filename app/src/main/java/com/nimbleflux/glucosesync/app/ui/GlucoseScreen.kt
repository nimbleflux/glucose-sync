package com.nimbleflux.glucosesync.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimbleflux.glucosesync.app.R
import com.nimbleflux.glucosesync.shared.domain.AlertEntry
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseScreen(
    glucose: Double?,
    unit: String,
    trend: String,
    lastUpdate: Long?,
    sensorActive: Boolean,
    realname: String,
    isDemo: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    error: String?,
    refreshError: String?,
    history: List<GlucoseHistoryPoint>,
    highThreshold: Double,
    lowThreshold: Double,
    showWearInstallBanner: Boolean,
    notificationsDenied: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    iob: Double?,
    delta: Double?,
    batteryPercent: Double?,
    basalRate: Double?,
    lastBolus: Double?,
    lastBolusTime: Long?,
    remainingDose: Double?,
    alerts: List<AlertEntry>,
    windowHours: Int = 24,
    onWindowHoursChange: (Int) -> Unit = {},
    providerId: String? = null,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onInstallWearApp: () -> Unit,
    onDismissWearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val refreshErrorString = refreshError

    // Debug mock injection state (only used when BuildConfig.DEBUG and
    // xDrip+ is the active provider).
    var mockCounter by remember { mutableIntStateOf(0) }
    var autoInject by remember { mutableStateOf(false) }
    val showMockButton = com.nimbleflux.glucosesync.app.BuildConfig.DEBUG && providerId == "xdrip"

    LaunchedEffect(refreshErrorString) {
        refreshErrorString?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Auto-inject mock readings every 15 seconds when toggled on.
    LaunchedEffect(autoInject) {
        while (autoInject) {
            kotlinx.coroutines.delay(15_000)
            sendMockXdripReading(context, mockCounter++)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_glucosesync),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (isLoading || isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.content_desc_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (showMockButton) {
                ExtendedFloatingActionButton(
                    onClick = { sendMockXdripReading(context, mockCounter++) },
                    text = {
                        Text(
                            if (autoInject) "Auto" else "Inject",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Filled.Science,
                            contentDescription = "Inject mock xDrip+ reading"
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.combinedClickable(
                        onClick = { sendMockXdripReading(context, mockCounter++) },
                        onLongClick = { autoInject = !autoInject }
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                if (realname.isNotBlank() || isDemo) {
                    UserInfoRow(realname, isDemo)
                }

            if (showWearInstallBanner) {
                Spacer(modifier = Modifier.height(8.dp))
                WearInstallBanner(onInstall = onInstallWearApp, onDismiss = onDismissWearBanner)
            }

            if (notificationsDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                NotificationDeniedBanner(onGrant = onRequestNotificationPermission)
            }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (sensorActive && glucose != null) {
                    val displayGlucose = if (unit == "mg/dL") glucose * 18 else glucose
                    ActiveSection(
                        displayGlucose, unit, trend, lastUpdate, history,
                        highThreshold, lowThreshold,
                        iob, delta, batteryPercent, basalRate, lastBolus, lastBolusTime, remainingDose, alerts,
                        windowHours = windowHours,
                        onWindowHoursChange = onWindowHoursChange
                    )
                } else {
                    InactiveSection(error)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UserInfoRow(realname: String, isDemo: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PersonOutline, contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(realname, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isDemo) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.badge_demo), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun ActiveSection(
    glucose: Double,
    unit: String,
    trend: String,
    lastUpdate: Long?,
    history: List<GlucoseHistoryPoint>,
    highThreshold: Double,
    lowThreshold: Double,
    iob: Double?,
    delta: Double?,
    batteryPercent: Double?,
    basalRate: Double?,
    lastBolus: Double?,
    lastBolusTime: Long?,
    remainingDose: Double?,
    alerts: List<AlertEntry>,
    windowHours: Int = 24,
    onWindowHoursChange: (Int) -> Unit = {}
) {
    val inRange = glucose in lowThreshold..highThreshold
    val glucoseColor = when {
        glucose < lowThreshold || glucose > highThreshold -> MaterialTheme.colorScheme.error
        // In-range uses the green tertiary role, leaving primary (blue) for
        // chrome and accents. Communicates "safe zone" at a glance.
        else -> MaterialTheme.colorScheme.tertiary
    }
    val stateLabel = when {
        glucose > highThreshold -> stringResource(R.string.state_high)
        glucose < lowThreshold -> stringResource(R.string.state_low)
        else -> null
    }

    // Hoist the window-filtered history so both the chart and the stats
    // cards below it use the same data set. Stats now reflect what the
    // user is actually looking at, not the full 24h.
    val nowSec = System.currentTimeMillis() / 1000
    val windowedHistory = remember(history, windowHours) {
        val cutoff = nowSec - windowHours * 3600L
        history.filter { it.timestamp >= cutoff }
    }
    val windowedTIR = if (windowedHistory.isNotEmpty()) {
        val inRangeCount = windowedHistory.count {
            val g = if (unit == "mg/dL") it.glucoseMmol * 18 else it.glucoseMmol
            g >= lowThreshold && g <= highThreshold
        }
        inRangeCount * 100 / windowedHistory.size
    } else 0
    val windowedAvg = windowedHistory.takeIf { it.isNotEmpty() }
        ?.map { if (unit == "mg/dL") it.glucoseMmol * 18 else it.glucoseMmol }
        ?.average()
    val windowedHigh = windowedHistory.maxOfOrNull {
        if (unit == "mg/dL") it.glucoseMmol * 18 else it.glucoseMmol
    }
    val windowedLow = windowedHistory.minOfOrNull {
        if (unit == "mg/dL") it.glucoseMmol * 18 else it.glucoseMmol
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%.1f", glucose),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = glucoseColor,
                    lineHeight = 72.sp,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Glucose ${String.format("%.1f", glucose)} $unit${stateLabel?.let { ", $it" } ?: ""}"
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (stateLabel != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = glucoseColor
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (trend.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = glucoseColor.copy(alpha = 0.15f)
                ) {
                    Text(trend, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium, color = glucoseColor)
                }
            }

            lastUpdate?.let { ts ->
                Spacer(modifier = Modifier.height(10.dp))
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.updated_at, sdf.format(Date(ts * 1000))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (history.size >= 2) {
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.history_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TimeRangeSelector(
                        selectedHours = windowHours,
                        onSelect = onWindowHoursChange
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val chartDescription = if (windowedHistory.size >= 2) {
                    val minV = windowedHistory.minOf { it.glucoseMmol }
                    val maxV = windowedHistory.maxOf { it.glucoseMmol }
                    val inRangeCount = windowedHistory.count { it.glucoseMmol in lowThreshold..highThreshold }
                    val pct = (inRangeCount * 100) / windowedHistory.size
                    "$windowHours hour glucose chart. Min ${"%.1f".format(minV)}, max ${"%.1f".format(maxV)}, $pct percent in range."
                } else {
                    "$windowHours hour glucose chart. Not enough data yet."
                }
                if (windowedHistory.size >= 2) {
                    GlucoseChart(
                        history = windowedHistory,
                        highThreshold = highThreshold,
                        lowThreshold = lowThreshold,
                        windowHours = windowHours,
                        modifier = Modifier.semantics { contentDescription = chartDescription }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.not_enough_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = stringResource(R.string.stat_time_in_range),
            value = "$windowedTIR%",
            icon = Icons.Filled.TrackChanges,
            modifier = Modifier.weight(1f),
            highlight = windowedTIR >= 70
        )
        StatCard(
            title = stringResource(R.string.stat_average),
            value = if (windowedAvg != null) String.format("%.1f", windowedAvg) else "--",
            icon = Icons.Filled.Speed,
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = stringResource(R.string.stat_high),
            value = if (windowedHigh != null) String.format("%.1f", windowedHigh) else "--",
            icon = Icons.Filled.ArrowUpward,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = stringResource(R.string.stat_low),
            value = if (windowedLow != null) String.format("%.1f", windowedLow) else "--",
            icon = Icons.Filled.ArrowDownward,
            modifier = Modifier.weight(1f)
        )
    }

    if (delta != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.stat_delta), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                val sign = if (delta >= 0) "+" else ""
                Text(
                    "$sign${String.format("%.1f", delta)} $unit",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    val hasPumpData = iob != null || basalRate != null || lastBolus != null || remainingDose != null
    if (hasPumpData) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.pump_info_title), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iob != null) {
                StatCard(
                    title = stringResource(R.string.stat_iob),
                    value = String.format("%.1f U", iob),
                    icon = Icons.Filled.WaterDrop,
                    modifier = Modifier.weight(1f)
                )
            }
            if (basalRate != null) {
                StatCard(
                    title = stringResource(R.string.stat_basal_rate),
                    value = String.format("%.2f U/h", basalRate),
                    icon = Icons.Filled.Speed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (lastBolus != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Medication, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.stat_last_bolus), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        String.format("%.1f U", lastBolus),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (lastBolusTime != null && lastBolusTime > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val bolusSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text(
                            bolusSdf.format(Date(lastBolusTime * 1000)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (remainingDose != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.stat_reservoir),
                    value = String.format("%.1f U", remainingDose),
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f)
                )
                if (batteryPercent != null) {
                    StatCard(
                        title = stringResource(R.string.stat_battery),
                        value = "${(batteryPercent * 100).toInt()}%",
                        icon = Icons.Filled.BatteryStd,
                        modifier = Modifier.weight(1f),
                        highlight = batteryPercent > 0.25
                    )
                }
            }
        }
    } else if (batteryPercent != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.stat_battery),
                value = "${(batteryPercent * 100).toInt()}%",
                icon = Icons.Filled.BatteryStd,
                modifier = Modifier.weight(1f),
                highlight = batteryPercent > 0.25
            )
        }
    }

    if (alerts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.alerts_recent_title), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))

        alerts.take(5).forEach { alert ->
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.timestamp * 1000))
            val icon = when (alert.type) {
                "pump" -> Icons.Filled.Medication
                else -> Icons.Filled.Warning
            }
            val alertColor = when {
                alert.message.contains("Low", ignoreCase = true) -> MaterialTheme.colorScheme.error
                alert.message.contains("High", ignoreCase = true) -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = alertColor.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(timeStr, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(alert.message, style = MaterialTheme.typography.bodySmall,
                        color = alertColor, modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = if (highlight) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InactiveSection(error: String?) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.SensorsOff, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.no_sensor_data), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(error ?: stringResource(R.string.waiting_for_readings), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChecklistRow(stringResource(R.string.checklist_sensor_active))
                    Spacer(modifier = Modifier.height(6.dp))
                    ChecklistRow(stringResource(R.string.checklist_open_easysense))
                    Spacer(modifier = Modifier.height(6.dp))
                    ChecklistRow(stringResource(R.string.checklist_data_syncs))
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimeRangeSelector(
    selectedHours: Int,
    onSelect: (Int) -> Unit
) {
    val ranges = remember { listOf(3, 6, 12, 24) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics {
            contentDescription = "Time range selector, currently $selectedHours hours"
        }
    ) {
        ranges.forEach { hours ->
            val selected = hours == selectedHours
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                onClick = { onSelect(hours) },
                modifier = Modifier.semantics {
                    if (selected) {
                        contentDescription = "$hours hours, selected"
                    } else {
                        contentDescription = "Show last $hours hours"
                    }
                }
            ) {
                Text(
                    text = "${hours}h",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationDeniedBanner(onGrant: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.notifications_blocked_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    stringResource(R.string.notifications_blocked_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.notifications_blocked_action),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun WearInstallBanner(onInstall: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Watch,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.wear_app_not_installed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    stringResource(R.string.wear_app_not_installed_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onInstall,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.wear_install_button),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.content_desc_dismiss),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Send a mock xDrip+ broadcast to test the xDrip+ provider on the emulator
 * without having xDrip+ installed. Generates a realistic glucose pattern
 * using a sine wave so trends (rising, falling, stable) are visible.
 *
 * Counter drives the sine wave: 0=stable, rising, peak, falling, trough, ...
 */
private fun sendMockXdripReading(context: android.content.Context, counter: Int) {
    val t = counter * 0.4
    val baseMgDl = 120.0
    val amplitudeMgDl = 50.0
    val glucoseMgDl = baseMgDl + amplitudeMgDl * kotlin.math.sin(t)
    // Derivative of the sine -> gives a realistic slope (mg/dL per minute)
    val slopeMgDlPerMin = amplitudeMgDl * 0.4 * kotlin.math.cos(t)
    val deltaMgDl = slopeMgDlPerMin * 5.0 // approximate 5-min delta

    val intent = android.content.Intent("com.eveningoutpost.dexdrip.BgReading").apply {
        putExtra("bgValue", glucoseMgDl.toFloat())
        putExtra("bgSlope", slopeMgDlPerMin)
        putExtra("bgDelta", deltaMgDl.toFloat())
        putExtra("bgTimestamp", System.currentTimeMillis())
    }
    context.sendBroadcast(intent)
}
