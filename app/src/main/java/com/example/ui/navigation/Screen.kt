package com.example.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.icons.SkySphereIcons

sealed class Screen(
    val route: String,
    val title: String,
    val activeIcon: ImageVector? = null,
    val inactiveIcon: ImageVector? = null
) {
    object Splash : Screen("splash", "Splash")
    
    object Home : Screen(
        route = "home",
        title = "SkySphere",
        activeIcon = SkySphereIcons.HomeActive,
        inactiveIcon = SkySphereIcons.Home
    )
    
    object Search : Screen(
        route = "search",
        title = "Explore",
        activeIcon = SkySphereIcons.SearchActive,
        inactiveIcon = SkySphereIcons.Search
    )
    
    object Map : Screen(
        route = "map",
        title = "Radar Map",
        activeIcon = SkySphereIcons.MapActive,
        inactiveIcon = SkySphereIcons.Map
    )
    
    object Favorites : Screen(
        route = "favorites",
        title = "Vault",
        activeIcon = SkySphereIcons.VaultActive,
        inactiveIcon = SkySphereIcons.Vault
    )
    
    object Settings : Screen(
        route = "settings",
        title = "Settings",
        activeIcon = SkySphereIcons.SettingsActive,
        inactiveIcon = SkySphereIcons.Settings
    )

    companion object {
        val bottomNavItems = listOf(Home, Search, Map, Favorites, Settings)
    }
}
