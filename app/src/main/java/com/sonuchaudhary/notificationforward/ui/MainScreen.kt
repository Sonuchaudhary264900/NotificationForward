package com.sonuchaudhary.notificationforward.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.sonuchaudhary.notificationforward.settings.DeviceRole
import com.sonuchaudhary.notificationforward.settings.RoleStore
import com.sonuchaudhary.notificationforward.settings.SettingsStore
import com.sonuchaudhary.notificationforward.ui.tabs.BackupScreen
import com.sonuchaudhary.notificationforward.ui.tabs.DashboardScreen
import com.sonuchaudhary.notificationforward.ui.tabs.HomeScreen
import com.sonuchaudhary.notificationforward.ui.tabs.PermissionsScreen

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    BACKUP("Backup", Icons.Filled.Backup),
    PERMISSIONS("Permissions", Icons.Filled.Lock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val roleStore = remember { RoleStore(context) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("XSPY") },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }
        }
    ) { _ ->
        when (selectedTab) {
            AppTab.HOME -> {
                if (roleStore.role == DeviceRole.CHILD) {
                    HomeScreen(settingsStore, roleStore)
                } else {
                    DashboardScreen(roleStore)
                }
            }
            AppTab.DASHBOARD -> DashboardScreen(roleStore)
            AppTab.BACKUP -> BackupScreen(settingsStore)
            AppTab.PERMISSIONS -> PermissionsScreen(roleStore)
        }
    }
}
