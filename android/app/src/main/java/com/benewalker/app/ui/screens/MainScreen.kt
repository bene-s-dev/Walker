package com.benewalker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.navigation.Screen
import com.benewalker.app.ui.navigation.bottomNavScreens

fun getScreenIndex(route: String?): Int {
    if (route == null) return 0
    return when {
        route.contains("Dashboard") -> 0
        route.contains("Stopwatch") -> 1
        route.contains("Data") -> 2
        route.contains("Settings") -> 3
        else -> 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: WalkViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "BeneWalker",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menü"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Einstellungen") },
                            onClick = {
                                showMenu = false
                                navController.navigate(Screen.Settings) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Settings, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Garmin Sync") },
                            onClick = {
                                showMenu = false
                                viewModel.syncWithHealthConnect()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Sync, contentDescription = null)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomNavScreens.forEach { screen ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.route?.contains(screen::class.simpleName ?: "") == true
                    } == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
            enterTransition = {
                val initialIdx = getScreenIndex(initialState.destination.route)
                val targetIdx = getScreenIndex(targetState.destination.route)
                val direction = if (targetIdx >= initialIdx) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End
                slideIntoContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(280))
            },
            exitTransition = {
                val initialIdx = getScreenIndex(initialState.destination.route)
                val targetIdx = getScreenIndex(targetState.destination.route)
                val direction = if (targetIdx >= initialIdx) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End
                slideOutOfContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(tween(280))
            },
            popEnterTransition = {
                val initialIdx = getScreenIndex(initialState.destination.route)
                val targetIdx = getScreenIndex(targetState.destination.route)
                val direction = if (targetIdx >= initialIdx) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End
                slideIntoContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(280))
            },
            popExitTransition = {
                val initialIdx = getScreenIndex(initialState.destination.route)
                val targetIdx = getScreenIndex(targetState.destination.route)
                val direction = if (targetIdx >= initialIdx) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End
                slideOutOfContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(tween(280))
            }
        ) {
            composable<Screen.Dashboard> {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToData = {
                        navController.navigate(Screen.Data) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable<Screen.Stopwatch> {
                StopwatchScreen(viewModel = viewModel)
            }
            composable<Screen.Data> {
                DataScreen(viewModel = viewModel)
            }
            composable<Screen.Settings> {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
