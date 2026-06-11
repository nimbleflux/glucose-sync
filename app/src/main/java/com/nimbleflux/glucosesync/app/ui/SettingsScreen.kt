package com.nimbleflux.glucosesync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.nimbleflux.glucosesync.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUnit: String,
    onUnitChange: (String) -> Unit,
    alertsEnabled: Boolean,
    onAlertsEnabledChange: (Boolean) -> Unit,
    highThresholdMmol: Double,
    onHighThresholdChange: (Double) -> Unit,
    lowThresholdMmol: Double,
    onLowThresholdChange: (Double) -> Unit,
    overrideDnd: Boolean,
    onOverrideDndChange: (Boolean) -> Unit,
    alertRepeatMinutes: Int,
    onAlertRepeatChange: (Int) -> Unit,
    alertSound: Boolean,
    onAlertSoundChange: (Boolean) -> Unit,
    alertVibrate: Boolean,
    onAlertVibrateChange: (Boolean) -> Unit,
    alertVibrateDuration: Int,
    onAlertVibrateDurationChange: (Int) -> Unit,
    deltaMinutes: Int,
    onDeltaMinutesChange: (Int) -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    showWearInstall: Boolean,
    onInstallWearApp: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val isMmol = currentUnit == "mmol/L"
    val highDisplay = if (isMmol) highThresholdMmol else highThresholdMmol * 18
    val lowDisplay = if (isMmol) lowThresholdMmol else lowThresholdMmol * 18
    val unitLabel = currentUnit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
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
                .padding(horizontal = 24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.settings_glucose_unit),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_glucose_unit_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    val units = listOf("mmol/L" to stringResource(R.string.unit_mmol_description), "mg/dL" to stringResource(R.string.unit_mgdl_description))
                    units.forEach { (unit, description) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentUnit == unit,
                                    onClick = { onUnitChange(unit) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentUnit == unit,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(unit, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (unit == "mmol/L") {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                stringResource(R.string.settings_delta_window),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_delta_window_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10, 15).forEach { mins ->
                            FilterChip(
                                selected = deltaMinutes == mins,
                                onClick = { onDeltaMinutesChange(mins) },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                stringResource(R.string.settings_alerts),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_alerts_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_enable_alerts), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.settings_enable_alerts_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = alertsEnabled,
                            onCheckedChange = onAlertsEnabledChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_override_dnd), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.settings_override_dnd_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = overrideDnd,
                            onCheckedChange = onOverrideDndChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_sound), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.settings_sound_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = alertSound,
                            onCheckedChange = onAlertSoundChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_vibration), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.settings_vibration_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = alertVibrate,
                            onCheckedChange = onAlertVibrateChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (alertVibrate) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(stringResource(R.string.settings_vibration_duration), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 5, 10, 15).forEach { secs ->
                                    FilterChip(
                                        selected = alertVibrateDuration == secs,
                                        onClick = { onAlertVibrateDurationChange(secs) },
                                        label = { Text("${secs}s") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (alertsEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.settings_thresholds, unitLabel),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ThresholdSlider(
                            label = stringResource(R.string.settings_high_threshold),
                            value = highDisplay,
                            onValueChange = { v ->
                                val mmol = if (isMmol) v.toDouble() else v.toDouble() / 18.0
                                onHighThresholdChange(mmol)
                            },
                            range = if (isMmol) 5.0f..22.0f else 90f..400f,
                            unitLabel = unitLabel
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ThresholdSlider(
                            label = stringResource(R.string.settings_low_threshold),
                            value = lowDisplay,
                            onValueChange = { v ->
                                val mmol = if (isMmol) v.toDouble() else v.toDouble() / 18.0
                                onLowThresholdChange(mmol)
                            },
                            range = if (isMmol) 1.0f..6.0f else 18f..108f,
                            unitLabel = unitLabel
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column {
                            Text(
                                stringResource(R.string.settings_repeat_every),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 5, 10, 15, 30).forEach { mins ->
                                    val selected = alertRepeatMinutes == mins
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onAlertRepeatChange(mins) },
                                        label = { Text("${mins}m") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_conversion_factor), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(stringResource(R.string.settings_conversion_formula), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    val themes = listOf(
                        "system" to stringResource(R.string.theme_system),
                        "light" to stringResource(R.string.theme_light),
                        "dark" to stringResource(R.string.theme_dark)
                    )
                    themes.forEachIndexed { index, (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == mode,
                                    onClick = { onThemeChange(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        }
                        if (index < themes.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (showWearInstall) {
                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    stringResource(R.string.settings_wear_os),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.wear_app_not_installed),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
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
                            onClick = onInstallWearApp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.wear_install_button),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                stringResource(R.string.settings_privacy_data),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_privacy_data_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PrivacyItem(
                        title = stringResource(R.string.privacy_stored_locally),
                        detail = stringResource(R.string.privacy_stored_locally_detail)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrivacyItem(
                        title = stringResource(R.string.privacy_third_party),
                        detail = stringResource(R.string.privacy_third_party_detail)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrivacyItem(
                        title = stringResource(R.string.privacy_watch_sync),
                        detail = stringResource(R.string.privacy_watch_sync_detail)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrivacyItem(
                        title = stringResource(R.string.privacy_no_analytics),
                        detail = stringResource(R.string.privacy_no_analytics_detail)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.medical_disclaimer),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Double,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    unitLabel: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(
                String.format("%.1f %s", value, unitLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun PrivacyItem(title: String, detail: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Spacer(modifier = Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
