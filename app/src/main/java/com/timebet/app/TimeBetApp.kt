package com.timebet.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import com.timebet.app.core.database.AppDatabase
import com.timebet.app.core.notifications.NotificationChannels
import com.timebet.app.services.TimeBetForegroundService

class TimeBetApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.create(this)
        ServiceLocator.init(this)
        createNotificationChannels()

        // Start the foreground monitoring service on app launch
        try {
            val serviceIntent = Intent(this, TimeBetForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) { }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channels = listOf(
                NotificationChannel(
                    NotificationChannels.LOW_TIME,
                    "Low Time Warnings",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Warnings when your Time Bank is running low" },
                NotificationChannel(
                    NotificationChannels.BLOCKING,
                    "App Blocking",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifications when controlled apps are blocked" },
                NotificationChannel(
                    NotificationChannels.SPORTS,
                    "Sports Predictions",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Sports prediction settlement updates" }
            )
            manager.createNotificationChannels(channels)
        }
    }

    companion object {
        lateinit var instance: TimeBetApp
            private set
    }
}
