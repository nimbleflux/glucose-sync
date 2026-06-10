package com.nimbleflux.glucosesync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimbleflux.glucosesync.app.R
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
    error: String?,
    history: List<GlucoseHistoryPoint>,
    timeInRange: Int,
    averageGlucose: Double?,
    highThreshold: Double,
    lowThreshold: Double,
    showWearInstallBanner: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    onInstallWearApp: () -> Unit,
    onDismissWearBanner: () -> Unit
) {
    Scaffold(
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.content_desc_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            UserInfoRow(realname, isDemo)

            if (showWearInstallBanner) {
                Spacer(modifier = Modifier.height(8.dp))
                WearInstallBanner(onInstall = onInstallWearApp, onDismiss = onDismissWearBanner)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sensorActive && glucose != null) {
                val displayGlucose = if (unit == "mg/dL") glucose * 18 else glucose
                ActiveSection(displayGlucose, unit, trend, lastUpdate, history,
                    highThreshold, lowThreshold, timeInRange, averageGlucose)
            } else {
                InactiveSection(error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
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
    timeInRange: Int,
    averageGlucose: Double?
) {
    val inRange = glucose in lowThreshold..highThreshold
    val glucoseColor = when {
        glucose < lowThreshold || glucose > highThreshold -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
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
                    lineHeight = 72.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
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
                Text(stringResource(R.string.history_24h), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                GlucoseChart(
                    history = history,
                    highThreshold = highThreshold,
                    lowThreshold = lowThreshold
                )
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
            value = "$timeInRange%",
            icon = Icons.Filled.TrackChanges,
            modifier = Modifier.weight(1f),
            highlight = timeInRange >= 70
        )
        StatCard(
            title = stringResource(R.string.stat_average),
            value = if (averageGlucose != null) String.format("%.1f", averageGlucose) else "--",
            icon = Icons.Filled.Speed,
            modifier = Modifier.weight(1f)
        )
    }

    val historyHigh = history.maxOfOrNull { it.glucoseMmol }
    val historyLow = history.minOfOrNull { it.glucoseMmol }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = stringResource(R.string.stat_high),
            value = if (historyHigh != null) String.format("%.1f", historyHigh) else "--",
            icon = Icons.Filled.ArrowUpward,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = stringResource(R.string.stat_low),
            value = if (historyLow != null) String.format("%.1f", historyLow) else "--",
            icon = Icons.Filled.ArrowDownward,
            modifier = Modifier.weight(1f)
        )
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
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.content_desc_dismiss),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}
