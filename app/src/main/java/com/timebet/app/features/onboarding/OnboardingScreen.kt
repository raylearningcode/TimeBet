package com.timebet.app.features.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeBetConstants
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.launch

/**
 * Onboarding flow per PRD Section 39 + Phase 1.
 *
 * Steps:
 * 1. Welcome
 * 2. Permission explanation
 * 3. Permission grant (Usage Access + Overlay)
 * 4. Choose base daily allowance
 * 5. Complete
 */
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var selectedAllowance by remember { mutableLongStateOf(TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS) }
    var hasUsageAccess by remember { mutableStateOf(false) }
    var hasOverlayPerm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == step) 32.dp else 8.dp, 4.dp)
                            .background(
                                if (i <= step) TimeBetWhite else TimeBetBorder,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            when (step) {
                0 -> WelcomeStep(onNext = { step = 1 })
                1 -> PermissionExplanationStep(
                    onNext = { step = 2 }
                )
                2 -> PermissionGrantStep(
                    hasUsageAccess = hasUsageAccess,
                    hasOverlay = hasOverlayPerm,
                    onRequestUsageAccess = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    },
                    onRequestOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onCheckPermissions = {
                        hasUsageAccess = ServiceLocator.permissionMonitor.hasUsageStatsPermission()
                        hasOverlayPerm = Settings.canDrawOverlays(context)
                    },
                    onNext = { step = 3 }
                )
                3 -> AllowanceStep(
                    selected = selectedAllowance,
                    onSelect = { selectedAllowance = it },
                    onComplete = {
                        scope.launch {
                            // Save settings
                            val db = ServiceLocator.database
                            db.userSettingsDao().upsert(
                                com.timebet.app.core.database.entity.UserSettingsEntity(
                                    baseDailyAllowanceSeconds = selectedAllowance
                                )
                            )
                            // Initialize today's bank
                            ServiceLocator.timeBankEngine.ensureDailyReset()
                            // Mark onboarding complete
                            OnboardingPreferences.setOnboardingComplete(context)
                            // Schedule daily reset
                            ServiceLocator.dailyResetManager.schedule()

                            onOnboardingComplete()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "TIMEBET",
            style = TimeBetTypography.headlineMedium,
            color = TimeBetTextSecondary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Your time is limited.\nSpend it, or risk it.",
            style = TimeBetTypography.headlineLarge,
            color = TimeBetWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Risk time. Not money.",
            style = TimeBetTypography.bodyLarge,
            color = TimeBetTextTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started", style = TimeBetTypography.labelLarge)
        }
    }
}

@Composable
private fun PermissionExplanationStep(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Permissions",
            style = TimeBetTypography.headlineLarge,
            color = TimeBetWhite
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "TimeBet needs two permissions to work. Here's why:",
            style = TimeBetTypography.bodyLarge,
            color = TimeBetTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        PermissionCard(
            title = "Usage Access",
            description = "Detects when you open entertainment apps so time can be deducted automatically.\n\nTimeBet cannot see what you do inside apps — only which app is open."
        )
        Spacer(modifier = Modifier.height(16.dp))
        PermissionCard(
            title = "Display Over Other Apps",
            description = "Shows the Time's Up screen when your daily entertainment time runs out.\n\nWithout this, TimeBet can't block apps at zero balance."
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue", style = TimeBetTypography.labelLarge)
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, style = TimeBetTypography.labelLarge, color = TimeBetWhite)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
        }
    }
}

@Composable
private fun PermissionGrantStep(
    hasUsageAccess: Boolean,
    hasOverlay: Boolean,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
    onCheckPermissions: () -> Unit,
    onNext: () -> Unit
) {
    LaunchedEffect(Unit) { onCheckPermissions() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Grant Permissions", style = TimeBetTypography.headlineLarge, color = TimeBetWhite)
        Spacer(modifier = Modifier.height(32.dp))

        // Usage Access
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Usage Access", style = TimeBetTypography.labelLarge, color = TimeBetWhite)
                Text(
                    if (hasUsageAccess) "Granted" else "Required",
                    style = TimeBetTypography.bodyMedium,
                    color = if (hasUsageAccess) TimeBetGreen else TimeBetAmber
                )
            }
            if (!hasUsageAccess) {
                TextButton(onClick = onRequestUsageAccess) {
                    Text("Grant", color = TimeBetWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = TimeBetBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // Overlay
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Display Over Apps", style = TimeBetTypography.labelLarge, color = TimeBetWhite)
                Text(
                    if (hasOverlay) "Granted" else "Required",
                    style = TimeBetTypography.bodyMedium,
                    color = if (hasOverlay) TimeBetGreen else TimeBetAmber
                )
            }
            if (!hasOverlay) {
                TextButton(onClick = onRequestOverlay) {
                    Text("Grant", color = TimeBetWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Re-check button
        OutlinedButton(
            onClick = onCheckPermissions,
            modifier = Modifier.fillMaxWidth(),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(TimeBetBorderLight)
            )
        ) {
            Text("Check Again", color = TimeBetTextSecondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            enabled = hasUsageAccess && hasOverlay,
            colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue", style = TimeBetTypography.labelLarge)
        }
    }
}

@Composable
private fun AllowanceStep(
    selected: Long,
    onSelect: (Long) -> Unit,
    onComplete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Daily Allowance", style = TimeBetTypography.headlineLarge, color = TimeBetWhite)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "How much entertainment time\ndo you want each day?",
            style = TimeBetTypography.bodyLarge,
            color = TimeBetTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Quick options
        Column(modifier = Modifier.fillMaxWidth()) {
            TimeBetConstants.BASE_ALLOWANCE_OPTIONS.forEach { allowance ->
                val isSelected = selected == allowance
                OutlinedButton(
                    onClick = { onSelect(allowance) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isSelected) TimeBetWhite else TimeBetBorderLight
                        )
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) TimeBetSurfaceElevated else TimeBetBlack
                    )
                ) {
                    Text(
                        TimeFormatter.formatHumanReadable(allowance),
                        style = TimeBetTypography.headlineMedium,
                        color = if (isSelected) TimeBetWhite else TimeBetTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "You can change this anytime in Settings.",
            style = TimeBetTypography.labelSmall,
            color = TimeBetTextTertiary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Using TimeBet", style = TimeBetTypography.labelLarge)
        }
    }
}
