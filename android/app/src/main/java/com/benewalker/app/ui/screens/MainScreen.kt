package com.benewalker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.navigation.Screen
import com.benewalker.app.ui.navigation.bottomNavScreens

@Composable
fun MainScreen(
    viewModel: WalkViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine current screen
    val currentScreen = bottomNavScreens.find { screen ->
        currentDestination?.route?.contains(screen::class.simpleName ?: "") == true
    } ?: Screen.Dashboard

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "BeneWalker",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomNavScreens.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                navController.navigate(screen) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = { fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
            exitTransition = { fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
            popEnterTransition = { fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
            popExitTransition = { fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
        ) {
            composable<Screen.Dashboard> {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToStopwatch = {
                        navController.navigate(Screen.Stopwatch)
                    },
                    onNavigateToAnalytics = {
                        navController.navigate(Screen.Analytics)
                    }
                )
            }
            composable<Screen.Stopwatch> {
                StopwatchScreen(viewModel = viewModel)
            }
            composable<Screen.Analytics> {
                AnalyticsScreen(viewModel = viewModel)
            }
            composable<Screen.Settings> {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
