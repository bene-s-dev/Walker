package com.benewalker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.screens.MainScreen
import com.benewalker.app.ui.theme.BeneWalkerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WalkViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+ (required for Samsung Now Bar & Status Bar Live Chronometer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
