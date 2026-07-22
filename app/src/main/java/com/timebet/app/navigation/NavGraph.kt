package com.timebet.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.timebet.app.ServiceLocator
import com.timebet.app.core.auth.AuthState
import com.timebet.app.features.activity.ActivityScreen
import com.timebet.app.features.auth.LoginScreen
import com.timebet.app.features.casino.CasinoLandingScreen
import com.timebet.app.features.casino.baccarat.BaccaratScreen
import com.timebet.app.features.casino.blackjack.BlackjackScreen
import com.timebet.app.features.casino.chicken.ChickenScreen
import com.timebet.app.features.casino.coinflip.CoinFlipScreen
import com.timebet.app.features.casino.crash.CrashScreen
import com.timebet.app.features.casino.mines.MinesScreen
import com.timebet.app.features.casino.roulette.RouletteScreen
import com.timebet.app.features.controlledapps.ControlledAppsScreen
import com.timebet.app.features.home.AppDetailScreen
import com.timebet.app.features.home.HomeScreen
import com.timebet.app.features.onboarding.OnboardingScreen
import com.timebet.app.features.settings.DevicesScreen
import com.timebet.app.features.settings.SettingsScreen
import com.timebet.app.features.sports.MatchDetailScreen
import com.timebet.app.features.sports.SportsLandingScreen

/**
 * Bottom nav destinations where the nav bar should be visible.
 * Immersive game screens and detail screens hide it (PRD Section 23, 29.6 #15).
 */
private val screensWithBottomNav = setOf(
    NavRoute.Home.route,
    NavRoute.Casino.route,
    NavRoute.Sports.route,
    NavRoute.Activity.route
)

@Composable
fun TimeBetNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.Onboarding.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in screensWithBottomNav

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Onboarding
            composable(NavRoute.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(NavRoute.Login.route) {
                            popUpTo(NavRoute.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // Main tabs
            composable(NavRoute.Home.route) {
                HomeScreen(
                    onAppClick = { packageName ->
                        navController.navigate(NavRoute.AppDetail.createRoute(packageName))
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoute.Settings.route)
                    }
                )
            }

            composable(NavRoute.Casino.route) {
                CasinoLandingScreen(
                    onGameClick = { route ->
                        navController.navigate(route)
                    }
                )
            }

            composable(NavRoute.Sports.route) {
                SportsLandingScreen(
                    onMatchClick = { eventId ->
                        navController.navigate(NavRoute.MatchDetail.createRoute(eventId))
                    }
                )
            }

            composable(NavRoute.Activity.route) {
                ActivityScreen()
            }

            // Auth
            composable(NavRoute.Login.route) {
                LoginScreen(
                    onLoginComplete = {
                        navController.navigate(NavRoute.Home.route) {
                            popUpTo(NavRoute.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Detail screens
            composable(
                route = NavRoute.AppDetail.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppDetailScreen(
                    packageName = packageName,
                    onBack = { navController.popBackStack() }
                )
            }

            // Casino games — immersive, no bottom nav
            composable(NavRoute.CoinFlip.route) {
                CoinFlipScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Mines.route) {
                MinesScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Roulette.route) {
                RouletteScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Blackjack.route) {
                BlackjackScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Crash.route) {
                CrashScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Baccarat.route) {
                BaccaratScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Chicken.route) {
                ChickenScreen(onBack = { navController.popBackStack() })
            }

            // Sports detail screens
            composable(
                route = NavRoute.MatchDetail.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                MatchDetailScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() }
                )
            }
            // PredictionSlip removed — bets now placed via bottom sheet in SportsLandingScreen and MatchDetailScreen

            // Settings
            composable(NavRoute.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToControlledApps = {
                        navController.navigate(NavRoute.ControlledApps.route)
                    },
                    onNavigateToDevices = {
                        navController.navigate(NavRoute.Devices.route)
                    },
                    onNavigateToLogin = {
                        navController.navigate(NavRoute.Login.route)
                    }
                )
            }

            // Devices
            composable(NavRoute.Devices.route) {
                DevicesScreen(onBack = { navController.popBackStack() })
            }

            // Controlled Apps management
            composable(NavRoute.ControlledApps.route) {
                ControlledAppsScreen(onBack = { navController.popBackStack() })
            }

        }
    }
}
