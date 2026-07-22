package com.timebet.app.core.permissions

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors the health of required permissions.
 *
 * PRD Section 18.5, 39:
 * - If usage access stops working, show an explicit tracking failure state
 * - Pause authoritative balance deduction if accuracy cannot be guaranteed
 * - Prompt user to restore permissions
 */
class PermissionHealthMonitor(private val context: Context) {

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.UNKNOWN)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _lastSuccessfulCheck = MutableStateFlow<Long?>(null)
    val lastSuccessfulCheck: StateFlow<Long?> = _lastSuccessfulCheck.asStateFlow()

    /**
     * Check all required permissions and return the current tracking state.
     */
    fun checkPermissions(): TrackingState {
        val hasUsageStats = hasUsageStatsPermission()
        val canOverlay = canDrawOverlays()

        val state = when {
            !hasUsageStats -> TrackingState.PERMISSION_MISSING(
                missingPermissions = buildList {
                    if (!hasUsageStats) add(RequiredPermission.USAGE_STATS)
                    if (!canOverlay) add(RequiredPermission.SYSTEM_ALERT_WINDOW)
                }
            )
            else -> TrackingState.HEALTHY
        }

        if (state == TrackingState.HEALTHY) {
            _lastSuccessfulCheck.value = System.currentTimeMillis()
        }

        _trackingState.value = state
        return state
    }

    /**
     * Verify usage stats permission via AppOpsManager.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // Use unsafeCheckOpNoThrow (API 29+) — more reliable than deprecated checkOpNoThrow
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Check overlay permission (needed for app blocking).
     */
    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Get IDs of all currently missing permissions.
     */
    fun getMissingPermissions(): List<RequiredPermission> {
        return buildList {
            if (!hasUsageStatsPermission()) add(RequiredPermission.USAGE_STATS)
            if (!canDrawOverlays()) add(RequiredPermission.SYSTEM_ALERT_WINDOW)
        }
    }
}

sealed class TrackingState {
    /** Initial state before first check */
    data object UNKNOWN : TrackingState()

    /** All required permissions are granted */
    data object HEALTHY : TrackingState()

    /** One or more required permissions are missing */
    data class PERMISSION_MISSING(
        val missingPermissions: List<RequiredPermission>
    ) : TrackingState()

    /** Tracking was healthy but has become unreliable */
    data class UNRELIABLE(
        val reason: String,
        val lastSuccessfulAt: Long?
    ) : TrackingState()
}

enum class RequiredPermission(val permissionId: String, val displayName: String, val description: String) {
    USAGE_STATS(
        "android.permission.PACKAGE_USAGE_STATS",
        "Usage Access",
        "TimeBet needs usage access to detect when you're using entertainment apps. " +
        "This allows automatic time deduction. TimeBet cannot see what you do inside apps, " +
        "only which apps are open."
    ),
    SYSTEM_ALERT_WINDOW(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "Display Over Other Apps",
        "TimeBet needs this to show the block screen when your Time Bank reaches zero. " +
        "Without it, entertainment apps cannot be blocked."
    )
}
