package com.timebet.app.core.blocking

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.timebet.app.core.time.TimeBankEngine
import com.timebet.app.features.blocked.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controls blocking of entertainment apps when Time Bank reaches zero.
 *
 * PRD Section 22, 32.4:
 * - When balance <= 0 and foreground package is controlled, trigger block experience
 * - Prevent ordinary continued access using the selected Android enforcement mechanism
 * - Blocking behavior must be tested across OEM variants
 */
class AppBlockController(
    private val context: Context,
    private val timeBankEngine: TimeBankEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isBlocking = MutableStateFlow(false)
    val isBlocking: StateFlow<Boolean> = _isBlocking.asStateFlow()

    /**
     * Check if controlled apps should be blocked and trigger blocking if needed.
     *
     * @param currentForegroundPackage The package currently in foreground
     * @param controlledPackages Set of controlled package names
     * @return true if the current app should be blocked
     */
    suspend fun checkAndBlock(
        currentForegroundPackage: String?,
        controlledPackages: Set<String>
    ): Boolean {
        if (currentForegroundPackage == null) return false
        if (currentForegroundPackage == context.packageName) return false
        if (currentForegroundPackage !in controlledPackages) return false

        val balance = timeBankEngine.getBalance()
        if (balance <= 0) {
            triggerBlocking(currentForegroundPackage)
            return true
        }

        return false
    }

    /**
     * Trigger the block screen overlay.
     * PRD Section 22: Show "Time's Up" screen with usage summary.
     */
    fun triggerBlocking(blockedPackage: String) {
        if (_isBlocking.value) return

        scope.launch {
            _isBlocking.value = true

            val intent = Intent(context, BlockedActivity::class.java).apply {
                putExtra("blocked_package", blockedPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Release the block (called when the user exits the blocked screen).
     */
    fun releaseBlock() {
        _isBlocking.value = false
    }

    /**
     * Check if a given package should be blocked based on current state.
     */
    suspend fun shouldBlock(packageName: String, controlledPackages: Set<String>): Boolean {
        if (packageName !in controlledPackages) return false
        return timeBankEngine.getBalance() <= 0
    }
}
