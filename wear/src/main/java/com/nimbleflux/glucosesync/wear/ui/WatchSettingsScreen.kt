package com.nimbleflux.glucosesync.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.SelectableChip
import androidx.wear.compose.material.Text
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.WatchDisplaySettings

private fun hoursLabel(hours: Int): String = if (hours == 1) "1 hour" else "$hours hours"

/**
 * Watch-local display settings: which time span the graph covers and which
 * window TIR is computed over. Settings live only on the watch — the phone
 * is not involved. The side button (BackHandler) returns to the dashboard.
 */
@Composable
fun WatchSettingsScreen(
    settings: WatchDisplaySettings,
    onGraphWindowClick: () -> Unit,
    onTirWindowClick: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Settings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = onGraphWindowClick,
                    label = {
                        Text("Graph window", fontSize = 12.sp)
                    },
                    secondaryLabel = {
                        Text(
                            hoursLabel(settings.graphWindowHours),
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }
            item {
                Chip(
                    onClick = onTirWindowClick,
                    label = {
                        Text("TIR window", fontSize = 12.sp)
                    },
                    secondaryLabel = {
                        Text(
                            hoursLabel(settings.tirWindowHours),
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.watch_settings_hint),
                    fontSize = 9.sp,
                    color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        PositionIndicator(scalingLazyListState = listState)
    }
}

/**
 * Full-screen option list for one of the window settings. Tapping an option
 * applies it immediately and returns to the settings screen.
 */
@Composable
fun WindowPickerScreen(
    title: String,
    options: List<Int>,
    selectedHours: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(options.size) { i ->
                val hours = options[i]
                SelectableChip(
                    selected = hours == selectedHours,
                    onClick = { onSelect(hours); onBack() },
                    label = {
                        Text(hoursLabel(hours), fontSize = 12.sp)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 2.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        PositionIndicator(scalingLazyListState = listState)
    }
}
