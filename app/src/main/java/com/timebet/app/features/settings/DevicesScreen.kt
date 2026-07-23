package com.timebet.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.auth.AuthState
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.launch

/**
 * Devices Screen — shows all devices linked to your account with per-device stats.
 */
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val authManager = ServiceLocator.authManager
    val currentDeviceId = authManager.deviceIdVal
    val currentDeviceName = authManager.deviceName
    var showRenameDialog by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf(currentDeviceName) }
    val scope = rememberCoroutineScope()

    // Collect device stats
    var deviceStats by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val stats = collectDeviceUsage()
            deviceStats = stats
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
            }
            Text("Your Devices", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TimeBetWhite)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current device card (highlighted)
                item {
                    Text(
                        "This Device",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceCard(
                        name = currentDeviceName,
                        deviceId = currentDeviceId,
                        isCurrentDevice = true,
                        todayUsage = deviceStats
                            .find { it.deviceId == currentDeviceId }?.todayUsageSeconds ?: 0L,
                        weekUsage = deviceStats
                            .find { it.deviceId == currentDeviceId }?.weekUsageSeconds ?: 0L,
                        lastSeen = "Now",
                        onRename = {
                            newDeviceName = currentDeviceName
                            showRenameDialog = true
                        }
                    )
                }

                // Other devices
                val otherDevices = deviceStats.filter { it.deviceId != currentDeviceId }
                if (otherDevices.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Other Devices",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextTertiary
                        )
                    }
                    items(otherDevices) { device ->
                        DeviceCard(
                            name = device.deviceName,
                            deviceId = device.deviceId,
                            isCurrentDevice = false,
                            todayUsage = device.todayUsageSeconds,
                            weekUsage = device.weekUsageSeconds,
                            lastSeen = device.lastSeen
                        )
                    }
                }

                // Info card
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TimeBetSurfaceElevated)
                            .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = TimeBetTextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "How sync works",
                                    style = TimeBetTypography.bodyMedium,
                                    color = TimeBetWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "All devices share one Time Bank. Time used on any device deducts from the same balance. Changes sync every 30 seconds.",
                                    style = TimeBetTypography.labelSmall,
                                    color = TimeBetTextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = TimeBetSurfaceElevated,
            title = { Text("Rename Device", color = TimeBetWhite) },
            text = {
                OutlinedTextField(
                    value = newDeviceName,
                    onValueChange = { newDeviceName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TimeBetWhite,
                        unfocusedTextColor = TimeBetWhite,
                        focusedBorderColor = TimeBetWhite,
                        unfocusedBorderColor = TimeBetBorder
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    authManager.setDeviceName(newDeviceName)
                    showRenameDialog = false
                }) { Text("Save", color = TimeBetWhite) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TimeBetTextTertiary)
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(
    name: String,
    deviceId: String,
    isCurrentDevice: Boolean,
    todayUsage: Long,
    weekUsage: Long,
    lastSeen: String,
    onRename: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentDevice) TimeBetSurfaceCard else TimeBetSurfaceElevated)
            .border(
                0.5.dp,
                if (isCurrentDevice) TimeBetGreen.copy(alpha = 0.3f) else TimeBetBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentDevice) TimeBetGreen.copy(alpha = 0.15f) else TimeBetSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCurrentDevice) Icons.Filled.PhoneAndroid else Icons.Filled.Tablet,
                    contentDescription = null,
                    tint = if (isCurrentDevice) TimeBetGreen else TimeBetTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = TimeBetTypography.bodyLarge,
                        color = TimeBetWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrentDevice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TimeBetGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "You",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetGreen
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Last seen: $lastSeen",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    StatChip("Today", TimeFormatter.formatMinutesShort(todayUsage))
                    Spacer(modifier = Modifier.width(12.dp))
                    StatChip("Week", TimeFormatter.formatMinutesShort(weekUsage))
                }
            }

            if (onRename != null) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, "Rename", tint = TimeBetTextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column {
        Text(value, style = TimeBetTypography.labelLarge.copy(fontFeatureSettings = "tnum"), color = TimeBetWhite)
        Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
    }
}

private data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val todayUsageSeconds: Long,
    val weekUsageSeconds: Long,
    val lastSeen: String
)

/**
 * Collects per-device usage stats from local DB (synced records include device info).
 */
private suspend fun collectDeviceUsage(): List<DeviceInfo> {
    val result = mutableMapOf<String, DeviceInfo>()

    try {
        val now = System.currentTimeMillis()
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val startOfWeek = java.time.LocalDate.now().minusDays(7)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sessions = ServiceLocator.database.appUsageSessionDao()
            .getByDateRange(startOfDay, now)

        for (s in sessions) {
            val id = s.deviceId.ifEmpty { "unknown" }
            val name = if (id == ServiceLocator.authManager.deviceIdVal) {
                ServiceLocator.authManager.deviceName
            } else {
                s.deviceName.ifEmpty { "Device ${id.take(8)}" }
            }
            val existing = result.getOrPut(id) {
                DeviceInfo(id, name, 0, 0, "")
            }
            result[id] = existing.copy(
                deviceName = name,
                todayUsageSeconds = existing.todayUsageSeconds + s.durationSeconds,
                lastSeen = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(s.startedAt))
            )
        }

        // Week usage
        val weekSessions = ServiceLocator.database.appUsageSessionDao()
            .getByDateRange(startOfWeek, now)
        for (s in weekSessions) {
            val id = s.deviceId.ifEmpty { "unknown" }
            val name = if (id == ServiceLocator.authManager.deviceIdVal) {
                ServiceLocator.authManager.deviceName
            } else {
                s.deviceName.ifEmpty { "Device ${id.take(8)}" }
            }
            val existing = result.getOrPut(id) {
                DeviceInfo(id, name, 0, 0, "")
            }
            result[id] = existing.copy(weekUsageSeconds = existing.weekUsageSeconds + s.durationSeconds)
        }
    } catch (_: Exception) { }

    return result.values.toList()
}
