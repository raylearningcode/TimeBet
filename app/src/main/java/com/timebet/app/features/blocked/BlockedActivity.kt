package com.timebet.app.features.blocked

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.DailyTimeBankState
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import java.time.LocalTime

/**
 * Full-screen block overlay shown when Time Bank reaches zero.
 *
 * PRD Section 22: Zero-balance behavior.
 * - Shows "00:00 TIME'S UP" display
 * - Displays usage summary
 * - Provides View Activity and Back actions
 * - NEVER shows buy time, watch ad, free spin, or borrow time
 */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra("blocked_package") ?: ""

        setContent {
            TimeBetTheme {
                BlockedScreen(
                    blockedPackage = blockedPackage,
                    onBack = { finish() },
                    onViewActivity = {
                        // Navigate to activity — handled by the caller
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun BlockedScreen(
    blockedPackage: String,
    onBack: () -> Unit,
    onViewActivity: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var bankState by remember { mutableStateOf<DailyTimeBankState?>(null) }

    LaunchedEffect(Unit) {
        bankState = ServiceLocator.timeBankEngine.getDailyBank()
    }

    val nextResetHour = remember {
        val now = LocalTime.now()
        val midnight = LocalTime.of(23, 59)
        val minutesUntil = (midnight.toSecondOfDay() - now.toSecondOfDay()) / 60
        if (minutesUntil < 60) "${minutesUntil}m" else "${minutesUntil / 60}h ${minutesUntil % 60}m"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Main "00:00" display
            Text(
                text = "00:00",
                style = TimeBetTypography.displayLarge,
                color = TimeBetWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "TIME'S UP",
                style = TimeBetTypography.headlineMedium,
                color = TimeBetTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "You've used today's available entertainment time.",
                style = TimeBetTypography.bodyLarge,
                color = TimeBetTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Usage summary
            bankState?.let { bank ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimeBetSurfaceElevated, MaterialTheme.shapes.medium)
                        .padding(20.dp)
                ) {
                    SummaryRow("Used Today", TimeFormatter.formatHumanReadable(bank.usedSeconds))
                    SummaryRow("Time Won", "+${TimeFormatter.formatHumanReadable(bank.netCasinoProfit.coerceAtLeast(0))}")
                    SummaryRow("Time Lost", "-${TimeFormatter.formatHumanReadable(bank.casinoLossSeconds)}")
                    SummaryRow("Next Reset", nextResetHour)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions
            TextButton(onClick = onViewActivity) {
                Text(
                    text = "View Activity",
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetWhite
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBack) {
                Text(
                    text = "Back",
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetTextSecondary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Text(
                text = "Risk time. Not money.",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = TimeBetTypography.bodyMedium,
            color = TimeBetTextSecondary
        )
        Text(
            text = value,
            style = TimeBetTypography.bodyMedium,
            color = TimeBetWhite
        )
    }
}
