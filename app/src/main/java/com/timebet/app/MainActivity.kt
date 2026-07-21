package com.timebet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.timebet.app.design.theme.TimeBetBlack
import com.timebet.app.design.theme.TimeBetTheme
import com.timebet.app.features.onboarding.OnboardingPreferences
import com.timebet.app.navigation.NavRoute
import com.timebet.app.navigation.TimeBetNavGraph

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
                    TimeBetNavGraph(
                        startDestination = if (hasCompletedOnboarding) {
                            NavRoute.Home.route
                        } else {
                            NavRoute.Onboarding.route
                        }
                    )
                }
            }
        }
    }
}
