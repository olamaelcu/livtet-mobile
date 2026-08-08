package net.olamaelcu.livtet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.olamaelcu.livtet.account.AccountScreen
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.settings.SettingsScreen
import net.olamaelcu.livtet.settings.ThemeManager

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by
                ThemeManager.mode(this).collectAsState(initial = ThemeManager.Mode.SYSTEM)
            val isDark =
                when (themeMode) {
                    ThemeManager.Mode.SYSTEM -> isSystemInDarkTheme()
                    ThemeManager.Mode.LIGHT -> false
                    ThemeManager.Mode.DARK -> true
                }
            LivtetTheme(darkTheme = isDark) { DashboardNavHost() }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val iconId: Int?,
    val vectorIcon: ImageVector?,
)

private val bottomNavItems =
    listOf(
        BottomNavItem("dashboard", "Dashboard", null, Icons.Default.Home),
        BottomNavItem("library", "Library", null, Icons.Default.MenuBook),
        BottomNavItem("account", "Account", null, Icons.Default.Person),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            // Top-right action button. The gear navigates to the
            // Settings screen on every tab — the user can reach
            // device-pairing and plugin management from anywhere.
            TopAppBar(
                title = { Text("Livtet") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomNavItems.forEach { item ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        modifier =
                            Modifier.semantics(mergeDescendants = true) {
                                role = Role.Tab
                                this.selected = selected
                            },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.vectorIcon!!,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onNavigateToLibrary = {
                        navController.navigate("library") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("library") { LibraryScreen() }
            composable("account") { AccountScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
