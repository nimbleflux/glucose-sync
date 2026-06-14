package com.nimbleflux.glucosesync.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nimbleflux.glucosesync.app.BuildConfig
import com.nimbleflux.glucosesync.app.data.SettingsStore
import com.nimbleflux.glucosesync.app.domain.GlucoseCoordinator
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.domain.DemoData
import com.nimbleflux.glucosesync.shared.domain.AlertEntry
import com.nimbleflux.glucosesync.shared.domain.GlucoseAggregator
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.provider.AuthType
import com.nimbleflux.glucosesync.shared.provider.Connection
import com.nimbleflux.glucosesync.shared.provider.GlucoseError
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderCredentials
import com.nimbleflux.glucosesync.shared.provider.ProviderRegistry
import com.nimbleflux.glucosesync.app.ui.PatientInfo
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MainUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val glucose: Double? = null,
    val glucoseUnit: String = "mmol/L",
    val trend: String = "",
    val lastUpdate: Long? = null,
    val error: String? = null,
    val refreshError: String? = null,
    val realname: String = "",
    val sensorActive: Boolean = false,
    val isDemo: Boolean = false,
    val history: List<GlucoseHistoryPoint> = emptyList(),
    val showSettings: Boolean = false,
    val alertsEnabled: Boolean = true,
    val highThresholdMmol: Double = 10.0,
    val lowThresholdMmol: Double = 3.9,
    val overrideDnd: Boolean = true,
    val alertRepeatMinutes: Int = 5,
    val alertSound: Boolean = true,
    val alertVibrate: Boolean = true,
    val alertVibrateDuration: Int = 3,
    val selectedProviderId: String? = null,
    val showProviderPicker: Boolean = false,
    val showPatientPicker: Boolean = false,
    val patients: List<PatientInfo> = emptyList(),
    val watchPaired: Boolean = false,
    val wearAppInstalled: Boolean = true,
    val wearBannerDismissed: Boolean = false,
    val themeMode: String = "system",
    val iob: Double? = null,
    val delta: Double? = null,
    val batteryPercent: Double? = null,
    val basalRate: Double? = null,
    val lastBolus: Double? = null,
    val lastBolusTime: Long? = null,
    val remainingDose: Double? = null,
    val deltaMinutes: Int = 5,
    val alerts: List<AlertEntry> = emptyList(),
    val restoringSession: Boolean = true,
    val settingsLoaded: Boolean = false
) {
    val glucoseDisplay: Double?
        get() = glucose?.let { if (glucoseUnit == "mg/dL") it * 18 else it }

    val highThreshold: Double get() = if (glucoseUnit == "mg/dL") highThresholdMmol * 18 else highThresholdMmol
    val lowThreshold: Double get() = if (glucoseUnit == "mg/dL") lowThresholdMmol * 18 else lowThresholdMmol

    val timeInRange: Int get() {
        if (history.isEmpty()) return 0
        val inRange = history.count {
            val g = if (glucoseUnit == "mg/dL") it.glucoseMmol * 18 else it.glucoseMmol
            g >= lowThreshold && g <= highThreshold
        }
        return inRange * 100 / history.size
    }

    val averageGlucose: Double? get() {
        if (history.isEmpty()) return null
        val avg = history.map { it.glucoseMmol }.average()
        return if (glucoseUnit == "mg/dL") avg * 18 else avg
    }

    val historyDisplay: List<GlucoseHistoryPoint>
        get() = history.map {
            GlucoseHistoryPoint(it.timestamp, if (glucoseUnit == "mg/dL") it.glucoseMmol * 18 else it.glucoseMmol)
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)
    private val settingsStore = SettingsStore(application)
    private val coordinator = GlucoseCoordinator(application, settingsStore)
    private var provider: GlucoseProvider? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val refreshMutex = Mutex()
    private var demoPollingJob: kotlinx.coroutines.Job? = null
    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    init {
        checkWearCompanion()
        viewModelScope.launch {
            _uiState.collect { state ->
                if (state.isLoggedIn && !state.isDemo && autoRefreshJob?.isActive != true) {
                    startAutoRefresh()
                }
            }
        }
        viewModelScope.launch {
            val unit = settingsStore.getUnit()
            val alerts = settingsStore.getAlertsEnabled()
            val high = settingsStore.getHighThresholdMmol()
            val low = settingsStore.getLowThresholdMmol()
            val dnd = settingsStore.getOverrideDnd()
            val repeat = settingsStore.getAlertRepeatMinutes()
            val sound = settingsStore.getAlertSound()
            val vibrate = settingsStore.getAlertVibrate()
            val vibrateDuration = settingsStore.getAlertVibrateDuration()
            val themeMode = settingsStore.getThemeMode()
            val deltaMinutes = settingsStore.getDeltaMinutes()
            val wearBannerDismissed = settingsStore.getWearBannerDismissed()
            _uiState.update {
                it.copy(
                    glucoseUnit = unit,
                    alertsEnabled = alerts,
                    highThresholdMmol = high,
                    lowThresholdMmol = low,
                    overrideDnd = dnd,
                    alertRepeatMinutes = repeat,
                    alertSound = sound,
                    alertVibrate = vibrate,
                    alertVibrateDuration = vibrateDuration,
                    themeMode = themeMode,
                    deltaMinutes = deltaMinutes,
                    wearBannerDismissed = wearBannerDismissed,
                    settingsLoaded = true
                )
            }

            val selectedProvider = credentialStore.getSelectedProvider()
            if (selectedProvider != null) {
                val p = ProviderRegistry.create(selectedProvider, application, BuildConfig.DEBUG)
                provider = p
                val restored = p.restoreSession()
                if (restored) {
                    val displayName = credentialStore.getSessionDisplayName() ?: ""
                    _uiState.update {
                        it.copy(
                            restoringSession = false,
                            isLoggedIn = true,
                            isLoading = true,
                            realname = displayName,
                            selectedProviderId = selectedProvider
                        )
                    }

                    refreshGlucose()
                } else {
                    _uiState.update { it.copy(restoringSession = false) }
                }
            } else {
                _uiState.update { it.copy(restoringSession = false) }
            }
        }
    }

    fun selectProvider(providerId: String) {
        provider = ProviderRegistry.create(providerId, getApplication(), BuildConfig.DEBUG)
        _uiState.update { it.copy(selectedProviderId = providerId, showProviderPicker = false) }
    }

    fun showProviderPicker() {
        _uiState.update { it.copy(showProviderPicker = true) }
    }

    fun hideProviderPicker() {
        _uiState.update { it.copy(showProviderPicker = false) }
    }

    fun cancelPatientPicker() {
        _uiState.update { it.copy(showPatientPicker = false) }
    }

    fun login(username: String, password: String, baseUrl: String) {
        val p = provider ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val creds = ProviderCredentials.UsernamePassword(username, password, baseUrl)
            performLogin(p, creds)
        }
    }

    fun loginWithToken(url: String, token: String) {
        val p = provider ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val creds = ProviderCredentials.ApiToken(url, token)
            performLogin(p, creds)
        }
    }

    private suspend fun performLogin(p: GlucoseProvider, creds: ProviderCredentials) {
        try {
            val session = p.login(creds).getOrThrow()
            credentialStore.saveSelectedProvider(p.id)
            credentialStore.saveSessionDisplayName(session.displayName)

            val connections = fetchConnectionsForProvider(p)
            when {
                connections == null -> {
                    _uiState.update {
                        it.copy(isLoggedIn = true, isLoading = false, realname = session.displayName)
                    }
                    refreshGlucose()
                }
                connections.isEmpty() -> {
                    _uiState.update { it.copy(isLoading = false, error = "No patients found") }
                }
                connections.size == 1 -> selectSinglePatient(session.displayName, connections[0])
                else -> showPatientPicker(connections)
            }
        } catch (e: GlucoseError) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("Unable to resolve") == true -> "No internet connection"
                else -> e.message ?: "Something went wrong"
            }
            _uiState.update { it.copy(isLoading = false, error = msg) }
        }
    }

    private suspend fun fetchConnectionsForProvider(p: GlucoseProvider): List<Connection>? {
        return if (p.supportsConnections()) p.getConnections() else null
    }

    private suspend fun selectSinglePatient(sessionDisplayName: String, conn: Connection) {
        val p = provider ?: return
        p.selectPatient(conn.id)
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                isLoading = false,
                realname = conn.displayName.ifBlank { sessionDisplayName }
            )
        }
        refreshGlucose()
    }

    private fun showPatientPicker(connections: List<Connection>) {
        val unit = _uiState.value.glucoseUnit
        val patientInfos = connections.map { conn ->
            PatientInfo(
                patientId = conn.id,
                firstName = conn.displayName,
                lastName = "",
                sensorActive = conn.sensorActive,
                lastGlucose = formatLastGlucose(conn.lastGlucoseMmol, unit),
                displayUnit = unit
            )
        }
        _uiState.update {
            it.copy(isLoading = false, showPatientPicker = true, patients = patientInfos)
        }
    }

    private fun formatLastGlucose(glucoseMmol: Double?, unit: String): String =
        if (unit == "mg/dL") {
            glucoseMmol?.let { g -> "%.0f".format(g * 18) } ?: ""
        } else {
            glucoseMmol?.let { g -> "%.1f".format(g) } ?: ""
        }

    fun selectPatient(patientId: String) {
        viewModelScope.launch {
            val patientName = _uiState.value.patients.find { it.patientId == patientId }?.firstName
            provider?.selectPatient(patientId)
            if (patientName != null) {
                credentialStore.saveSessionDisplayName(patientName)
            }
            val displayName = patientName ?: credentialStore.getSessionDisplayName() ?: ""
            _uiState.update {
                it.copy(isLoggedIn = true, isLoading = false, showPatientPicker = false, realname = displayName)
            }
            refreshGlucose()
            startAutoRefresh()
        }
    }

    fun loginDemo() {
        val demoHistory = DemoData.generateHistory()
        val snapshot = DemoData.snapshot(demoHistory)
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                isLoading = false,
                realname = DemoData.demoDisplayName,
                isDemo = true,
                sensorActive = true,
                history = demoHistory,
                glucose = snapshot.glucose,
                trend = snapshot.trend.symbol,
                lastUpdate = snapshot.timestamp,
                delta = snapshot.delta,
                iob = snapshot.iob,
                batteryPercent = snapshot.batteryPercent,
                basalRate = snapshot.basalRate,
                lastBolus = snapshot.lastBolus,
                lastBolusTime = snapshot.lastBolusTime,
                remainingDose = snapshot.remainingDose,
                alerts = snapshot.alerts
            )
        }
        startDemoPolling()
    }

    private fun refreshDemoGlucose() {
        val currentHistory = _uiState.value.history.toMutableList()
        val snapshot = DemoData.snapshot(currentHistory)
        currentHistory.add(GlucoseHistoryPoint(snapshot.timestamp, snapshot.glucose ?: 5.6))
        val trimmed = GlucoseAggregator.trimTo24h(currentHistory)

        _uiState.update {
            it.copy(
                glucose = snapshot.glucose,
                trend = snapshot.trend.symbol,
                lastUpdate = snapshot.timestamp,
                sensorActive = true,
                error = null,
                history = trimmed,
                delta = snapshot.delta,
                iob = snapshot.iob,
                batteryPercent = snapshot.batteryPercent,
                basalRate = snapshot.basalRate,
                lastBolus = snapshot.lastBolus,
                lastBolusTime = snapshot.lastBolusTime,
                remainingDose = snapshot.remainingDose,
                alerts = snapshot.alerts
            )
        }
        snapshot.glucose?.let { _ ->
            coordinator.pushToWatch(snapshot, trimmed, snapshot.trend.symbol, snapshot.delta)
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(60_000)
                // Skip when the app is backgrounded - the foreground service
                // (5 min loop) keeps alerts flowing without burning battery
                // on UI refreshes nobody sees.
                val inForeground = ProcessLifecycleOwner.get()
                    .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (inForeground && _uiState.value.isLoggedIn && !_uiState.value.isDemo) {
                    refreshGlucose()
                }
            }
        }
    }

    private fun startDemoPolling() {
        demoPollingJob?.cancel()
        demoPollingJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshDemoGlucose()
            }
        }
    }

    fun refreshGlucose() {
        if (_uiState.value.isDemo) {
            refreshDemoGlucose()
            return
        }
        val p = provider ?: return
        viewModelScope.launch {
            refreshMutex.withLock {
                // Distinguish initial-load from silent refresh so we don't wipe
                // the dashboard every 60s when the auto-refresh fires.
                val hasPriorData = _uiState.value.glucose != null
                _uiState.update {
                    it.copy(
                        isLoading = !hasPriorData,
                        isRefreshing = hasPriorData,
                        error = null,
                        refreshError = null
                    )
                }
                coordinator.fetchAndProcess(p, _uiState.value.history)
                    .onSuccess { processed ->
                        val snapshot = processed.snapshot
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                glucose = snapshot.glucose,
                                lastUpdate = snapshot.timestamp,
                                sensorActive = snapshot.sensorActive,
                                trend = processed.trend.symbol,
                                error = if (!snapshot.sensorActive) "No active sensor" else null,
                                history = processed.history,
                                iob = snapshot.iob,
                                delta = processed.delta,
                                batteryPercent = snapshot.batteryPercent,
                                basalRate = snapshot.basalRate,
                                lastBolus = snapshot.lastBolus,
                                lastBolusTime = snapshot.lastBolusTime,
                                remainingDose = snapshot.remainingDose,
                                alerts = snapshot.alerts
                            )
                        }
                        // Alerts intentionally NOT fired from the VM loop.
                        // The foreground service's 5-min cycle is the single
                        // alerter - that matches the underlying CGM sensor
                        // update rate (Dexcom 5 min, Libre 1 min but alert
                        // windows are typically wider) and avoids hammering
                        // the provider API or duplicating alerts.
                    }
                    .onFailure { e ->
                        val msg = when (e) {
                            is GlucoseError.SessionExpired -> "Authentication expired. Try signing in again."
                            is GlucoseError.NetworkError -> "No internet connection"
                            is GlucoseError -> e.message
                            else -> when {
                                e.message?.contains("Unable to resolve") == true -> "No internet connection"
                                else -> e.message ?: "Could not fetch data"
                            }
                        }
                        // If we already have data on screen, treat this as a
                        // transient refresh error (Snackbar) rather than wiping
                        // the dashboard with a full-screen error.
                        val hasPriorData = _uiState.value.glucose != null
                        _uiState.update {
                            if (hasPriorData) {
                                it.copy(isLoading = false, isRefreshing = false, refreshError = msg)
                            } else {
                                it.copy(isLoading = false, isRefreshing = false, error = msg)
                            }
                        }
                    }
            }
        }
    }

    fun setUnit(unit: String) {
        viewModelScope.launch {
            settingsStore.setUnit(unit)
            _uiState.update { it.copy(glucoseUnit = unit) }
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAlertsEnabled(enabled)
            _uiState.update { it.copy(alertsEnabled = enabled) }
        }
    }

    fun setHighThreshold(mmol: Double) {
        viewModelScope.launch {
            settingsStore.setHighThresholdMmol(mmol)
            _uiState.update { it.copy(highThresholdMmol = mmol) }
        }
    }

    fun setLowThreshold(mmol: Double) {
        viewModelScope.launch {
            settingsStore.setLowThresholdMmol(mmol)
            _uiState.update { it.copy(lowThresholdMmol = mmol) }
        }
    }

    fun setOverrideDnd(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setOverrideDnd(enabled)
            _uiState.update { it.copy(overrideDnd = enabled) }
        }
    }

    fun setAlertRepeatMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setAlertRepeatMinutes(minutes)
            _uiState.update { it.copy(alertRepeatMinutes = minutes) }
        }
    }

    fun setAlertSound(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAlertSound(enabled)
            _uiState.update { it.copy(alertSound = enabled) }
        }
    }

    fun setAlertVibrate(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAlertVibrate(enabled)
            _uiState.update { it.copy(alertVibrate = enabled) }
        }
    }

    fun setAlertVibrateDuration(seconds: Int) {
        viewModelScope.launch {
            settingsStore.setAlertVibrateDuration(seconds)
            _uiState.update { it.copy(alertVibrateDuration = seconds) }
        }
    }

    fun showSettings() { _uiState.update { it.copy(showSettings = true) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setDeltaMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setDeltaMinutes(minutes)
            _uiState.update { it.copy(deltaMinutes = minutes) }
            if (_uiState.value.isLoggedIn && !_uiState.value.isDemo) {
                refreshGlucose()
            }
        }
    }

    fun dismissWearBanner() {
        _uiState.update { it.copy(wearBannerDismissed = true) }
        viewModelScope.launch { settingsStore.setWearBannerDismissed(true) }
    }

    fun logout() {
        demoPollingJob?.cancel()
        autoRefreshJob?.cancel()
        provider?.logout()
        // Cancel the WorkManager keepalive too - otherwise it would resurrect
        // the FGS within 15 minutes of logout, defeating the user's intent.
        com.nimbleflux.glucosesync.app.service.PollingWorker.cancel(getApplication())
        viewModelScope.launch {
            credentialStore.clear()
            _uiState.update { MainUiState(glucoseUnit = settingsStore.getUnit(), restoringSession = false) }
        }
    }


    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info ->
        val hasCapability = info.nodes.isNotEmpty()
        _uiState.update { it.copy(watchPaired = true, wearAppInstalled = hasCapability) }
    }

    private fun checkWearCompanion() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(app)
                val connectedNodes = Tasks.await(nodeClient.connectedNodes)
                if (connectedNodes.isEmpty()) {
                    _uiState.update { it.copy(watchPaired = false, wearAppInstalled = true) }
                    return@launch
                }
                val capabilityClient = Wearable.getCapabilityClient(app)
                capabilityClient.addListener(capabilityListener, "glucose_sync_wear")
                val capabilityInfo = Tasks.await(
                    capabilityClient.getCapability("glucose_sync_wear", CapabilityClient.FILTER_REACHABLE)
                )
                val hasCapability = capabilityInfo.nodes.isNotEmpty()
                _uiState.update { it.copy(watchPaired = true, wearAppInstalled = hasCapability) }

                Wearable.getMessageClient(app).addListener { messageEvent ->
                    if (messageEvent.path == "/request_glucose") {
                        refreshGlucose()
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(watchPaired = false, wearAppInstalled = true) }
            }
        }
    }

    fun openWatchPlayStore() {
        viewModelScope.launch {
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.nimbleflux.glucosesync")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
            } catch (_: Exception) { }
        }
    }
}
