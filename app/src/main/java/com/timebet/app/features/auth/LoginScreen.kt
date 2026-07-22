package com.timebet.app.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.design.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val authManager = ServiceLocator.authManager
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    fun doAuth() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both email and password"
            return
        }
        isSigningIn = true
        errorMessage = null
        scope.launch {
            val result = if (isSignUp) {
                authManager.signUp(email.trim(), password)
            } else {
                authManager.signIn(email.trim(), password)
            }
            isSigningIn = false
            when (result) {
                is com.timebet.app.core.auth.AuthResult.Success -> {
                    ServiceLocator.syncEngine.start()
                    onLoginComplete()
                }
                is com.timebet.app.core.auth.AuthResult.Error -> {
                    errorMessage = result.message
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(48.dp))

        Text("TIMEBET", style = TimeBetTypography.labelMedium,
            color = TimeBetTextTertiary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp))
        Spacer(Modifier.height(8.dp))
        Text("Risk time. Not money.", style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)

        Spacer(Modifier.height(40.dp))

        // Error card
        if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TimeBetRed.copy(alpha = 0.12f))
                    .border(1.dp, TimeBetRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = TimeBetRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(errorMessage!!, style = TimeBetTypography.bodyMedium, color = TimeBetRed)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Auth card
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TimeBetSurfaceElevated)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Lock, null, tint = TimeBetTextSecondary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isSignUp) "Create Account" else "Welcome Back",
                    style = TimeBetTypography.headlineMedium, color = TimeBetWhite
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isSignUp) "Sign up to sync across devices" else "Sign in to sync across devices",
                    style = TimeBetTypography.bodyMedium, color = TimeBetTextTertiary
                )
                Spacer(Modifier.height(20.dp))

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    placeholder = { Text("Email", color = TimeBetTextTertiary) },
                    leadingIcon = { Icon(Icons.Filled.Email, null, tint = TimeBetTextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TimeBetWhite, unfocusedTextColor = TimeBetWhite,
                        focusedBorderColor = TimeBetBorderLight, unfocusedBorderColor = TimeBetBorder,
                        cursorColor = TimeBetWhite,
                        focusedContainerColor = TimeBetSurfaceCard, unfocusedContainerColor = TimeBetSurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    placeholder = { Text("Password", color = TimeBetTextTertiary) },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, tint = TimeBetTextSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                null, tint = TimeBetTextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TimeBetWhite, unfocusedTextColor = TimeBetWhite,
                        focusedBorderColor = TimeBetBorderLight, unfocusedBorderColor = TimeBetBorder,
                        cursorColor = TimeBetWhite,
                        focusedContainerColor = TimeBetSurfaceCard, unfocusedContainerColor = TimeBetSurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))

                // Submit button
                Button(
                    onClick = { doAuth() },
                    enabled = !isSigningIn && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TimeBetWhite, contentColor = TimeBetBlack,
                        disabledContainerColor = TimeBetSurfaceElevated, disabledContentColor = TimeBetTextTertiary
                    )
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(color = TimeBetBlack, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isSignUp) "Create Account" else "Sign In",
                            style = TimeBetTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Toggle sign in/up
                TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null }) {
                    Text(
                        if (isSignUp) "Already have an account? Sign in" else "New? Create an account",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLoginComplete) {
            Text("Skip for now", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        }
        Spacer(Modifier.height(32.dp))
    }
}
