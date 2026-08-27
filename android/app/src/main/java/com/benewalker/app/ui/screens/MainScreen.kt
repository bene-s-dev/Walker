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
import androidx.compose.ui.Alignment
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BeneWalker",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menü",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onBackground
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
                                    val state = viewModel.uiState.value
                                    if (state.hcStatus == com.benewalker.app.ui.HcStatus.UNAVAILABLE) {
                                        android.widget.Toast.makeText(context, "Health Connect ist auf diesem Gerät nicht verfügbar", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (state.hcStatus == com.benewalker.app.ui.HcStatus.PERMISSION_NEEDED) {
                                        android.widget.Toast.makeText(context, "Health Connect Berechtigungen fehlen – bitte in Einstellungen aktivieren", android.widget.Toast.LENGTH_LONG).show()
                                        navController.navigate(Screen.Settings) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Garmin Sync gestartet...", android.widget.Toast.LENGTH_SHORT).show()
                                        viewModel.syncWithHealthConnect(days = 30) { count ->
                                            if (count > 0) {
                                                android.widget.Toast.makeText(context, "✓ $count Gehzeiten von Garmin aktualisiert", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Alles aktuell (keine neuen Garmin-Gehzeiten)", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Sync, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
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
