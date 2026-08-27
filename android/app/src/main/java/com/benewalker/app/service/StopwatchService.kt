package com.benewalker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.benewalker.app.MainActivity
import com.benewalker.app.R

class StopwatchService : Service() {

    companion object {
        const val CHANNEL_ID = "benewalker_stopwatch_live_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.benewalker.app.ACTION_START"
        const val ACTION_PAUSE = "com.benewalker.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.benewalker.app.ACTION_STOP"

        const val EXTRA_ELAPSED_SECONDS = "extra_elapsed_seconds"
        const val EXTRA_TARGET = "extra_target"

        fun start(context: Context, elapsedSeconds: Int, target: String) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds)
                putExtra(EXTRA_TARGET, target)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val elapsedSec = intent.getIntExtra(EXTRA_ELAPSED_SECONDS, 0)
                val target = intent.getStringExtra(EXTRA_TARGET) ?: "morning"
                val notification = buildLiveNotification(elapsedSec, target, isRunning = true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_PAUSE -> {
                val elapsedSec = intent.getIntExtra(EXTRA_ELAPSED_SECONDS, 0)
                val target = intent.getStringExtra(EXTRA_TARGET) ?: "morning"
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildLiveNotification(elapsedSec, target, isRunning = false))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
