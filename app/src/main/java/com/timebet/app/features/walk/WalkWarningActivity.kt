package com.timebet.app.features.walk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.design.theme.*

/**
 * Full-screen overlay shown when walking is detected while a controlled app is active.
 *
 * Two options:
 * - "Put phone away" -> goes to home screen, ends the controlled app session
 * - "I need this (2x)" -> dismisses overlay, user continues with double time burn
 */
class WalkWarningActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "this app"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        setContent {
            TimeBetTheme {
                WalkWarningScreen(
                    appName = appName,
                    onPutPhoneAway = {
                        // Go to home screen
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    },
                    onContinueWith2x = {
                        // Tell monitor to use 2x multiplier
                        // Multiplier already set by monitor before launching this activity
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun WalkWarningScreen(
    appName: String,
    onPutPhoneAway: () -> Unit,
    onContinueWith2x: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Warning icon
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = TimeBetAmber,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "You're walking —\nput your phone away",
                style = MaterialTheme.typography.headlineMedium,
                color = TimeBetWhite,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "$appName is currently open",
                style = MaterialTheme.typography.bodyLarge,
                color = TimeBetTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            val multiplier = ServiceLocator.usageMonitor.walkMultiplier
            Text(
                "Continued use will burn time at ${multiplier}x the normal rate",
                style = MaterialTheme.typography.labelMedium,
                color = TimeBetAmber,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Primary: Put phone away
            Button(
                onClick = onPutPhoneAway,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TimeBetWhite,
                    contentColor = TimeBetBlack
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Put phone away", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary: Continue at 2x
            OutlinedButton(
                onClick = onContinueWith2x,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TimeBetTextSecondary),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, TimeBetBorder)
            ) {
                Text("I need this (${multiplier}x time)", color = TimeBetTextSecondary)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "This will dismiss automatically when you stop walking",
                style = MaterialTheme.typography.labelSmall,
                color = TimeBetTextTertiary
            )
        }
    }
}
