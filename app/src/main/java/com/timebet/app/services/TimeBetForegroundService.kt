package com.timebet.app.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.timebet.app.MainActivity
import com.timebet.app.ServiceLocator
import com.timebet.app.core.blocking.AppBlockController
import com.timebet.app.core.monitoring.ForegroundUsageMonitor
import com.timebet.app.core.time.TimeBankEngine
import com.timebet.app.core.notifications.NotificationChannels
import com.timebet.app.core.notifications.NotificationIds
import com.timebet.app.util.TimeBetConstants
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.*

/**
 * Foreground service that keeps usage monitoring alive.
 *
 * PRD Section 32.2: Foreground service where necessary for reliability.
 * Android requires a persistent notification for foreground services.
 * This service hosts the ForegroundUsageMonitor and AppBlockController.
 *
 * Also handles:
 * - Posting low-time / time-up / tracking-failure notifications
 * - Casino round recovery on startup
 */
class TimeBetForegroundService : Service() {

    private lateinit var usageMonitor: ForegroundUsageMonitor
    private lateinit var blockController: AppBlockController
    private lateinit var timeBankEngine: TimeBankEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val firedThresholds = mutableSetOf<Long>()

    override fun onCreate() {
        super.onCreate()

        timeBankEngine = ServiceLocator.timeBankEngine
        usageMonitor = ServiceLocator.usageMonitor
        blockController = ServiceLocator.blockController

        // Wire up callbacks that the service needs
        usageMonitor.onBalanceChanged = { newBalance ->
            checkLowTimeThresholds(newBalance)
        }
        usageMonitor.onTrackingFailed = {
            postTrackingFailure()
        }

        // Recover any unsettled casino rounds from a previous crash
        serviceScope.launch {
            try {
                ServiceLocator.timeBankRepository.recoverUnsettledRounds()
            } catch (_: Exception) { }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NotificationIds.FOREGROUND_SERVICE, notification)

        usageMonitor.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        usageMonitor.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── Low-Time Threshold Checks ───

    private fun checkLowTimeThresholds(balanceSeconds: Long) {
        val thresholds = TimeBetConstants.LOW_TIME_THRESHOLDS
        for (threshold in thresholds) {
            if (balanceSeconds <= threshold && firedThresholds.add(threshold)) {
                postLowTimeWarning(balanceSeconds)
            }
        }
        // Check time-up
        if (balanceSeconds <= 0 && firedThresholds.add(0L)) {
            postTimeUp()
        }
    }

    // ─── Notification Posting ───

    private fun postLowTimeWarning(remainingSeconds: Long) {
        val formatted = TimeFormatter.formatMinutesShort(remainingSeconds)
        val notification = NotificationCompat.Builder(this, NotificationChannels.LOW_TIME)
            .setContentTitle("Time Running Low")
            .setContentText("$formatted remaining in your Time Bank")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainPendingIntent())
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationIds.LOW_TIME_WARNING, notification)
    }

    private fun postTimeUp() {
        val notification = NotificationCompat.Builder(this, NotificationChannels.BLOCKING)
            .setContentTitle("Time's Up")
            .setContentText("You've used today's entertainment time. Controlled apps are now blocked.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainPendingIntent())
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationIds.TIME_UP, notification)
    }

    private fun postTrackingFailure() {
        val notification = NotificationCompat.Builder(this, NotificationChannels.BLOCKING)
            .setContentTitle("Tracking Unavailable")
            .setContentText("TimeBet can't track app usage. Tap to fix permissions.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainPendingIntent())
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationIds.TRACKING_FAILURE, notification)
    }

    private fun createMainPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationChannels.BLOCKING)
            .setContentTitle("TimeBet")
            .setContentText("Monitoring entertainment app usage")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createMainPendingIntent())
            .build()
    }
}
