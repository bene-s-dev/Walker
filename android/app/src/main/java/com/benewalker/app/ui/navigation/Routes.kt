package com.benewalker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface Screen {
    val title: String
    val selectedIcon: ImageVector
    val unselectedIcon: ImageVector

    @Serializable
    data object Dashboard : Screen {
        override val title = "Tracker"
        override val selectedIcon = Icons.Filled.DirectionsWalk
        override val unselectedIcon = Icons.Outlined.DirectionsWalk
    }

    @Serializable
    data object Stopwatch : Screen {
        override val title = "Stoppuhr"
        override val selectedIcon = Icons.Filled.Timer
        override val unselectedIcon = Icons.Outlined.Timer
    }

    @Serializable
    data object Analytics : Screen {
        override val title = "Statistik"
        override val selectedIcon = Icons.Filled.Insights
        override val unselectedIcon = Icons.Outlined.Insights
    }

    @Serializable
    data object Settings : Screen {
        override val title = "Einstellungen"
        override val selectedIcon = Icons.Filled.Settings
        override val unselectedIcon = Icons.Outlined.Settings
    }
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Stopwatch,
    Screen.Analytics,
    Screen.Settings
)
