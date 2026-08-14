package com.envanter.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.envanter.app.data.Settings
import com.envanter.app.data.ThemeMode
import com.envanter.app.ui.AddEditScreen
import com.envanter.app.ui.AssistantScreen
import com.envanter.app.ui.BatchAddScreen
import com.envanter.app.ui.DashboardScreen
import com.envanter.app.ui.EnvanterTheme
import com.envanter.app.ui.HomesScreen
import com.envanter.app.ui.InventoryScreen
import com.envanter.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private var startVoice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Ekranın desteklediği en yüksek yenileme hızını (120Hz+) iste; OEM'ler varsayılan 60Hz'e kısabiliyor.
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }?.let { mode ->
            window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }
        startVoice = intent?.getBooleanExtra(EXTRA_VOICE, false) == true
        setContent {
            val context = LocalContext.current
            val themeMode by Settings.themeMode(context).collectAsState(initial = ThemeMode.SYSTEM)
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            EnvanterTheme(darkTheme = dark) {
                AppNav(startVoice = startVoice, onVoiceConsumed = { startVoice = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_VOICE, false)) startVoice = true
    }

    companion object {
        const val EXTRA_VOICE = "com.envanter.app.START_VOICE"
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppNav(startVoice: Boolean = false, onVoiceConsumed: () -> Unit = {}) {
    val nav: NavHostController = rememberNavController()
    val tabs = listOf(
        Tab("dashboard", "Ana Sayfa") { Icon(Icons.Filled.Home, null) },
        Tab("inventory", "Envanter") { Icon(Icons.Filled.Inventory2, null) },
        Tab("assistant", "Asistan") { Icon(Icons.Filled.AutoAwesome, null) },
        Tab("settings", "Ayarlar") { Icon(Icons.Filled.Settings, null) }
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentBase = currentRoute?.substringBefore("?")?.substringBefore("/")

    // Sesli ekleme widget'ından açıldıysa doğrudan ses ekranını başlat.
    LaunchedEffect(startVoice) {
        if (startVoice) {
            nav.navigate("edit?mode=voice")
            onVoiceConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentBase != "edit" && currentBase != "batch") {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentBase == tab.route,
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
            composable(
                "inventory?urgency={urgency}",
                arguments = listOf(navArgument("urgency") {
                    type = NavType.StringType; defaultValue = ""
                })
            ) { entry ->
                InventoryScreen(nav, entry.arguments?.getString("urgency") ?: "")
            }
            composable("assistant") { AssistantScreen() }
            composable("homes") { HomesScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable(
                "edit?mode={mode}",
                arguments = listOf(navArgument("mode") { defaultValue = "" })
            ) { entry ->
                AddEditScreen(nav, null, entry.arguments?.getString("mode") ?: "")
            }
            composable(
                "batch?mode={mode}",
                arguments = listOf(navArgument("mode") { defaultValue = "" })
            ) { entry ->
                BatchAddScreen(nav, entry.arguments?.getString("mode") ?: "")
            }
            composable("edit/{id}") { entry ->
                AddEditScreen(nav, entry.arguments?.getString("id"), "")
            }
        }
    }
}
