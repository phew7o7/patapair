package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchStatus
import com.example.ui.screens.AiPairsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.LandRecordListScreen
import com.example.ui.screens.LocationGroupsScreen
import com.example.ui.screens.ManualReviewScreen
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.PataPairTheme
import com.example.viewmodel.PataPairViewModel

enum class AppScreen {
    DASHBOARD,
    AI_PAIRS,
    LOCATION_GROUPS,
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

    val matchPairs by viewModel.matchPairs.collectAsState()
    val locationGroups by viewModel.locationGroups.collectAsState()

    val activePairsCount = remember(matchPairs) {
        matchPairs.count { it.status != MatchStatus.REJECTED }
    }
    val locationCount = remember(locationGroups) {
        locationGroups.count { it.locationKey != "Unassigned / General" }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                // 1. Dashboard
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Home", fontSize = 11.sp) },
                    selected = currentScreen == AppScreen.DASHBOARD,
                    onClick = { currentScreen = AppScreen.DASHBOARD },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = ForestGreen.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_home")
                )

                // 2. AI Pairs (New dedicated section)
                NavigationBarItem(
                    icon = {
                        if (activePairsCount > 0) {
                            BadgedBox(badge = {
                                Badge(containerColor = ForestGreen) {
                                    Text("$activePairsCount", color = Color.White)
                                }
                            }) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Pairs")
                            }
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Pairs")
                        }
                    },
                    label = { Text("AI Pairs", fontSize = 11.sp) },
                    selected = currentScreen == AppScreen.AI_PAIRS,
                    onClick = { currentScreen = AppScreen.AI_PAIRS },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = ForestGreen.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_ai_pairs")
                )

                // 3. Location Groups (New dedicated section)
                NavigationBarItem(
                    icon = {
                        if (locationCount > 0) {
                            BadgedBox(badge = {
                                Badge(containerColor = Color(0xFFC77700)) {
                                    Text("$locationCount", color = Color.White)
                                }
                            }) {
                                Icon(Icons.Default.Place, contentDescription = "By Location")
                            }
                        } else {
                            Icon(Icons.Default.Place, contentDescription = "By Location")
                        }
                    },
                    label = { Text("Locations", fontSize = 11.sp) },
                    selected = currentScreen == AppScreen.LOCATION_GROUPS,
                    onClick = { currentScreen = AppScreen.LOCATION_GROUPS },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFC77700),
                        selectedTextColor = Color(0xFFC77700),
                        indicatorColor = Color(0xFFC77700).copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_locations")
                )

                // 4. Documents List
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Documents") },
                    label = { Text("Docs", fontSize = 11.sp) },
                    selected = currentScreen == AppScreen.LAND_RECORDS,
                    onClick = { currentScreen = AppScreen.LAND_RECORDS },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = ForestGreen.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_docs")
                )

                // 5. Manual Review
                NavigationBarItem(
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = "Review") },
                    label = { Text("Review", fontSize = 11.sp) },
                    selected = currentScreen == AppScreen.MANUAL_REVIEW,
                    onClick = { currentScreen = AppScreen.MANUAL_REVIEW },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = ForestGreen.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_review")
                )

                // 6. Export
                NavigationBarItem(
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = "Export") },
                    label = { Text("Export", fontSize = 11.sp) },
                    selected = currentScreen == AppScreen.EXPORT,
                    onClick = { currentScreen = AppScreen.EXPORT },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = ForestGreen.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_export")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToAiPairs = { currentScreen = AppScreen.AI_PAIRS },
                        onNavigateToLocationGroups = { currentScreen = AppScreen.LOCATION_GROUPS },
                        onNavigateToLandRecords = { currentScreen = AppScreen.LAND_RECORDS },
                        onNavigateToManualReview = { currentScreen = AppScreen.MANUAL_REVIEW },
                        onNavigateToExport = { currentScreen = AppScreen.EXPORT }
                    )
                }
                AppScreen.AI_PAIRS -> {
                    BackHandler {
                        currentScreen = AppScreen.DASHBOARD
                    }
                    AiPairsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                        onNavigateToLocations = { currentScreen = AppScreen.LOCATION_GROUPS }
                    )
                }
                AppScreen.LOCATION_GROUPS -> {
                    BackHandler {
                        currentScreen = AppScreen.DASHBOARD
                    }
                    LocationGroupsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                        onNavigateToAiPairs = { currentScreen = AppScreen.AI_PAIRS }
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
    }
}
