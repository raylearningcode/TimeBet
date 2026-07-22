package com.timebet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.timebet.app.core.auth.AuthState
import com.timebet.app.design.theme.TimeBetBlack
import com.timebet.app.design.theme.TimeBetTheme
import com.timebet.app.features.onboarding.OnboardingPreferences
import com.timebet.app.navigation.NavRoute
import com.timebet.app.navigation.TimeBetNavGraph
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasCompletedOnboarding = OnboardingPreferences.hasCompletedOnboarding(this)

        setContent {
            TimeBetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TimeBetBlack
                ) {
                    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }

                    // Observe auth state
                    LaunchedEffect(Unit) {
                        ServiceLocator.authManager.authState.collectLatest { state ->
                            authState = state
                            // Start sync if authenticated
                            if (state is AuthState.Authenticated) {
                                ServiceLocator.syncEngine.start()
                            }
                        }
                    }

                    when {
                        authState is AuthState.Loading -> {
                            // Brief splash while checking auth
                            SplashPlaceholder()
                        }
                        !hasCompletedOnboarding -> {
                            TimeBetNavGraph(startDestination = NavRoute.Onboarding.route)
                        }
                        authState is AuthState.Unauthenticated -> {
                            TimeBetNavGraph(startDestination = NavRoute.Login.route)
                        }
                        else -> {
                            TimeBetNavGraph(startDestination = NavRoute.Home.route)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TimeBetBlack
    ) {}
}
