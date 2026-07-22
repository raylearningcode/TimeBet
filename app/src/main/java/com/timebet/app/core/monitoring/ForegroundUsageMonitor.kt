package com.timebet.app.core.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.timebet.app.core.database.dao.AppUsageSessionDao
import com.timebet.app.core.database.dao.ControlledAppDao
import com.timebet.app.core.database.entity.AppUsageSessionEntity
import com.timebet.app.core.permissions.PermissionHealthMonitor
import com.timebet.app.core.permissions.TrackingState
import com.timebet.app.core.time.TimeBankEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Monitors which app is in the foreground and triggers time deduction.
 *
 * PRD Section 18, 32.2:
 * - Detect current foreground package using UsageStatsManager
 * - Deduct from shared Time Bank when a controlled app is active
 * - Pause deduction when non-controlled app is open or screen is locked
 *
 * Uses a polling-based approach with UsageStatsManager.queryEvents()
 * as the primary detection mechanism. Runs on a background coroutine.
 */
class ForegroundUsageMonitor(
    private val context: Context,
    private val controlledAppDao: ControlledAppDao,
    private val appUsageSessionDao: AppUsageSessionDao,
    private val timeBankEngine: TimeBankEngine,
    private val permissionMonitor: PermissionHealthMonitor,
    private val onBalanceChanged: ((Long) -> Unit)? = null,
    private val onTrackingFailed: (() -> Unit)? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())

    private val _activeApp = MutableStateFlow<ActiveAppState>(ActiveAppState.None)
    val activeApp: StateFlow<ActiveAppState> = _activeApp.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var currentSession: ActiveSession? = null
    private val controlledPackages = mutableSetOf<String>()
    private var lastCheckTime = 0L
    private var lastEventTime = 0L
    private var consecutiveEmptyPolls = 0
    private var pollIntervalMs = 2000L // 2 second polling

    companion object {
        private const val TAG = "ForegroundUsageMonitor"
        /** Number of empty polls before performing a usage-stats verification */
        private const val EMPTY_POLLS_BEFORE_VERIFY = 15 // ~30 seconds at 2s intervals
    }

    /**
     * Start monitoring foreground app usage.
     * PRD Section 32.2: Combine UsageStatsManager with periodic polling.
     */
    fun start() {
        if (_isMonitoring.value) return

        val state = permissionMonitor.checkPermissions()
        if (state != TrackingState.HEALTHY) {
            _isMonitoring.value = false
            onTrackingFailed?.invoke()
            return
        }

        _isMonitoring.value = true
        scope.launch {
            // Load controlled packages
            refreshControlledPackages()

            // Observe controlled app changes
            controlledAppDao.observeControlled().collect { apps ->
                controlledPackages.clear()
                controlledPackages.addAll(apps.map { it.packageName })
            }
        }

        // Start the polling loop
        scope.launch {
            while (isActive) {
                if (_isMonitoring.value) {
                    checkForegroundApp()
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        _isMonitoring.value = false
        endCurrentSession()
        scope.cancel()
    }

    /**
     * Set polling interval (used for battery optimization).
     */
    fun setPollInterval(ms: Long) {
        pollIntervalMs = ms.coerceIn(1000L, 10000L)
    }

    /**
     * Main polling check — queries recent usage events to detect foreground app.
     *
     * PRD Section 18, 32.2:
     * When no foreground events are found in the polling window, we do NOT assume
     * the device is locked. The user is most likely still using the same app — apps
     * don't generate MOVE_TO_FOREGROUND events while they're actively in use.
     * Instead, we maintain the current session and periodically verify via
     * queryUsageStats that the tracked app is still the most recently used.
     */
    private fun checkForegroundApp() {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return

            val now = System.currentTimeMillis()
            val startTime = if (lastCheckTime == 0L) now - pollIntervalMs * 2 else lastCheckTime - 1000

            val events = usageStatsManager.queryEvents(startTime, now)
            var foregroundPackage: String? = null
            var eventTime = now

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    foregroundPackage = event.packageName
                    eventTime = event.timeStamp
                }
            }

            lastCheckTime = now

            if (foregroundPackage != null) {
                if (foregroundPackage == context.packageName) {
                    // User switched to TimeBet itself — end any active controlled-app session
                    lastEventTime = now
                    consecutiveEmptyPolls = 0
                    if (currentSession != null) {
                        val deducted = endCurrentSession()
                        _activeApp.value = ActiveAppState.None
                        if (deducted > 0) {
                            scope.launch {
                                val result = timeBankEngine.deduct(deducted)
                                onBalanceChanged?.invoke(result.remainingBalance)
                            }
                        }
                    }
                } else {
                    // We have an explicit foreground event for another app — act on it
                    lastEventTime = now
                    consecutiveEmptyPolls = 0
                    handleForegroundChange(foregroundPackage, eventTime)
                }
            } else {
                // No new foreground event in this window.
                consecutiveEmptyPolls++

                if (currentSession != null) {
                    // Maintain current session — user is still using the same app.
                    // Periodically verify the session is still valid
                    if (consecutiveEmptyPolls >= EMPTY_POLLS_BEFORE_VERIFY) {
                        consecutiveEmptyPolls = 0
                        verifyCurrentSession(usageStatsManager, now)
                    }
                } else {
                    // No session running — try to detect if a controlled app is already
                    // in foreground (e.g., app was opened before monitoring started).
                    // Use queryUsageStats as fallback to find the most recently used app.
                    detectInitialForegroundApp(usageStatsManager, now)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkForegroundApp failed", e)
            // Permission likely revoked
            permissionMonitor.checkPermissions()
            onTrackingFailed?.invoke()
        }
    }

    /**
     * Verify that the currently tracked session's app is still the most recently used.
     *
     * Uses UsageStatsManager.queryUsageStats() as a fallback when no foreground events
     * have been seen for a while. This catches cases where:
     * - The user locked their phone
     * - The user switched to a non-controlled app via a fast gesture that didn't
     *   generate events in our polling window
     *
     * Only ends the session if the tracked app's lastUsedTime is significantly stale
     * (more than 10 seconds ago), indicating the user genuinely moved away.
     */
    private fun verifyCurrentSession(usageStatsManager: UsageStatsManager, now: Long) {
        try {
            val session = currentSession ?: return
            val lookbackStart = now - 10_000 // Look back 10 seconds

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lookbackStart,
                now
            )

            // Find the app with the most recent lastTimeUsed
            var mostRecentPackage: String? = null
            var mostRecentTime = 0L

            for (stats in usageStats) {
                if (stats.lastTimeUsed > mostRecentTime) {
                    mostRecentTime = stats.lastTimeUsed
                    mostRecentPackage = stats.packageName
                }
            }

            // If the tracked app is no longer the most recent, end the session
            if (mostRecentPackage != null && mostRecentPackage != session.packageName) {
                Log.d(TAG, "Session verify: ${session.packageName} no longer most recent ($mostRecentPackage is)")
                val deducted = endCurrentSession()
                _activeApp.value = ActiveAppState.None
                if (deducted > 0) {
                    scope.launch {
                        val result = timeBankEngine.deduct(deducted)
                        onBalanceChanged?.invoke(result.remainingBalance)
                    }
                }
            } else if (mostRecentPackage == session.packageName &&
                       (now - mostRecentTime) > 10_000) {
                // Tracked app is most recent but hasn't been used in 10+ seconds
                // — likely the device is locked
                Log.d(TAG, "Session verify: ${session.packageName} stale (last used ${now - mostRecentTime}ms ago)")
                val deducted = endCurrentSession()
                _activeApp.value = ActiveAppState.None
                if (deducted > 0) {
                    scope.launch {
                        val result = timeBankEngine.deduct(deducted)
                        onBalanceChanged?.invoke(result.remainingBalance)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyCurrentSession failed", e)
            // Don't disrupt tracking on verification failure — just try again later
        }
    }

    /**
     * Detect the initial foreground app when no session exists.
     * Uses queryUsageStats to find the most recently used app — catches apps
     * that were already open before TimeBet's monitoring started (cold start).
     */
    private fun detectInitialForegroundApp(usageStatsManager: UsageStatsManager, now: Long) {
        try {
            val lookbackStart = now - 5_000 // Look back 5 seconds
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lookbackStart,
                now
            )

            var mostRecentPackage: String? = null
            var mostRecentTime = 0L

            for (stats in usageStats) {
                if (stats.lastTimeUsed > mostRecentTime) {
                    mostRecentTime = stats.lastTimeUsed
                    mostRecentPackage = stats.packageName
                }
            }

            // If the most recent app is controlled and was used in the last 5 seconds,
            // start tracking it
            if (mostRecentPackage != null &&
                mostRecentPackage != context.packageName &&
                mostRecentPackage in controlledPackages &&
                (now - mostRecentTime) < 5_000
            ) {
                Log.d(TAG, "Initial detection: $mostRecentPackage is active (last used ${now - mostRecentTime}ms ago)")
                handleForegroundChange(mostRecentPackage, mostRecentTime)
                consecutiveEmptyPolls = 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initial foreground detection failed", e)
        }
    }

    /**
     * Handle a change in the foreground app.
     * PRD Section 18.3: Controlled app → deduct; non-controlled → pause; lock → pause.
     */
    private fun handleForegroundChange(packageName: String?, timestamp: Long) {
        val isControlled = packageName != null && packageName in controlledPackages
        val isTimeBet = packageName == context.packageName

        if (isControlled && !isTimeBet) {
            // Controlled app came to foreground — start/resume session
            if (currentSession == null || currentSession?.packageName != packageName) {
                endCurrentSession()
                startNewSession(packageName, timestamp)
            }
            _activeApp.value = ActiveAppState.Active(
                packageName = packageName,
                sessionStartTime = currentSession?.startTime ?: timestamp
            )
        } else {
            // Non-controlled app or lock — pause deduction
            if (currentSession != null) {
                val deducted = endCurrentSession()
                _activeApp.value = ActiveAppState.None

                // Trigger balance deduction
                if (deducted > 0) {
                    scope.launch {
                        val result = timeBankEngine.deduct(deducted)
                        onBalanceChanged?.invoke(result.remainingBalance)
                    }
                }
            }
            _activeApp.value = ActiveAppState.None
        }
    }

    private fun startNewSession(packageName: String, startTime: Long) {
        currentSession = ActiveSession(
            packageName = packageName,
            startTime = startTime
        )
    }

    /**
     * End the current tracking session and persist to database.
     * Returns the duration in seconds to deduct from the Time Bank.
     *
     * PRD Section 32.3: Record active session start timestamp; calculate
     * elapsed duration when state changes; deduct atomically.
     */
    private fun endCurrentSession(): Long {
        val session = currentSession ?: return 0
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - session.startTime
        // Only count sessions >= 2 seconds to filter noise
        if (durationMs < 2000) {
            currentSession = null
            return 0
        }

        val durationSeconds = durationMs / 1000L

        // Persist session to database
        scope.launch {
            appUsageSessionDao.insert(
                AppUsageSessionEntity(
                    packageName = session.packageName,
                    startedAt = session.startTime,
                    endedAt = endTime,
                    durationSeconds = durationSeconds,
                    wasControlled = true
                )
            )
        }

        currentSession = null
        return durationSeconds
    }

    private suspend fun refreshControlledPackages() {
        val apps = controlledAppDao.getControlled()
        controlledPackages.clear()
        controlledPackages.addAll(apps.map { it.packageName })
    }
}

data class ActiveSession(
    val packageName: String,
    val startTime: Long
)

sealed class ActiveAppState {
    data object None : ActiveAppState()
    data class Active(
        val packageName: String,
        val sessionStartTime: Long
    ) : ActiveAppState()
}
