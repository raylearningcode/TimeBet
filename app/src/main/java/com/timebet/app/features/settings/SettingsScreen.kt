package com.timebet.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.UserSettingsEntity
import com.timebet.app.core.permissions.PermissionHealthMonitor
import com.timebet.app.core.permissions.RequiredPermission
import com.timebet.app.core.permissions.TrackingState
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import com.timebet.app.util.TimeBetConstants
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToControlledApps: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<UserSettingsEntity?>(null) }
    var trackingState by remember { mutableStateOf<TrackingState>(TrackingState.UNKNOWN) }

    // Allowance change dialog state
    var showAllowanceDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingAllowance by remember { mutableLongStateOf(TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS) }

    LaunchedEffect(Unit) {
        ServiceLocator.timeBankRepository.observeSettings().collect { s ->
            settings = s
        }
        trackingState = ServiceLocator.permissionMonitor.checkPermissions()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TimeBetBlack)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
            }
            Text("Settings", style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Time Bank ──
            SettingsSection("Time Bank") {
                settings?.let { s ->
                    SettingsRow(
                        label = "Daily Allowance",
                        value = TimeFormatter.formatHumanReadable(s.baseDailyAllowanceSeconds),
                        onClick = {
                            pendingAllowance = s.baseDailyAllowanceSeconds
                            showAllowanceDialog = true
                        }
                    )
                    SettingsRow(label = "Reset Time", value = "Midnight")
                    SettingsRow(
                        label = "Max Daily Bonus",
                        value = TimeFormatter.formatHumanReadable(
                            (s.baseDailyAllowanceSeconds * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong()
                        )
                    )
                    SettingsRow(
                        label = "Max Single Bet",
                        value = TimeFormatter.formatHumanReadable(
                            (s.baseDailyAllowanceSeconds * TimeBetConstants.MAX_STAKE_PERCENTAGE).toLong()
                        ) + " (50% of balance)"
                    )
                }
            }

            // ── Controlled Apps ──
            SettingsSection("Controlled Apps") {
                SettingsNavRow(label = "Manage Apps", route = "controlled_apps", onClick = onNavigateToControlledApps)
            }

            // ── Casino ──
            SettingsSection("Casino") {
                SettingsRow(label = "House Edge Transparency", value = "View") { /* TODO */ }
                SettingsRow(label = "Game Fairness", value = "Provably Fair") { /* TODO */ }
            }

            // ── Sports ──
            SettingsSection("Sports") {
                settings?.let { s ->
                    SettingsRow(
                        label = "Max Active Stake",
                        value = TimeFormatter.formatHumanReadable(
                            (s.baseDailyAllowanceSeconds * s.sportsStakeLimitPercentage).toLong()
                        )
                    )
                }
            }

            // ── Permissions ──
            SettingsSection("Permissions") {
                when (val state = trackingState) {
                    is TrackingState.HEALTHY -> SettingsRow(label = "Status", value = "All granted")
                    is TrackingState.PERMISSION_MISSING -> {
                        state.missingPermissions.forEach { perm ->
                            SettingsRow(label = perm.displayName, value = "Missing — tap to fix")
                        }
                    }
                    is TrackingState.UNRELIABLE -> SettingsRow(label = "Status", value = "Unreliable")
                    is TrackingState.UNKNOWN -> SettingsRow(label = "Status", value = "Checking...")
                }
                SettingsRow(label = "Verify Permissions", value = "Check Now") {
                    scope.launch { trackingState = ServiceLocator.permissionMonitor.checkPermissions() }
                }
            }

            // ── Notifications ──
            SettingsSection("Notifications") {
                SwitchSetting(label = "Low Time Warnings", checked = settings?.notificationsEnabled ?: true) { enabled ->
                    scope.launch { ServiceLocator.database.userSettingsDao().updateNotifications(enabled) }
                }
                SwitchSetting(label = "Haptics", checked = settings?.hapticsEnabled ?: true) { enabled ->
                    scope.launch { ServiceLocator.database.userSettingsDao().updateHaptics(enabled) }
                }
                SwitchSetting(label = "Sound", checked = settings?.soundEnabled ?: false) { enabled ->
                    scope.launch { ServiceLocator.database.userSettingsDao().updateSound(enabled) }
                }
            }

            // ── About ──
            SettingsSection("About") {
                SettingsRow(label = "Version", value = "1.0.0")
                SettingsRow(label = "Risk time. Not money.", value = "")
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // ── Allowance Picker Dialog ──
    if (showAllowanceDialog) {
        val currentAllowance = settings?.baseDailyAllowanceSeconds ?: TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS
        val hours = pendingAllowance / 3600
        val minutes = (pendingAllowance % 3600) / 60

        AlertDialog(
            onDismissRequest = { showAllowanceDialog = false },
            containerColor = TimeBetSurfaceElevated,
            title = {
                Text("Daily Allowance", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${hours}h ${minutes}m",
                        style = TimeBetTypography.displayMedium,
                        color = TimeBetGoldLight,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Choose how much time you get each day. This resets at midnight. A lower amount helps you stay disciplined.",
                        style = TimeBetTypography.bodyMedium,
                        color = TimeBetTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = pendingAllowance.toFloat(),
                        onValueChange = { pendingAllowance = it.toLong().coerceIn(
                            TimeBetConstants.MIN_BASE_ALLOWANCE_SECONDS,
                            TimeBetConstants.MAX_RECOMMENDED_ALLOWANCE_SECONDS
                        )},
                        valueRange = TimeBetConstants.MIN_BASE_ALLOWANCE_SECONDS.toFloat()..TimeBetConstants.MAX_RECOMMENDED_ALLOWANCE_SECONDS.toFloat(),
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = TimeBetWhite,
                            activeTrackColor = TimeBetWhite,
                            inactiveTrackColor = TimeBetBorder
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("30m", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Text("3h", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Text("6h", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }

                    if (pendingAllowance < currentAllowance) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, "Warning", tint = TimeBetAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Reducing your allowance takes effect immediately.",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetAmber
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingAllowance != currentAllowance) {
                            showConfirmDialog = true
                        } else {
                            showAllowanceDialog = false
                        }
                    },
                    enabled = pendingAllowance != currentAllowance,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showAllowanceDialog = false }) {
                    Text("Cancel", color = TimeBetTextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Confirmation Dialog ──
    if (showConfirmDialog) {
        val currentAllowance = settings?.baseDailyAllowanceSeconds ?: TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS
        val isReducing = pendingAllowance < currentAllowance
        val currentFormatted = TimeFormatter.formatHumanReadable(currentAllowance)
        val newFormatted = TimeFormatter.formatHumanReadable(pendingAllowance)

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = TimeBetSurfaceElevated,
            icon = {
                Icon(Icons.Filled.Warning, "Warning", tint = TimeBetAmber, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Are you sure?", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            },
            text = {
                Column {
                    Text(
                        if (isReducing)
                            "You're about to reduce your daily allowance from $currentFormatted to $newFormatted. This is a commitment — it can only be changed once per day."
                        else
                            "You're about to increase your daily allowance from $currentFormatted to $newFormatted. More time means more flexibility, but also more risk.",
                        style = TimeBetTypography.bodyMedium,
                        color = TimeBetTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (isReducing)
                            "A lower allowance helps build discipline. Are you committed to this change?"
                        else
                            "Only increase if you're sure you can handle more screen time responsibly.",
                        style = TimeBetTypography.labelMedium,
                        color = if (isReducing) TimeBetGreen else TimeBetAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            ServiceLocator.database.userSettingsDao().updateBaseAllowance(pendingAllowance)
                            ServiceLocator.timeBankEngine.ensureDailyReset()
                            showConfirmDialog = false
                            showAllowanceDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReducing) TimeBetGreen else TimeBetWhite,
                        contentColor = TimeBetBlack
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (isReducing) "I Commit — Reduce" else "I Understand — Increase") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Go Back", color = TimeBetTextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─── Reusable Settings Components ───

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp)).padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = TimeBetTypography.bodyMedium, color = if (onClick != null) TimeBetGoldLight else TimeBetTextSecondary)
            if (onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, "Edit", tint = TimeBetTextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsNavRow(label: String, route: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
        Icon(Icons.Filled.ChevronRight, "Navigate", tint = TimeBetTextSecondary)
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = TimeBetBlack, checkedTrackColor = TimeBetGreen, uncheckedThumbColor = TimeBetWhite, uncheckedTrackColor = TimeBetBorder)
        )
    }
}
