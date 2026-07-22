package com.timebet.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavRoute(
    val route: String
) {
    // Bottom nav destinations
    data object Home : NavRoute("home")
    data object Casino : NavRoute("casino")
    data object Sports : NavRoute("sports")
    data object Activity : NavRoute("activity")

    // Detail screens
    data object AppDetail : NavRoute("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    data object Onboarding : NavRoute("onboarding")
    data object Settings : NavRoute("settings")
    data object ControlledApps : NavRoute("controlled_apps")

    // Casino game screens
    data object CoinFlip : NavRoute("casino/coin_flip")
    data object Mines : NavRoute("casino/mines")
    data object Roulette : NavRoute("casino/roulette")
    data object Blackjack : NavRoute("casino/blackjack")
    data object Crash : NavRoute("casino/crash")
    data object Baccarat : NavRoute("casino/baccarat")
    data object Chicken : NavRoute("casino/chicken")

    // Sports detail screens
    data object MatchDetail : NavRoute("sports/match/{eventId}") {
        fun createRoute(eventId: String) = "sports/match/$eventId"
    }
    data object PredictionSlip : NavRoute("sports/prediction/{eventId}/{marketType}/{selection}") {
        fun createRoute(eventId: String, marketType: String, selection: String) =
            "sports/prediction/$eventId/$marketType/$selection"
    }

    // Auth
    data object Login : NavRoute("login")

    // Blocked screen
    data object Blocked : NavRoute("blocked")
}

data class BottomNavItem(
    val route: NavRoute,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoute.Home, Icons.Filled.Home, "Home"),
    BottomNavItem(NavRoute.Casino, Icons.Filled.Casino, "Casino"),
    BottomNavItem(NavRoute.Sports, Icons.Filled.SportsSoccer, "Sports"),
    BottomNavItem(NavRoute.Activity, Icons.Filled.QueryStats, "Activity")
)
