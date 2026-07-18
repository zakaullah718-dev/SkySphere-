package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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
        activeIcon = Icons.Filled.Home,
        inactiveIcon = Icons.Outlined.Home
    )
    
    object Search : Screen(
        route = "search",
        title = "Explore",
        activeIcon = Icons.Filled.Search,
        inactiveIcon = Icons.Outlined.Search
    )
    
    object Favorites : Screen(
        route = "favorites",
        title = "Vault",
        activeIcon = Icons.Filled.Favorite,
        inactiveIcon = Icons.Outlined.FavoriteBorder
    )
    
    object Settings : Screen(
        route = "settings",
        title = "Settings",
        activeIcon = Icons.Filled.Settings,
        inactiveIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems = listOf(Home, Search, Favorites, Settings)
    }
}
