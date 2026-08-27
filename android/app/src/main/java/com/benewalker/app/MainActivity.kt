package com.benewalker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.screens.MainScreen
import com.benewalker.app.ui.theme.BeneWalkerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WalkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isDark = when (uiState.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            BeneWalkerTheme(
                darkTheme = isDark,
                dynamicColor = uiState.useDynamicColor
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-Sync upon opening or returning to the app
        viewModel.checkHealthConnectStatus()
        viewModel.syncWithHealthConnect(days = 14)
    }
}
