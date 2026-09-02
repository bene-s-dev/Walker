package com.benewalker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.benewalker.app.MainActivity
import com.benewalker.app.R
import kotlinx.coroutines.*
import java.util.Locale

class StopwatchService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private var toneGenerator: ToneGenerator? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private var soundEnabled = true
    private var beep30s = true
    private var voiceIntervalMin = 1
    private var lastBeepSecond = -1
    private var lastSpokenMinute = 0

    companion object {
        const val CHANNEL_ID = "benewalker_stopwatch_live_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.benewalker.app.ACTION_START"
        const val ACTION_PAUSE = "com.benewalker.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.benewalker.app.ACTION_STOP"
        const val ACTION_UPDATE_CONFIG = "com.benewalker.app.ACTION_UPDATE_CONFIG"

        const val EXTRA_ELAPSED_SECONDS = "extra_elapsed_seconds"
        const val EXTRA_TARGET = "extra_target"
        const val EXTRA_SOUND_ENABLED = "extra_sound_enabled"
        const val EXTRA_BEEP_30S = "extra_beep_30s"
        const val EXTRA_VOICE_INTERVAL_MIN = "extra_voice_interval_min"

        fun start(
            context: Context,
            elapsedSeconds: Int,
            target: String,
            soundEnabled: Boolean = true,
            beep30s: Boolean = true,
            voiceIntervalMin: Int = 1
        ) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds)
                putExtra(EXTRA_TARGET, target)
                putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                putExtra(EXTRA_BEEP_30S, beep30s)
                putExtra(EXTRA_VOICE_INTERVAL_MIN, voiceIntervalMin)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun updateConfig(
            context: Context,
            soundEnabled: Boolean,
            beep30s: Boolean,
            voiceIntervalMin: Int
        ) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
                putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                putExtra(EXTRA_BEEP_30S, beep30s)
                putExtra(EXTRA_VOICE_INTERVAL_MIN, voiceIntervalMin)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun pause(context: Context, elapsedSeconds: Int, target: String) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds)
                putExtra(EXTRA_TARGET, target)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initAudio()

        // Attach split TTS callback to LocationTracker
        LocationTracker.getInstance(applicationContext).onSplitReached = { split ->
            if (soundEnabled) {
                val text = "Kilometer ${split.kmNumber} in ${split.paceString} Minuten"
                speakText(text)
            }
        }
    }

    private fun initAudio() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: Exception) {}

        try {
            textToSpeech = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.GERMAN
                    textToSpeech?.setPitch(0.82f)
                    textToSpeech?.setSpeechRate(0.95f)
                    isTtsReady = true
                }
            }
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BeneWalker::StopwatchWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6h max
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                acquireWakeLock()
                val elapsedSec = intent.getIntExtra(EXTRA_ELAPSED_SECONDS, 0)
                val target = intent.getStringExtra(EXTRA_TARGET) ?: "morning"
                soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true)
                beep30s = intent.getBooleanExtra(EXTRA_BEEP_30S, true)
                voiceIntervalMin = intent.getIntExtra(EXTRA_VOICE_INTERVAL_MIN, 1)
                lastBeepSecond = -1
                lastSpokenMinute = elapsedSec / 60

                // 1. Start continuous foreground GPS tracking engine
                LocationTracker.getInstance(applicationContext).startTracking()

                // 2. Play start confirmation tone
                if (soundEnabled) {
                    try {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                    } catch (_: Exception) {}
                }

                // 3. Start Foreground Notification with Chronometer
                val notification = buildLiveNotification(elapsedSec, target, isRunning = true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        startForeground(NOTIFICATION_ID, notification, serviceType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {}
                }

                // 4. Start background timer & TTS loop synced with StopwatchManager
                startServiceTimer()
            }
            ACTION_UPDATE_CONFIG -> {
                soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                beep30s = intent.getBooleanExtra(EXTRA_BEEP_30S, beep30s)
                voiceIntervalMin = intent.getIntExtra(EXTRA_VOICE_INTERVAL_MIN, voiceIntervalMin)
            }
            ACTION_PAUSE -> {
                timerJob?.cancel()
                LocationTracker.getInstance(applicationContext).pauseTracking()
                releaseWakeLock()
                val elapsedSec = intent.getIntExtra(EXTRA_ELAPSED_SECONDS, 0)
                val target = intent.getStringExtra(EXTRA_TARGET) ?: "morning"
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildLiveNotification(elapsedSec, target, isRunning = false))
            }
            ACTION_STOP -> {
                timerJob?.cancel()
                LocationTracker.getInstance(applicationContext).pauseTracking()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startServiceTimer() {
        timerJob?.cancel()
        val manager = StopwatchManager.getInstance(applicationContext)

        timerJob = serviceScope.launch {
            while (isActive) {
                delay(500)
                val currentSec = manager.getExactCurrentElapsedSeconds()
                manager.tick(currentSec)

                if (soundEnabled) {
                    // 30 Seconds Beep
                    if (beep30s && currentSec % 60 == 30 && currentSec != lastBeepSecond) {
                        lastBeepSecond = currentSec
                        try {
                            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 320)
                        } catch (_: Exception) {}
                    }

                    // Minute Voice Interval Announcement
                    val currentMin = currentSec / 60
                    if (currentMin > lastSpokenMinute && currentMin > 0) {
                        if (currentMin % voiceIntervalMin == 0) {
                            lastSpokenMinute = currentMin
                            val text = if (currentMin == 1) "Eine Minute" else "$currentMin Minuten"
                            speakText(text)
                        } else {
                            lastSpokenMinute = currentMin
                        }
                    }
                }
            }
        }
    }

    private fun speakText(text: String) {
        try {
            if (isTtsReady) {
                textToSpeech?.setPitch(0.82f)
                textToSpeech?.setSpeechRate(0.95f)
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "service_min_${System.currentTimeMillis()}")
            } else {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        LocationTracker.getInstance(applicationContext).onSplitReached = null
        LocationTracker.getInstance(applicationContext).pauseTracking()
        releaseWakeLock()
        try {
            toneGenerator?.release()
            toneGenerator = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun buildLiveNotification(elapsedSeconds: Int, target: String, isRunning: Boolean): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetLabel = if (target == "morning") "1. Gehen" else "2. Gehen"
        val baseTime = System.currentTimeMillis() - (elapsedSeconds * 1000L)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeneWalker • $targetLabel")
            .setContentText(if (isRunning) "Gehzeit läuft live..." else "Pausiert (${elapsedSeconds / 60}m ${elapsedSeconds % 60}s)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isRunning) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setWhen(baseTime)
        } else {
            builder.setUsesChronometer(false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stoppuhr Live-Anzeige (Now Bar)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zeigt die aktive Stoppuhr als Live-Pille in der Statusleiste und auf dem Sperrbildschirm an"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
