package com.benewalker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benewalker.app.data.WalkDatabase
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

import com.benewalker.app.service.LocationTracker
import com.benewalker.app.service.GpsPoint
import com.benewalker.app.service.KilometerSplit

enum class HcStatus {
    IDLE,
    SYNCING,
    READY,
    PERMISSION_NEEDED,
    UNAVAILABLE,
    ERROR
}

data class WalkUiState(
    val records: List<WalkRecord> = emptyList(),
    val todayRecord: WalkRecord? = null,
    val todayVsYesterdayDiffSec: Int = 0,
    val avg7DaysSec: Int = 0,
    val diff7DaysSec: Int = 0,
    val avg30DaysSec: Int = 0,
    val diff30DaysSec: Int = 0,
    val allTimeSingleRecordSec: Int = 0,
    val todayMaxSingleSec: Int = 0,
    val totalRecordedDays: Int = 0,
    
    // Health Connect
    val hcStatus: HcStatus = HcStatus.IDLE,
    val lastSyncTime: Long? = null,
    val lastSyncSessionsCount: Int = 0,
    val syncErrorMessage: String? = null,

    // Form
    val formDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    val morningMin: String = "",
    val morningSec: String = "",
    val morningDistanceKm: String = "",
    val eveningMin: String = "",
    val eveningSec: String = "",
    val eveningDistanceKm: String = "",
    val formSuccessFeedback: Boolean = false,

    // Stopwatch & Training
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsedSec: Int = 0,
    val stopwatchTarget: String = "morning", // "morning" or "evening"
    val stopwatchSoundEnabled: Boolean = true,
    val stopwatchVoiceIntervalMin: Int = 1, // 1 or 5
    val stopwatchBeep30s: Boolean = true,

    // GPS & Route Tracking
    val gpsDistanceMeters: Double = 0.0,
    val gpsCurrentSpeedKmh: Double = 0.0,
    val gpsAvgSpeedKmh: Double = 0.0,
    val gpsCurrentPaceMinPerKm: Double = 0.0,
    val gpsAvgPaceMinPerKm: Double = 0.0,
    val gpsRoutePoints: List<GpsPoint> = emptyList(),
    val gpsCurrentLat: Double? = null,
    val gpsCurrentLon: Double? = null,
    val gpsAccuracy: Float = 0f,
    val kilometerSplits: List<KilometerSplit> = emptyList(),
    val hasLocationPermission: Boolean = true,

    // Theme & Appearance
    val themeMode: String = "system", // "system", "light", "dark"
    val useDynamicColor: Boolean = true
)

class WalkViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("benewalker_prefs", android.content.Context.MODE_PRIVATE)
    private val database = WalkDatabase.getInstance(application)
    private val walkDao = database.walkDao()
    val healthConnectManager = HealthConnectManager(application, walkDao)
    val locationTracker = LocationTracker.getInstance(application)

    private var toneGenerator: ToneGenerator? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _uiState = MutableStateFlow(
        WalkUiState(
            themeMode = prefs.getString("theme_mode", "system") ?: "system",
            useDynamicColor = prefs.getBoolean("use_dynamic_color", true),
            stopwatchSoundEnabled = prefs.getBoolean("stopwatch_sound_enabled", true),
            stopwatchVoiceIntervalMin = prefs.getInt("stopwatch_voice_interval_min", 1),
            stopwatchBeep30s = prefs.getBoolean("stopwatch_beep_30s", true)
        )
    )
    val uiState: StateFlow<WalkUiState> = _uiState.asStateFlow()

    private var stopwatchJob: Job? = null
    private var stopwatchStartTimestamp: Long = 0L
    private var stopwatchBaseElapsedSec: Int = 0
    private var lastBeepSecond: Int = -1
    private var lastSpokenMinute: Int = -1

    init {
        // Init Audio / TTS (Volume 100 for clear audible feedback)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: Exception) {}

        try {
            textToSpeech = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.GERMAN
                    isTtsReady = true
                }
            }
        } catch (_: Exception) {}

        // Collect GPS Location updates
        viewModelScope.launch {
            locationTracker.trackingState.collect { trackState ->
                val prevSplits = _uiState.value.kilometerSplits
                if (trackState.splits.size > prevSplits.size && _uiState.value.stopwatchSoundEnabled) {
                    val latestSplit = trackState.splits.last()
                    val text = "Kilometer ${latestSplit.kmNumber} in ${latestSplit.paceString} Minuten"
                    try {
                        if (isTtsReady) {
                            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "km_${latestSplit.kmNumber}")
                        }
                    } catch (_: Exception) {}
                }

                _uiState.update {
                    it.copy(
                        gpsDistanceMeters = trackState.totalDistanceMeters,
                        gpsCurrentSpeedKmh = trackState.currentSpeedKmh,
                        gpsAvgSpeedKmh = trackState.avgSpeedKmh,
                        gpsCurrentPaceMinPerKm = trackState.currentPaceMinPerKm,
                        gpsAvgPaceMinPerKm = trackState.avgPaceMinPerKm,
                        gpsRoutePoints = trackState.routePoints,
                        gpsCurrentLat = trackState.currentLatitude,
                        gpsCurrentLon = trackState.currentLongitude,
                        gpsAccuracy = trackState.accuracy,
                        kilometerSplits = trackState.splits
                    )
                }
            }
        }

        // Collect DB updates
        viewModelScope.launch {
            walkDao.getAllRecordsFlow().collect { list ->
                updateMetrics(list)
            }
        }

        // Init Health Connect status & trigger auto-sync (last 90 days)
        checkHealthConnectStatus()
        viewModelScope.launch {
            delay(500)
            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                syncWithHealthConnect(days = 90)
            }
        }
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("use_dynamic_color", enabled).apply()
        _uiState.update { it.copy(useDynamicColor = enabled) }
    }

    private fun updateMetrics(records: List<WalkRecord>) {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val todayRec = records.find { it.date == todayStr }
        val yesterdayRec = records.find { it.date == yesterdayStr }

        val todayTotal = todayRec?.totalSeconds ?: 0
        val yesterdayTotal = yesterdayRec?.totalSeconds ?: 0
        val diff = todayTotal - yesterdayTotal

        // 7-day average & comparison to previous 7 days (days 8-14)
        val last7 = records.take(7)
        val avg7 = if (last7.isNotEmpty()) last7.map { it.totalSeconds }.average().toInt() else 0
        val prev7 = records.drop(7).take(7)
        val prevAvg7 = if (prev7.isNotEmpty()) prev7.map { it.totalSeconds }.average().toInt() else avg7
        val diff7 = if (prev7.isNotEmpty()) avg7 - prevAvg7 else 0

        // 30-day average & comparison to previous 30 days (days 31-60)
        val last30 = records.take(30)
        val avg30 = if (last30.isNotEmpty()) last30.map { it.totalSeconds }.average().toInt() else 0
        val prev30 = records.drop(30).take(30)
        val prevAvg30 = if (prev30.isNotEmpty()) prev30.map { it.totalSeconds }.average().toInt() else avg30
        val diff30 = if (prev30.isNotEmpty()) avg30 - prevAvg30 else 0

        // Single best session (all-time & today)
        val maxSingle = records.flatMap { listOf(it.morningSeconds, it.eveningSeconds) }.maxOrNull() ?: 0
        val todayMax = listOfNotNull(todayRec?.morningSeconds, todayRec?.eveningSeconds).maxOrNull() ?: 0

        // Auto select 2. Gehen (evening) if 1. Gehen (morning) is already recorded today and stopwatch not currently running
        val autoTarget = if ((todayRec?.morningSeconds ?: 0) > 0 && !_uiState.value.stopwatchRunning && _uiState.value.stopwatchTarget == "morning") {
            "evening"
        } else {
            _uiState.value.stopwatchTarget
        }

        _uiState.update { current ->
            current.copy(
                records = records,
                todayRecord = todayRec,
                todayVsYesterdayDiffSec = diff,
                avg7DaysSec = avg7,
                diff7DaysSec = diff7,
                avg30DaysSec = avg30,
                diff30DaysSec = diff30,
                allTimeSingleRecordSec = maxSingle,
                todayMaxSingleSec = todayMax,
                totalRecordedDays = records.size,
                stopwatchTarget = autoTarget
            )
        }

        try {
            com.benewalker.app.widget.BeneWalkerChartWidget.updateAllWidgets(getApplication())
        } catch (_: Exception) {}
    }

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            if (!healthConnectManager.isAvailable()) {
                _uiState.update { it.copy(hcStatus = HcStatus.UNAVAILABLE) }
                return@launch
            }
            val hasPerm = healthConnectManager.hasPermissions()
            _uiState.update {
                it.copy(hcStatus = if (hasPerm) HcStatus.READY else HcStatus.PERMISSION_NEEDED)
            }
        }
    }

    fun syncWithHealthConnect(days: Int = 14, onComplete: ((updatedCount: Int) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(hcStatus = HcStatus.SYNCING) }
            val result = healthConnectManager.syncWalkingSessions(days)
            if (result.errorMessage != null) {
                _uiState.update {
                    it.copy(
                        hcStatus = HcStatus.ERROR,
                        syncErrorMessage = result.errorMessage
                    )
                }
                onComplete?.invoke(0)
            } else {
                _uiState.update {
                    it.copy(
                        hcStatus = HcStatus.READY,
                        lastSyncTime = result.timestamp,
                        lastSyncSessionsCount = result.totalSessions,
                        syncErrorMessage = null
                    )
                }
                onComplete?.invoke(result.updatedCount)
            }
        }
    }

    // Form Operations
    fun setFormDate(date: String) {
        _uiState.update { it.copy(formDate = date) }
        loadRecordIntoForm(date)
    }

    fun loadRecordIntoForm(date: String) {
        viewModelScope.launch {
            val record = walkDao.getRecordByDate(date)
            if (record != null) {
                val mMin = if (record.morningSeconds > 0) (record.morningSeconds / 60).toString() else ""
                val mSec = if (record.morningSeconds > 0) (record.morningSeconds % 60).toString() else ""
                val eMin = if (record.eveningSeconds > 0) (record.eveningSeconds / 60).toString() else ""
                val eSec = if (record.eveningSeconds > 0) (record.eveningSeconds % 60).toString() else ""
                val mDist = if (record.morningDistanceMeters > 0) String.format(Locale.GERMAN, "%.2f", record.morningDistanceMeters / 1000.0) else ""
                val eDist = if (record.eveningDistanceMeters > 0) String.format(Locale.GERMAN, "%.2f", record.eveningDistanceMeters / 1000.0) else ""
                _uiState.update {
                    it.copy(
                        formDate = record.date,
                        morningMin = if (record.morningSeconds > 0) (record.morningSeconds / 60).toString() else "",
                        morningSec = if (record.morningSeconds > 0) (record.morningSeconds % 60).toString() else "",
                        morningDistanceKm = mDist,
                        eveningMin = if (record.eveningSeconds > 0) (record.eveningSeconds / 60).toString() else "",
                        eveningSec = if (record.eveningSeconds > 0) (record.eveningSeconds % 60).toString() else "",
                        eveningDistanceKm = eDist
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        formDate = date,
                        morningMin = "",
                        morningSec = "",
                        morningDistanceKm = "",
                        eveningMin = "",
                        eveningSec = "",
                        eveningDistanceKm = ""
                    )
                }
            }
        }
    }

    fun updateFormFields(
        morningMin: String? = null,
        morningSec: String? = null,
        morningDistanceKm: String? = null,
        eveningMin: String? = null,
        eveningSec: String? = null,
        eveningDistanceKm: String? = null
    ) {
        _uiState.update { current ->
            current.copy(
                morningMin = morningMin ?: current.morningMin,
                morningSec = morningSec ?: current.morningSec,
                morningDistanceKm = morningDistanceKm ?: current.morningDistanceKm,
                eveningMin = eveningMin ?: current.eveningMin,
                eveningSec = eveningSec ?: current.eveningSec,
                eveningDistanceKm = eveningDistanceKm ?: current.eveningDistanceKm
            )
        }
    }

    fun addQuickSeconds(target: String, secondsToAdd: Int) {
        _uiState.update { current ->
            if (target == "morning") {
                val currTotal = (current.morningMin.toIntOrNull() ?: 0) * 60 + (current.morningSec.toIntOrNull() ?: 0)
                val newTotal = (currTotal + secondsToAdd).coerceAtLeast(0)
                current.copy(
                    morningMin = (newTotal / 60).toString(),
                    morningSec = (newTotal % 60).toString()
                )
            } else {
                val currTotal = (current.eveningMin.toIntOrNull() ?: 0) * 60 + (current.eveningSec.toIntOrNull() ?: 0)
                val newTotal = (currTotal + secondsToAdd).coerceAtLeast(0)
                current.copy(
                    eveningMin = (newTotal / 60).toString(),
                    eveningSec = (newTotal % 60).toString()
                )
            }
        }
    }

    fun clearFormField(target: String) {
        _uiState.update {
            if (target == "morning") it.copy(morningMin = "", morningSec = "", morningDistanceKm = "")
            else it.copy(eveningMin = "", eveningSec = "", eveningDistanceKm = "")
        }
    }

    fun saveForm() {
        val state = _uiState.value
        val mMin = state.morningMin.toIntOrNull() ?: 0
        val mSec = state.morningSec.toIntOrNull() ?: 0
        val eMin = state.eveningMin.toIntOrNull() ?: 0
        val eSec = state.eveningSec.toIntOrNull() ?: 0
        val mDistKm = state.morningDistanceKm.replace(',', '.').toDoubleOrNull() ?: 0.0
        val eDistKm = state.eveningDistanceKm.replace(',', '.').toDoubleOrNull() ?: 0.0

        val morningSeconds = mMin * 60 + mSec
        val eveningSeconds = eMin * 60 + eSec
        val totalSeconds = morningSeconds + eveningSeconds

        if (totalSeconds == 0 && mDistKm == 0.0 && eDistKm == 0.0) return

        viewModelScope.launch {
            val existing = walkDao.getRecordByDate(state.formDate)
            val morningDistMeters = if (mDistKm > 0.0) mDistKm * 1000.0 else (existing?.morningDistanceMeters ?: 0.0)
            val eveningDistMeters = if (eDistKm > 0.0) eDistKm * 1000.0 else (existing?.eveningDistanceMeters ?: 0.0)

            val record = WalkRecord(
                date = state.formDate,
                morningSeconds = morningSeconds,
                eveningSeconds = eveningSeconds,
                totalSeconds = totalSeconds,
                updatedAt = System.currentTimeMillis(),
                source = existing?.source ?: "manual",
                morningDistanceMeters = morningDistMeters,
                eveningDistanceMeters = eveningDistMeters,
                morningRouteJson = existing?.morningRouteJson,
                eveningRouteJson = existing?.eveningRouteJson
            )
            walkDao.insertOrUpdate(record)

            _uiState.update { it.copy(formSuccessFeedback = true) }
            delay(1200)
            _uiState.update { it.copy(formSuccessFeedback = false) }
        }
    }

    fun updateRecordDirectly(
        date: String,
        morningSec: Int,
        eveningSec: Int,
        morningDistMeters: Double? = null,
        eveningDistMeters: Double? = null
    ) {
        viewModelScope.launch {
            val existing = walkDao.getRecordByDate(date)
            val totalSeconds = morningSec + eveningSec
            val mDist = morningDistMeters ?: (existing?.morningDistanceMeters ?: 0.0)
            val eDist = eveningDistMeters ?: (existing?.eveningDistanceMeters ?: 0.0)
            val record = WalkRecord(
                date = date,
                morningSeconds = morningSec,
                eveningSeconds = eveningSec,
                totalSeconds = totalSeconds,
                updatedAt = System.currentTimeMillis(),
                source = existing?.source ?: "manual",
                morningDistanceMeters = mDist,
                eveningDistanceMeters = eDist,
                morningRouteJson = existing?.morningRouteJson,
                eveningRouteJson = existing?.eveningRouteJson
            )
            walkDao.insertOrUpdate(record)
        }
    }

    fun deleteRecord(date: String) {
        viewModelScope.launch {
            walkDao.deleteByDate(date)
            if (_uiState.value.formDate == date) {
                loadRecordIntoForm(date)
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            walkDao.deleteAll()
        }
    }

    // Stopwatch Controls
    fun toggleStopwatch() {
        if (_uiState.value.stopwatchRunning) {
            pauseStopwatch()
        } else {
            startStopwatch()
        }
    }

    fun setStopwatchSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("stopwatch_sound_enabled", enabled).apply()
        _uiState.update { it.copy(stopwatchSoundEnabled = enabled) }
        try {
            com.benewalker.app.service.StopwatchService.updateConfig(
                getApplication(),
                enabled,
                _uiState.value.stopwatchBeep30s,
                _uiState.value.stopwatchVoiceIntervalMin
            )
        } catch (_: Exception) {}
    }

    fun setStopwatchVoiceInterval(intervalMin: Int) {
        prefs.edit().putInt("stopwatch_voice_interval_min", intervalMin).apply()
        _uiState.update { it.copy(stopwatchVoiceIntervalMin = intervalMin) }
        try {
            com.benewalker.app.service.StopwatchService.updateConfig(
                getApplication(),
                _uiState.value.stopwatchSoundEnabled,
                _uiState.value.stopwatchBeep30s,
                intervalMin
            )
        } catch (_: Exception) {}
    }

    fun setStopwatchBeep30s(enabled: Boolean) {
        prefs.edit().putBoolean("stopwatch_beep_30s", enabled).apply()
        _uiState.update { it.copy(stopwatchBeep30s = enabled) }
        try {
            com.benewalker.app.service.StopwatchService.updateConfig(
                getApplication(),
                _uiState.value.stopwatchSoundEnabled,
                enabled,
                _uiState.value.stopwatchVoiceIntervalMin
            )
        } catch (_: Exception) {}
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            locationTracker.startListening()
            if (_uiState.value.stopwatchRunning) {
                locationTracker.startTracking()
            }
        }
    }

    private fun startStopwatch() {
        _uiState.update { it.copy(stopwatchRunning = true) }
        val currentSec = _uiState.value.stopwatchElapsedSec
        val target = _uiState.value.stopwatchTarget

        stopwatchStartTimestamp = SystemClock.elapsedRealtime()
        stopwatchBaseElapsedSec = currentSec

        // Start Foreground Service with Location Tracking, WakeLock & Audio engine
        try {
            com.benewalker.app.service.StopwatchService.start(
                getApplication(),
                currentSec,
                target,
                _uiState.value.stopwatchSoundEnabled,
                _uiState.value.stopwatchBeep30s,
                _uiState.value.stopwatchVoiceIntervalMin
            )
        } catch (_: Exception) {}

        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (_uiState.value.stopwatchRunning) {
                delay(500)
                val elapsedSinceStart = ((SystemClock.elapsedRealtime() - stopwatchStartTimestamp) / 1000).toInt()
                val nextSec = stopwatchBaseElapsedSec + elapsedSinceStart

                if (nextSec != _uiState.value.stopwatchElapsedSec) {
                    _uiState.update { it.copy(stopwatchElapsedSec = nextSec) }
                    locationTracker.updateElapsedSeconds(nextSec)
                }
            }
        }
    }

    private fun pauseStopwatch() {
        if (_uiState.value.stopwatchRunning) {
            val elapsedSinceStart = ((SystemClock.elapsedRealtime() - stopwatchStartTimestamp) / 1000).toInt()
            val finalSec = stopwatchBaseElapsedSec + elapsedSinceStart
            _uiState.update { it.copy(stopwatchRunning = false, stopwatchElapsedSec = finalSec) }
            locationTracker.updateElapsedSeconds(finalSec)
        } else {
            _uiState.update { it.copy(stopwatchRunning = false) }
        }
        locationTracker.pauseTracking()
        stopwatchJob?.cancel()
        try {
            com.benewalker.app.service.StopwatchService.pause(
                getApplication(),
                _uiState.value.stopwatchElapsedSec,
                _uiState.value.stopwatchTarget
            )
        } catch (_: Exception) {}
    }

    fun resetStopwatch() {
        pauseStopwatch()
        locationTracker.reset()
        _uiState.update { it.copy(stopwatchElapsedSec = 0) }
        stopwatchBaseElapsedSec = 0
        lastBeepSecond = -1
        lastSpokenMinute = -1
        try {
            com.benewalker.app.service.StopwatchService.stop(getApplication())
        } catch (_: Exception) {}
    }

    fun setStopwatchTarget(target: String) {
        _uiState.update { it.copy(stopwatchTarget = target) }
        if (_uiState.value.stopwatchRunning) {
            try {
                com.benewalker.app.service.StopwatchService.start(
                    getApplication(),
                    _uiState.value.stopwatchElapsedSec,
                    target
                )
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            locationTracker.pauseTracking()
            com.benewalker.app.service.StopwatchService.stop(getApplication())
            toneGenerator?.release()
            toneGenerator = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }

    fun saveStopwatchToToday(targetChoice: String? = null) {
        val elapsed = _uiState.value.stopwatchElapsedSec
        if (elapsed <= 0) return

        val target = targetChoice ?: _uiState.value.stopwatchTarget
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val currentPoints = _uiState.value.gpsRoutePoints
        val currentDist = _uiState.value.gpsDistanceMeters
        val routeJsonString = if (currentPoints.isNotEmpty()) jsonConfig.encodeToString(currentPoints) else null

        viewModelScope.launch {
            val existing = walkDao.getRecordByDate(todayStr)
            val morningSec = if (target == "morning") elapsed else (existing?.morningSeconds ?: 0)
            val eveningSec = if (target == "evening") elapsed else (existing?.eveningSeconds ?: 0)
            val morningDist = if (target == "morning") (if (currentDist > 0) currentDist else (existing?.morningDistanceMeters ?: 0.0)) else (existing?.morningDistanceMeters ?: 0.0)
            val eveningDist = if (target == "evening") (if (currentDist > 0) currentDist else (existing?.eveningDistanceMeters ?: 0.0)) else (existing?.eveningDistanceMeters ?: 0.0)
            val morningRoute = if (target == "morning") (routeJsonString ?: existing?.morningRouteJson) else existing?.morningRouteJson
            val eveningRoute = if (target == "evening") (routeJsonString ?: existing?.eveningRouteJson) else existing?.eveningRouteJson

            val record = WalkRecord(
                date = todayStr,
                morningSeconds = morningSec,
                eveningSeconds = eveningSec,
                totalSeconds = morningSec + eveningSec,
                updatedAt = System.currentTimeMillis(),
                source = "stopwatch",
                morningDistanceMeters = morningDist,
                eveningDistanceMeters = eveningDist,
                morningRouteJson = morningRoute,
                eveningRouteJson = eveningRoute
            )
            walkDao.insertOrUpdate(record)
            resetStopwatch()

            // If 1. Gehen was saved, automatically set target to 2. Gehen for the next session
            if (target == "morning") {
                setStopwatchTarget("evening")
            }
        }
    }

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        prettyPrint = true
    }

    // JSON Export / Import
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val all = walkDao.getAllRecords()
        jsonConfig.encodeToString(all)
    }

    private fun parseBackupJson(rawJson: String): List<WalkRecord> {
        val clean = rawJson.trim().removePrefix("\uFEFF")
        val result = mutableListOf<WalkRecord>()

        fun parseSeconds(obj: org.json.JSONObject, vararg keys: String): Int {
            for (k in keys) {
                if (obj.has(k) && !obj.isNull(k)) {
                    val v = obj.get(k)
                    val s = when (v) {
                        is Number -> v.toInt()
                        is String -> v.toDoubleOrNull()?.toInt() ?: 0
                        else -> 0
                    }
                    if (s > 0) return s
                }
            }
            return 0
        }

        fun parseDate(obj: org.json.JSONObject): String? {
            val d = obj.optString("date", "").ifBlank {
                obj.optString("id", "").ifBlank {
                    obj.optString("day", "")
                }
            }
            if (d.isBlank()) return null
            if (d.contains(".") && d.split(".").size == 3) {
                val p = d.split(".")
                return String.format("%04d-%02d-%02d", p[2].toIntOrNull() ?: 2026, p[1].toIntOrNull() ?: 1, p[0].toIntOrNull() ?: 1)
            }
            return d
        }

        try {
            if (clean.startsWith("[")) {
                val array = org.json.JSONArray(clean)
                val tempGrouped = mutableMapOf<String, MutableList<Int>>()

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val date = parseDate(item) ?: continue

                    val mSec = parseSeconds(item, "morningSeconds", "morning", "mSec")
                    val eSec = parseSeconds(item, "eveningSeconds", "evening", "eSec")
                    val dur = parseSeconds(item, "duration", "durationSeconds", "seconds")

                    if (mSec > 0 || eSec > 0 || item.has("morningSeconds") || item.has("eveningSeconds")) {
                        val tot = if (item.has("totalSeconds")) parseSeconds(item, "totalSeconds") else (mSec + eSec)
                        result.add(
                            WalkRecord(
                                date = date,
                                morningSeconds = mSec,
                                eveningSeconds = eSec,
                                totalSeconds = if (tot > 0) tot else (mSec + eSec),
                                source = item.optString("source", "backup")
                            )
                        )
                    } else if (dur > 0) {
                        tempGrouped.getOrPut(date) { mutableListOf() }.add(dur)
                    }
                }

                tempGrouped.forEach { (date, durs) ->
                    val morning = durs.getOrNull(0) ?: 0
                    val evening = durs.drop(1).sum()
                    result.add(
                        WalkRecord(
                            date = date,
                            morningSeconds = morning,
                            eveningSeconds = evening,
                            totalSeconds = morning + evening,
                            source = "backup_migrated"
                        )
                    )
                }
            } else if (clean.startsWith("{")) {
                val obj = org.json.JSONObject(clean)
                val possibleKeys = listOf("records", "beneWalker_records_v1", "data", "sessions", "items", "entries")
                var foundArray: org.json.JSONArray? = null
                for (k in possibleKeys) {
                    if (obj.has(k) && obj.optJSONArray(k) != null) {
                        foundArray = obj.getJSONArray(k)
                        break
                    }
                }
                if (foundArray != null) {
                    return parseBackupJson(foundArray.toString())
                }

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = obj.optJSONObject(key)
                    if (child != null) {
                        val date = if (key.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) key else (parseDate(child) ?: key)
                        val mSec = parseSeconds(child, "morningSeconds", "morning", "mSec")
                        val eSec = parseSeconds(child, "eveningSeconds", "evening", "eSec")
                        val tot = if (child.has("totalSeconds")) parseSeconds(child, "totalSeconds") else (mSec + eSec)
                        result.add(
                            WalkRecord(
                                date = date,
                                morningSeconds = mSec,
                                eveningSeconds = eSec,
                                totalSeconds = if (tot > 0) tot else (mSec + eSec),
                                source = child.optString("source", "backup")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    suspend fun importJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        val list = parseBackupJson(jsonString)
        if (list.isNotEmpty()) {
            walkDao.insertAll(list)
            list.size
        } else {
            0
        }
    }
}
