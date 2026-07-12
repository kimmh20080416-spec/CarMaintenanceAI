package com.carai.maintenance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carai.maintenance.ui.screens.MaintenanceScreen
import com.carai.maintenance.ui.screens.SoundDiagnosisScreen
import com.carai.maintenance.ui.theme.CarMaintenanceAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarMaintenanceAITheme {
                AppRoot()
            }
        }
    }
}

private sealed class Dest(val route: String, val label: String) {
    data object Sound : Dest("sound", "소리 진단")
    data object Maintenance : Dest("maintenance", "정비 추천")
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = Color.White,
        bottomBar = { BottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Sound.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Sound.route) { SoundDiagnosisScreen() }
            composable(Dest.Maintenance.route) { MaintenanceScreen() }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val items = listOf(Dest.Sound, Dest.Maintenance)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(containerColor = Color.White, contentColor = Color.Black) {
        items.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (dest is Dest.Sound) Icons.Filled.GraphicEq else Icons.Filled.Build,
                        contentDescription = dest.label
                    )
                },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.Black,
                    indicatorColor = Color.Black,
                    unselectedIconColor = Color(0xFF9E9E9E),
                    unselectedTextColor = Color(0xFF9E9E9E)
                )
            )
        }
    }
}
