package com.benewalker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.benewalker.app.ui.WalkViewModel
import com.benewalker.app.ui.screens.MainScreen
import com.benewalker.app.ui.theme.BeneWalkerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WalkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeneWalkerTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
