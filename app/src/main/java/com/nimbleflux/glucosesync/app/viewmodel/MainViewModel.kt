package com.nimbleflux.glucosesync.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nimbleflux.glucosesync.app.BuildConfig
import com.nimbleflux.glucosesync.app.data.SettingsStore
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.domain.DemoData
import com.nimbleflux.glucosesync.shared.domain.AlertEntry
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.AuthType
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderCredentials
import com.nimbleflux.glucosesync.shared.provider.ProviderRegistry
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import com.nimbleflux.glucosesync.shared.provider.medtrum.MedtrumProvider
import com.nimbleflux.glucosesync.app.ui.PatientInfo
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.PutDataMapRequest
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
    val glucose: Double? = null,
    val glucoseUnit: String = "mmol/L",
    val trend: String = "",
    val lastUpdate: Long? = null,
    val error: String? = null,
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
    val restoringSession: Boolean = true
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
                    deltaMinutes = deltaMinutes
                )
            }

            val selectedProvider = credentialStore.getSelectedProvider()
            if (selectedProvider != null) {
                _uiState.update { it.copy(selectedProviderId = selectedProvider) }
                val p = ProviderRegistry.create(selectedProvider, application, BuildConfig.DEBUG)
                provider = p
                val restored = p.restoreSession()
                if (restored) {
                    val displayName = credentialStore.getSessionDisplayName() ?: ""
                    _uiState.update { it.copy(isLoggedIn = true, realname = displayName) }

                    if (p is MedtrumProvider && p.isCarer()) {
                        val savedPatientName = credentialStore.getMedtrumPatientName()
                        if (savedPatientName != null) {
                            _uiState.update { it.copy(realname = savedPatientName) }
                        }
                    } else if (p is LibreLinkUpProvider) {
                        val savedPatientId = credentialStore.getLibrePatientId()
                        if (savedPatientId != null) {
                            p.selectPatient(savedPatientId)
                        }
                    }

                    refreshGlucose()
                } else {
                    _uiState.update { it.copy(selectedProviderId = null) }
                }
            }
            _uiState.update { it.copy(restoringSession = false) }
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
            try {
                val session = p.login(creds).getOrThrow()
                credentialStore.saveSelectedProvider(p.id)
                credentialStore.saveSessionDisplayName(session.displayName)

                if (p is LibreLinkUpProvider) {
                    val connections = p.getConnections()
                    if (connections.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, error = "No patients found") }
                        return@launch
                    }
                    if (connections.size == 1) {
                        p.selectPatient(connections[0].patientId)
                        credentialStore.saveLibrePatient(connections[0].patientId)
                        _uiState.update {
                            it.copy(isLoggedIn = true, isLoading = false, realname = session.displayName)
                        }
                        refreshGlucose()
                    } else {
                        val unit = _uiState.value.glucoseUnit
                        val patientInfos = connections.map { conn ->
                            val glucoseMmol = conn.glucoseMeasurement?.Value
                            PatientInfo(
                                patientId = conn.patientId,
                                firstName = conn.firstName,
                                lastName = conn.lastName,
                                sensorActive = conn.sensorActive,
                                lastGlucose = if (unit == "mg/dL") {
                                    glucoseMmol?.let { g -> "%.0f".format(g * 18) } ?: ""
                                } else {
                                    glucoseMmol?.let { g -> "%.1f".format(g) } ?: ""
                                },
                                displayUnit = unit
                            )
                        }
                        _uiState.update {
                            it.copy(isLoading = false, showPatientPicker = true, patients = patientInfos)
                        }
                    }
                } else if (p is MedtrumProvider && p.isCarer()) {
                    val connections = p.getConnections()
                    if (connections.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, error = "No monitored patients found") }
                        return@launch
                    }
                    if (connections.size == 1) {
                        val conn = connections[0]
                        p.selectPatient(conn.uid, conn.displayName)
                        _uiState.update {
                            it.copy(isLoggedIn = true, isLoading = false, realname = conn.displayName)
                        }
                        refreshGlucose()
                    } else {
                        val unit = _uiState.value.glucoseUnit
                        val patientInfos = connections.map { conn ->
                            val glucoseMmol = conn.sensor_status.glucose
                            PatientInfo(
                                patientId = conn.uid.toString(),
                                firstName = conn.displayName,
                                lastName = "",
                                sensorActive = glucoseMmol != null && glucoseMmol > 0,
                                lastGlucose = if (unit == "mg/dL") {
                                    glucoseMmol?.let { g -> "%.0f".format(g * 18) } ?: ""
                                } else {
                                    glucoseMmol?.let { g -> "%.1f".format(g) } ?: ""
                                },
                                displayUnit = unit
                            )
                        }
                        _uiState.update {
                            it.copy(isLoading = false, showPatientPicker = true, patients = patientInfos)
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoggedIn = true, isLoading = false, realname = session.displayName)
                    }
                    refreshGlucose()
                }
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("403") == true -> "Invalid credentials"
                    e.message?.contains("Unable to resolve") == true -> "No internet connection"
                    else -> e.message ?: "Something went wrong"
                }
                _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun selectPatient(patientId: String) {
        viewModelScope.launch {
            val patientName = _uiState.value.patients.find { it.patientId == patientId }?.firstName
            when (val p = provider) {
                is LibreLinkUpProvider -> {
                    p.selectPatient(patientId)
                    credentialStore.saveLibrePatient(patientId)
                    if (patientName != null) {
                        credentialStore.saveSessionDisplayName(patientName)
                    }
                }
                is MedtrumProvider -> {
                    p.selectPatient(patientId.toLongOrNull() ?: return@launch, patientName)
                }
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
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                isLoading = false,
                realname = DemoData.demoDisplayName,
                isDemo = true,
                sensorActive = true,
                history = demoHistory
            )
        }
        refreshDemoGlucose()
        startDemoPolling()
    }

    private fun refreshDemoGlucose() {
        val currentHistory = _uiState.value.history.toMutableList()
        val snapshot = DemoData.snapshot(currentHistory)
        currentHistory.add(GlucoseHistoryPoint(snapshot.timestamp, snapshot.glucose ?: 5.6))
        val trimmed = currentHistory.trimTo24h()

        val unit = _uiState.value.glucoseUnit
        _uiState.update {
            it.copy(
                glucose = snapshot.glucose,
                glucoseUnit = unit,
                trend = snapshot.trend.symbol,
                lastUpdate = snapshot.timestamp,
                sensorActive = true,
                error = null,
                history = trimmed
            )
        }
        snapshot.glucose?.let { _ ->
            syncToWatch(snapshot)
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(60_000)
                if (_uiState.value.isLoggedIn && !_uiState.value.isDemo) {
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
                _uiState.update { it.copy(isLoading = true, error = null) }
                p.fetchGlucose()
                    .onSuccess { snapshot ->
                        val history = if (snapshot.history.isNotEmpty()) {
                            snapshot.history.trimTo24h()
                        } else {
                            val currentHistory = _uiState.value.history.toMutableList()
                            val g = snapshot.glucose
                            if (g != null && snapshot.sensorActive) {
                                currentHistory.add(GlucoseHistoryPoint(snapshot.timestamp, g))
                            }
                            currentHistory.trimTo24h()
                        }

                        val trend = if (snapshot.trend == TrendArrow.UNKNOWN) {
                            computeTrend(snapshot.glucose, history) ?: snapshot.trend
                        } else {
                            snapshot.trend
                        }

                        _uiState.update {
                            val deltaMin = it.deltaMinutes
                            val computedDelta = computeDelta(history, deltaMin) ?: snapshot.delta
                            it.copy(
                                isLoading = false,
                                glucose = snapshot.glucose,
                                lastUpdate = snapshot.timestamp,
                                sensorActive = snapshot.sensorActive,
                                trend = trend.symbol,
                                error = if (!snapshot.sensorActive) "No active sensor" else null,
                                history = history,
                                iob = snapshot.iob,
                                delta = computedDelta,
                                batteryPercent = snapshot.batteryPercent,
                                basalRate = snapshot.basalRate,
                                lastBolus = snapshot.lastBolus,
                                lastBolusTime = snapshot.lastBolusTime,
                                remainingDose = snapshot.remainingDose,
                                alerts = snapshot.alerts
                            )
                        }
                        snapshot.glucose?.let { _ ->
                            syncToWatch(snapshot)
                        }
                    }
                    .onFailure { e ->
                        val msg = when {
                            e.message?.contains("403") == true -> "Authentication expired. Try signing in again."
                            e.message?.contains("Unable to resolve") == true -> "No internet connection"
                            else -> e.message ?: "Could not fetch data"
                        }
                        _uiState.update { it.copy(isLoading = false, error = msg) }
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

    private fun computeDelta(history: List<GlucoseHistoryPoint>, deltaMinutes: Int): Double? {
        if (history.size < 2) return null
        val latest = history.last()
        val targetTime = latest.timestamp - deltaMinutes * 60L
        val closest = history.filter { it.timestamp <= targetTime }
            .minByOrNull { Math.abs(it.timestamp - targetTime) }
            ?: return null
        return latest.glucoseMmol - closest.glucoseMmol
    }

    private fun computeTrend(currentGlucose: Double?, history: List<GlucoseHistoryPoint>): TrendArrow? {
        if (currentGlucose == null || history.size < 2) return null
        val now = System.currentTimeMillis() / 1000
        val windowStart = now - 900
        val recent = history.filter { it.timestamp >= windowStart }
        if (recent.size < 2) return null
        val oldest = recent.first()
        val newest = recent.last()
        val timeDeltaMinutes = (newest.timestamp - oldest.timestamp) / 60.0
        if (timeDeltaMinutes < 1) return null
        val delta = newest.glucoseMmol - oldest.glucoseMmol
        val ratePerMinute = delta / timeDeltaMinutes
        return TrendArrow.fromRate(ratePerMinute)
    }

    private fun List<GlucoseHistoryPoint>.trimTo24h(): List<GlucoseHistoryPoint> {
        val cutoff = System.currentTimeMillis() / 1000 - 86400
        return this.filter { it.timestamp >= cutoff }
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
    }

    fun logout() {
        demoPollingJob?.cancel()
        autoRefreshJob?.cancel()
        provider?.logout()
        viewModelScope.launch {
            credentialStore.clear()
            _uiState.update { MainUiState(glucoseUnit = settingsStore.getUnit()) }
        }
    }

    private fun syncToWatch(snapshot: GlucoseSnapshot) {
        try {
            val glucose = snapshot.glucose ?: return
            val dataClient = Wearable.getDataClient(getApplication<Application>())
            val request = PutDataMapRequest.create("/glucose").apply {
                dataMap.putDouble("glucose", glucose)
                dataMap.putLong("timestamp", snapshot.timestamp)
                dataMap.putString("trend", snapshot.trend.symbol)
                dataMap.putString("unit", snapshot.unit)
                snapshot.iob?.let { dataMap.putDouble("iob", it) }
                snapshot.delta?.let { dataMap.putDouble("delta", it) }
                snapshot.batteryPercent?.let { dataMap.putDouble("batteryPercent", it) }
                snapshot.basalRate?.let { dataMap.putDouble("basalRate", it) }
                snapshot.lastBolus?.let { dataMap.putDouble("lastBolus", it) }
                snapshot.lastBolusTime?.let { dataMap.putLong("lastBolusTime", it) }
                snapshot.remainingDose?.let { dataMap.putDouble("remainingDose", it) }
                snapshot.highThreshold?.let { dataMap.putDouble("highThreshold", it) }
                snapshot.lowThreshold?.let { dataMap.putDouble("lowThreshold", it) }
                snapshot.timeInRange?.let { dataMap.putDouble("timeInRange", it) }
                snapshot.averageGlucose?.let { dataMap.putDouble("averageGlucose", it) }
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent())
            _uiState.update { it.copy(wearAppInstalled = true) }
        } catch (_: Exception) { }
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
