package com.timebet.app.features.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import kotlinx.coroutines.launch

/**
 * Login Screen — Google Sign-In with Supabase.
 *
 * Shown on first launch when no session exists.
 * After successful sign-in, the user proceeds to the main app.
 */
@Composable
fun LoginScreen(onLoginComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = ServiceLocator.authManager
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> {
                isSigningIn = true
                errorMessage = null
                scope.launch {
                    val authResult = authManager.handleSignInResult(result.data)
                    isSigningIn = false
                    when (authResult) {
                        is com.timebet.app.core.auth.AuthResult.Success -> {
                            ServiceLocator.syncEngine.start()
                            onLoginComplete()
                        }
                        is com.timebet.app.core.auth.AuthResult.Error -> {
                            errorMessage = authResult.message
                        }
                    }
                }
            }
            result.resultCode == Activity.RESULT_CANCELED -> {
                // User cancelled — do nothing
                isSigningIn = false
            }
            else -> {
                // Some other result — likely an error
                isSigningIn = false
                errorMessage = "Sign-in failed (code: ${result.resultCode}). Try again."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo/brand
        Text(
            "TIMEBET",
            style = TimeBetTypography.labelMedium,
            color = TimeBetTextTertiary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Risk time. Not money.",
            style = TimeBetTypography.bodyMedium,
            color = TimeBetTextSecondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Info card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TimeBetSurfaceElevated, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = TimeBetTextSecondary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Sign in to sync across devices",
                    style = TimeBetTypography.headlineMedium,
                    color = TimeBetWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your time bank, settings, and controlled apps will sync seamlessly between your phone and tablet.",
                    style = TimeBetTypography.bodyMedium,
                    color = TimeBetTextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Google Sign-In button
        Button(
            onClick = {
                isSigningIn = true
                signInLauncher.launch(authManager.getSignInIntent())
            },
            enabled = !isSigningIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TimeBetWhite,
                contentColor = TimeBetBlack,
                disabledContainerColor = TimeBetSurfaceElevated,
                disabledContentColor = TimeBetTextTertiary
            )
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    color = TimeBetBlack,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "Sign in with Google",
                    style = TimeBetTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                errorMessage!!,
                style = TimeBetTypography.labelSmall,
                color = TimeBetRed,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Skip for now
        TextButton(onClick = onLoginComplete) {
            Text(
                "Skip for now",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
        }
    }
}
