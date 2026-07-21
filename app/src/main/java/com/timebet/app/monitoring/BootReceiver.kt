package com.timebet.app.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timebet.app.core.time.DailyResetManager
import com.timebet.app.core.time.TimeBankEngine
import com.timebet.app.services.TimeBetForegroundService
import com.timebet.app.util.TimeBetConstants

/**
 * Handles device boot completion.
 *
 * PRD Section 32.5: After device reboot:
 * - Reinitialize tracking
 * - Restore today's balance
 * - Reconcile any open session conservatively
 * - Revalidate permissions
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Start the foreground service to resume monitoring
        val serviceIntent = Intent(context, TimeBetForegroundService::class.java)
        context.startForegroundService(serviceIntent)

        // The service will handle reinitializing tracking and reconciling state
    }
}
