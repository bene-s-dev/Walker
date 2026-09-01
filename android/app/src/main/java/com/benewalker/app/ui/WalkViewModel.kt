package com.benewalker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benewalker.app.data.WalkDatabase
import com.benewalker.app.data.WalkRecord
import com.benewalker.app.health.HealthConnectManager
import com.benewalker.app.service.GpsPoint
import com.benewalker.app.service.KilometerSplit
import com.benewalker.app.service.LocationTracker
import com.benewalker.app.service.StopwatchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    // Stopwatch & Training (Synced from StopwatchManager)
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsedSec: Int = 0,
    val stopwatchTarget: String = "morning", // "morning" or "evening"
    val stopwatchSoundEnabled: Boolean = true,
    val stopwatchVoiceIntervalMin: Int = 1, // 1 or 5
    val stopwatchBeep30s: Boolean = true,

    // GPS & Route Tracking (Synced from LocationTracker)
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
    val stopwatchManager = StopwatchManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        WalkUiState(
            themeMode = prefs.getString("theme_mode", "system") ?: "system",
            useDynamicColor = prefs.getBoolean("use_dynamic_color", true),
            stopwatchSoundEnabled = stopwatchManager.soundEnabled.value,
            stopwatchVoiceIntervalMin = stopwatchManager.voiceIntervalMin.value,
            stopwatchBeep30s = stopwatchManager.beep30s.value,
            stopwatchTarget = stopwatchManager.target.value,
            stopwatchRunning = stopwatchManager.isRunning.value,
            stopwatchElapsedSec = stopwatchManager.elapsedSeconds.value
        )
    )
    val uiState: StateFlow<WalkUiState> = _uiState.asStateFlow()

    init {
        // 1. Sync StopwatchManager state into UI state
        viewModelScope.launch {
            stopwatchManager.isRunning.collect { running ->
                _uiState.update { it.copy(stopwatchRunning = running) }
            }
        }
        viewModelScope.launch {
            stopwatchManager.elapsedSeconds.collect { sec ->
                _uiState.update { it.copy(stopwatchElapsedSec = sec) }
            }
        }
        viewModelScope.launch {
            stopwatchManager.target.collect { target ->
                _uiState.update { it.copy(stopwatchTarget = target) }
            }
        }
        viewModelScope.launch {
            stopwatchManager.soundEnabled.collect { enabled ->
                _uiState.update { it.copy(stopwatchSoundEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            stopwatchManager.beep30s.collect { beep ->
                _uiState.update { it.copy(stopwatchBeep30s = beep) }
            }
        }
        viewModelScope.launch {
            stopwatchManager.voiceIntervalMin.collect { interval ->
                _uiState.update { it.copy(stopwatchVoiceIntervalMin = interval) }
            }
        }

        // 2. Collect GPS Location updates from LocationTracker
        viewModelScope.launch {
            locationTracker.trackingState.collect { trackState ->
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

        // 3. Collect DB updates
        viewModelScope.launch {
            walkDao.getAllRecordsFlow().collect { list ->
                updateMetrics(list)
            }
        }

        // 4. Init Health Connect status & trigger auto-sync (last 90 days)
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
        if ((todayRec?.morningSeconds ?: 0) > 0 && !stopwatchManager.isRunning.value && stopwatchManager.target.value == "morning") {
            stopwatchManager.setTarget("evening")
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
                totalRecordedDays = records.size
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
                        morningMin = mMin,
                        morningSec = mSec,
                        morningDistanceKm = mDist,
                        eveningMin = eMin,
                        eveningSec = eSec,
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
            val morningDistMeters = if (mDistKm > 0.0) mDistKm * 1000.0 else (if (state.morningDistanceKm.isBlank()) (existing?.morningDistanceMeters ?: 0.0) else 0.0)
            val eveningDistMeters = if (eDistKm > 0.0) eDistKm * 1000.0 else (if (state.eveningDistanceKm.isBlank()) (existing?.eveningDistanceMeters ?: 0.0) else 0.0)

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
        if (stopwatchManager.isRunning.value) {
            pauseStopwatch()
        } else {
            startStopwatch()
        }
    }

    fun setStopwatchSoundEnabled(enabled: Boolean) {
        stopwatchManager.setSoundEnabled(enabled)
    }

    fun setStopwatchVoiceInterval(intervalMin: Int) {
        stopwatchManager.setVoiceIntervalMin(intervalMin)
    }

    fun setStopwatchBeep30s(enabled: Boolean) {
        stopwatchManager.setBeep30s(enabled)
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            locationTracker.startListening()
            if (stopwatchManager.isRunning.value) {
                locationTracker.startTracking()
            }
        }
    }

    fun startStopwatch() {
        stopwatchManager.start()
    }

    fun pauseStopwatch() {
        stopwatchManager.pause()
    }

    fun resetStopwatch() {
        stopwatchManager.reset()
    }

    fun setStopwatchTarget(target: String) {
        stopwatchManager.setTarget(target)
    }

    fun saveStopwatchToToday(targetChoice: String? = null) {
        val elapsed = stopwatchManager.getExactCurrentElapsedSeconds()
        if (elapsed <= 0) return

        val target = targetChoice ?: stopwatchManager.target.value
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
