package com.benewalker.app.service

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StopwatchManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("benewalker_stopwatch_prefs", Context.MODE_PRIVATE)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _target = MutableStateFlow(prefs.getString("stopwatch_target", "morning") ?: "morning")
    val target: StateFlow<String> = _target.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _beep30s = MutableStateFlow(prefs.getBoolean("beep_30s", true))
    val beep30s: StateFlow<Boolean> = _beep30s.asStateFlow()

    private val _voiceIntervalMin = MutableStateFlow(prefs.getInt("voice_interval_min", 1))
    val voiceIntervalMin: StateFlow<Int> = _voiceIntervalMin.asStateFlow()

    private var startRealtime: Long = 0L
    private var baseElapsedSec: Int = 0

    companion object {
        @Volatile
        private var INSTANCE: StopwatchManager? = null

        fun getInstance(context: Context): StopwatchManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StopwatchManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getExactCurrentElapsedSeconds(): Int {
        if (!_isRunning.value) return _elapsedSeconds.value
        val elapsedSinceStart = ((SystemClock.elapsedRealtime() - startRealtime) / 1000).toInt()
        return (baseElapsedSec + elapsedSinceStart).coerceAtLeast(0)
    }

    fun start(target: String? = null) {
        val currentTarget = target ?: _target.value
        _target.value = currentTarget
        prefs.edit().putString("stopwatch_target", currentTarget).apply()

        baseElapsedSec = _elapsedSeconds.value
        startRealtime = SystemClock.elapsedRealtime()
        _isRunning.value = true

        // Start Foreground Service with accurate GPS and WakeLock
        StopwatchService.start(
            context = context,
            elapsedSeconds = baseElapsedSec,
            target = currentTarget,
            soundEnabled = _soundEnabled.value,
            beep30s = _beep30s.value,
            voiceIntervalMin = _voiceIntervalMin.value
        )
    }

    fun pause() {
        if (_isRunning.value) {
            val finalElapsed = getExactCurrentElapsedSeconds()
            _elapsedSeconds.value = finalElapsed
            baseElapsedSec = finalElapsed
            _isRunning.value = false
            LocationTracker.getInstance(context).updateElapsedSeconds(finalElapsed)
            LocationTracker.getInstance(context).pauseTracking()

            StopwatchService.pause(context, finalElapsed, _target.value)
        }
    }

    fun reset() {
        _isRunning.value = false
        baseElapsedSec = 0
        startRealtime = 0L
        _elapsedSeconds.value = 0

        LocationTracker.getInstance(context).reset()
        StopwatchService.stop(context)
    }

    fun tick(elapsed: Int) {
        if (_isRunning.value) {
            _elapsedSeconds.value = elapsed
            LocationTracker.getInstance(context).updateElapsedSeconds(elapsed)
        }
    }

    fun setTarget(target: String) {
        _target.value = target
        prefs.edit().putString("stopwatch_target", target).apply()
        if (_isRunning.value) {
            StopwatchService.start(
                context = context,
                elapsedSeconds = getExactCurrentElapsedSeconds(),
                target = target,
                soundEnabled = _soundEnabled.value,
                beep30s = _beep30s.value,
                voiceIntervalMin = _voiceIntervalMin.value
            )
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        StopwatchService.updateConfig(context, enabled, _beep30s.value, _voiceIntervalMin.value)
    }

    fun setBeep30s(enabled: Boolean) {
        _beep30s.value = enabled
        prefs.edit().putBoolean("beep_30s", enabled).apply()
        StopwatchService.updateConfig(context, _soundEnabled.value, enabled, _voiceIntervalMin.value)
    }

    fun setVoiceIntervalMin(min: Int) {
        _voiceIntervalMin.value = min
        prefs.edit().putInt("voice_interval_min", min).apply()
        StopwatchService.updateConfig(context, _soundEnabled.value, _beep30s.value, min)
    }
}
