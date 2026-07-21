package com.timebet.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.timebet.app.design.theme.TimeBetBlack
import com.timebet.app.design.theme.TimeBetBorder
import com.timebet.app.design.theme.TimeBetTextSecondary
import com.timebet.app.design.theme.TimeBetWhite

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = TimeBetBlack,
        contentColor = TimeBetWhite,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route.route) {
                        navController.navigate(item.route.route) {
                            popUpTo(NavRoute.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = com.timebet.app.design.theme.TimeBetTypography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TimeBetWhite,
                    selectedTextColor = TimeBetWhite,
                    unselectedIconColor = TimeBetTextSecondary,
                    unselectedTextColor = TimeBetTextSecondary,
                    indicatorColor = TimeBetBorder
                )
            )
        }
    }
}
