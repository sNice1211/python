package com.nexradwx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexradwx.app.ui.radar.RadarScreen
import com.nexradwx.app.ui.stations.StationsScreen
import com.nexradwx.app.ui.theme.NexradWxTheme

private const val ROUTE_RADAR = "radar"
private const val ROUTE_STATIONS = "stations"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexradWxTheme {
                NexradWxApp()
            }
        }
    }
}

@Composable
private fun NexradWxApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == ROUTE_RADAR } == true,
                    onClick = { navigateSingleTop(navController, ROUTE_RADAR) },
                    icon = { Icon(Icons.Filled.Radar, contentDescription = null) },
                    label = { Text("Radar") },
                )
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == ROUTE_STATIONS } == true,
                    onClick = { navigateSingleTop(navController, ROUTE_STATIONS) },
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                    label = { Text("Stations") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_RADAR,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_RADAR) { RadarScreen() }
            composable(ROUTE_STATIONS) { StationsScreen() }
        }
    }
}

private fun navigateSingleTop(navController: androidx.navigation.NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
