package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.WeatherRepository
import com.example.ui.screens.favorites.FavoritesScreen
import com.example.ui.screens.favorites.FavoritesViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.search.SearchViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen

@Composable
fun MainScreenShell(
    repository: WeatherRepository,
    darkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Collect global unit preferences
    val isCelsius by repository.isCelsius.collectAsState()
    val windUnit by repository.windUnit.collectAsState()

    // Instantiate viewmodels utilizing simple constructor injection
    val homeViewModel = remember { HomeViewModel(repository) }
    val searchViewModel = remember { SearchViewModel(repository) }
    val favoritesViewModel = remember { FavoritesViewModel(repository) }

    // Control visibility of Bottom Bar: Hide on Splash Screen
    val showBottomBar = currentRoute != Screen.Splash.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // SPLASH ROUTE
                composable(Screen.Splash.route) {
                    SplashScreen(
                        onNavigateToHome = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

                // HOME ROUTE
                composable(Screen.Home.route) {
                    HomeScreen(viewModel = homeViewModel)
                }

                // SEARCH ROUTE
                composable(Screen.Search.route) {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onCitySelected = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        }
                    )
                }

                // FAVORITES ROUTE
                composable(Screen.Favorites.route) {
                    FavoritesScreen(
                        viewModel = favoritesViewModel,
                        onCitySelected = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        }
                    )
                }

                // SETTINGS ROUTE
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        darkTheme = darkTheme,
                        onThemeToggle = onThemeToggle,
                        isCelsius = isCelsius,
                        onCelsiusToggle = { repository.setCelsius(it) },
                        windUnit = windUnit,
                        onWindUnitChange = { repository.setWindUnit(it) }
                    )
                }
            }

            // FLOATING GLASSMORPHIC BOTTOM NAVIGATION BAR
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding() // Safely pads bottom navigation from OS navigation bar
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            ) {
                FloatingBottomBar(
                    navController = navController,
                    currentRoute = currentRoute
                )
            }
        }
    }
}

@Composable
fun FloatingBottomBar(
    navController: NavController,
    currentRoute: String?
) {
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    // Glassmorphic translucent brush background
    val barBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFA13172E), // Translucent obsidian top
                Color(0xFA070913)  // Translucent black base
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF0FFFFFF),
                Color(0xF0F1F5F9)
            )
        )
    }

    val borderColor = if (isDark) Color(0xFF1D2447) else Color(0xFFE2E8F0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CircleShape)
            .background(barBrush)
            .border(1.dp, borderColor, CircleShape)
            .padding(horizontal = 12.dp)
            .testTag("floating_bottom_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route

            val activeIcon = screen.activeIcon ?: return@forEach
            val inactiveIcon = screen.inactiveIcon ?: return@forEach

            // Dynamic color/weight animations
            val iconColor by animateColorAsState(
                targetValue = if (isSelected) {
                    Color(0xFF2FA3FF) // Sky Blue for active selections
                } else {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                },
                label = "Bottom Tab Color Transition"
            )

            val indicatorSize by animateDpAsState(
                targetValue = if (isSelected) 46.dp else 0.dp,
                label = "Indicator Size Animation"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    }
                    .testTag("nav_item_${screen.route}"),
                contentAlignment = Alignment.Center
            ) {
                // Background Indicator Pill
                Box(
                    modifier = Modifier
                        .size(width = indicatorSize * 1.3f, height = indicatorSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2FA3FF).copy(alpha = if (isDark) 0.15f else 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) activeIcon else inactiveIcon,
                        contentDescription = screen.title,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = screen.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.sp,
                            color = iconColor
                        )
                    )
                }
            }
        }
    }
}
