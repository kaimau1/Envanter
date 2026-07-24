package com.envanter.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.envanter.app.ui.AddEditScreen
import com.envanter.app.ui.AssistantScreen
import com.envanter.app.ui.DashboardScreen
import com.envanter.app.ui.EnvanterTheme
import com.envanter.app.ui.InventoryScreen
import com.envanter.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            EnvanterTheme { AppNav() }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppNav() {
    val nav: NavHostController = rememberNavController()
    val tabs = listOf(
        Tab("dashboard", "Ana Sayfa") { Icon(Icons.Filled.Home, null) },
        Tab("inventory", "Envanter") { Icon(Icons.Filled.Inventory2, null) },
        Tab("assistant", "Asistan") { Icon(Icons.Filled.AutoAwesome, null) },
        Tab("settings", "Ayarlar") { Icon(Icons.Filled.Settings, null) }
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute?.startsWith("edit") != true) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("dashboard") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(nav) }
            composable("inventory") { InventoryScreen(nav) }
            composable("assistant") { AssistantScreen() }
            composable("settings") { SettingsScreen() }
            composable(
                "edit?mode={mode}",
                arguments = listOf(navArgument("mode") { defaultValue = "" })
            ) { entry ->
                AddEditScreen(nav, null, entry.arguments?.getString("mode") ?: "")
            }
            composable("edit/{id}") { entry ->
                AddEditScreen(nav, entry.arguments?.getString("id"), "")
            }
        }
    }
}
