package com.nimbleflux.glucosesync.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nimbleflux.glucosesync.app.service.GlucosePollingService
import com.nimbleflux.glucosesync.app.viewmodel.MainViewModel
import com.nimbleflux.glucosesync.shared.provider.ProviderRegistry

class MainActivity : ComponentActivity() {

    private val viewModel by lazy { MainViewModel(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsState()
            val context = LocalContext.current

            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted && state.isLoggedIn) {
                    startPollingService(context)
                }
            }

            LaunchedEffect(state.isLoggedIn) {
                if (state.isLoggedIn && !state.isDemo) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            startPollingService(context)
                        } else {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        startPollingService(context)
                    }
                }
            }

            GlucoseSyncTheme(themeMode = state.themeMode) {
                when {
                    state.showProviderPicker -> {
                        ProviderPickerScreen(
                            onProviderSelected = { viewModel.selectProvider(it) },
                            onDemoLogin = { viewModel.loginDemo() },
                            showBack = state.isLoggedIn,
                            onBack = { viewModel.hideProviderPicker() }
                        )
                    }
                    state.showSettings -> {
                        SettingsScreen(
                            currentUnit = state.glucoseUnit,
                            onUnitChange = { viewModel.setUnit(it) },
                            alertsEnabled = state.alertsEnabled,
                            onAlertsEnabledChange = { viewModel.setAlertsEnabled(it) },
                            highThresholdMmol = state.highThresholdMmol,
                            onHighThresholdChange = { viewModel.setHighThreshold(it) },
                            lowThresholdMmol = state.lowThresholdMmol,
                            onLowThresholdChange = { viewModel.setLowThreshold(it) },
                            overrideDnd = state.overrideDnd,
                            onOverrideDndChange = { viewModel.setOverrideDnd(it) },
                            alertRepeatMinutes = state.alertRepeatMinutes,
                            onAlertRepeatChange = { viewModel.setAlertRepeatMinutes(it) },
                            alertSound = state.alertSound,
                            onAlertSoundChange = { viewModel.setAlertSound(it) },
                            alertVibrate = state.alertVibrate,
                            onAlertVibrateChange = { viewModel.setAlertVibrate(it) },
                            alertVibrateDuration = state.alertVibrateDuration,
                            onAlertVibrateDurationChange = { viewModel.setAlertVibrateDuration(it) },
                            themeMode = state.themeMode,
                            onThemeChange = { viewModel.setThemeMode(it) },
                            showWearInstall = state.watchPaired && !state.wearAppInstalled,
                            onInstallWearApp = { viewModel.openWatchPlayStore() },
                            onBack = { viewModel.hideSettings() }
                        )
                    }
                    state.showPatientPicker -> {
                        PatientPickerScreen(
                            patients = state.patients,
                            onSelect = { viewModel.selectPatient(it) },
                            onBack = { viewModel.cancelPatientPicker() }
                        )
                    }
                    state.isLoggedIn -> {
                        GlucoseScreen(
                            glucose = state.glucose,
                            unit = state.glucoseUnit,
                            trend = state.trend,
                            lastUpdate = state.lastUpdate,
                            sensorActive = state.sensorActive,
                            realname = state.realname,
                            isDemo = state.isDemo,
                            isLoading = state.isLoading,
                            error = state.error,
                            history = state.historyDisplay,
                            timeInRange = state.timeInRange,
                            averageGlucose = state.averageGlucose,
                            highThreshold = state.highThreshold,
                            lowThreshold = state.lowThreshold,
                            showWearInstallBanner = state.watchPaired && !state.wearAppInstalled && !state.wearBannerDismissed,
                            onRefresh = { viewModel.refreshGlucose() },
                            onLogout = {
                                context.stopService(Intent(context, GlucosePollingService::class.java))
                                viewModel.logout()
                            },
                            onSettings = { viewModel.showSettings() },
                            onInstallWearApp = { viewModel.openWatchPlayStore() },
                            onDismissWearBanner = { viewModel.dismissWearBanner() }
                        )
                    }
                    state.selectedProviderId != null -> {
                        val config = ProviderRegistry.getConfig(state.selectedProviderId!!)
                        LoginScreen(
                            providerName = config?.displayName ?: "CGM",
                            providerId = state.selectedProviderId!!,
                            authType = config?.authType ?: com.nimbleflux.glucosesync.shared.provider.AuthType.USERNAME_PASSWORD,
                            onLogin = { u, p, b -> viewModel.login(u, p, b) },
                            onDemoLogin = { viewModel.loginDemo() },
                            onBack = { viewModel.logout() },
                            isLoading = state.isLoading,
                            error = state.error
                        )
                    }
                    else -> {
                        ProviderPickerScreen(
                            onProviderSelected = { viewModel.selectProvider(it) },
                            onDemoLogin = { viewModel.loginDemo() }
                        )
                    }
                }
            }
        }
    }

    private fun startPollingService(context: android.content.Context) {
        val intent = Intent(context, GlucosePollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
