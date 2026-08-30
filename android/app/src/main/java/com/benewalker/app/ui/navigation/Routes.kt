package com.benewalker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface Screen {
    val index: Int
    val title: String
    val selectedIcon: ImageVector
    val unselectedIcon: ImageVector

    @Serializable
    data object Dashboard : Screen {
        override val index = 0
        override val title = "Übersicht"
        override val selectedIcon = Icons.Filled.DirectionsWalk
        override val unselectedIcon = Icons.Outlined.DirectionsWalk
    }

    @Serializable
    data object Stopwatch : Screen {
        override val index = 1
        override val title = "Training"
        override val selectedIcon = Icons.Filled.Timer
        override val unselectedIcon = Icons.Outlined.Timer
    }

    @Serializable
    data object Data : Screen {
        override val index = 2
        override val title = "Daten"
        override val selectedIcon = Icons.Filled.EditNote
        override val unselectedIcon = Icons.Outlined.EditNote
    }

    @Serializable
    data object Settings : Screen {
        override val index = 3
        override val title = "Einstellungen"
        override val selectedIcon = Icons.Filled.Settings
        override val unselectedIcon = Icons.Outlined.Settings
    }
}

// 3 Tabs in Bottom Navigation Bar (Settings is in TopAppBar menu)
val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Stopwatch,
    Screen.Data
)
