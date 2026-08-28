package com.sonuchaudhary.notificationforward

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sonuchaudhary.notificationforward.settings.SettingsStore
import com.sonuchaudhary.notificationforward.ui.nav.AppNavHost
import com.sonuchaudhary.notificationforward.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SettingsStore(this)

        setContent {
            AppTheme {
                AppNavHost(settingsStore = settingsStore)
            }
        }
    }
}
