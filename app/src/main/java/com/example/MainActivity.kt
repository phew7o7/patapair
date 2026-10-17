package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.LandRecordListScreen
import com.example.ui.screens.ManualReviewScreen
import com.example.ui.theme.PataPairTheme
import com.example.viewmodel.PataPairViewModel

enum class AppScreen {
    DASHBOARD,
    LAND_RECORDS,
    MANUAL_REVIEW,
    EXPORT
}

class MainActivity : ComponentActivity() {

    private val viewModel: PataPairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PataPairTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PataPairApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PataPairApp(viewModel: PataPairViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    when (currentScreen) {
        AppScreen.DASHBOARD -> {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToLandRecords = { currentScreen = AppScreen.LAND_RECORDS },
                onNavigateToManualReview = { currentScreen = AppScreen.MANUAL_REVIEW },
                onNavigateToExport = { currentScreen = AppScreen.EXPORT }
            )
        }
        AppScreen.LAND_RECORDS -> {
            BackHandler {
                currentScreen = AppScreen.DASHBOARD
            }
            LandRecordListScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = AppScreen.DASHBOARD }
            )
        }
        AppScreen.MANUAL_REVIEW -> {
            BackHandler {
                currentScreen = AppScreen.DASHBOARD
            }
            ManualReviewScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = AppScreen.DASHBOARD }
            )
        }
        AppScreen.EXPORT -> {
            BackHandler {
                currentScreen = AppScreen.DASHBOARD
            }
            ExportScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = AppScreen.DASHBOARD }
            )
        }
    }
}
