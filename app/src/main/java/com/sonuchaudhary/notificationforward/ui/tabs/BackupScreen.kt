package com.sonuchaudhary.notificationforward.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonuchaudhary.notificationforward.settings.SettingsStore

@Composable
fun BackupScreen(settingsStore: SettingsStore) {
    var backupEnabled by remember { mutableStateOf(settingsStore.recordingBackupEnabled) }
    var retentionDays by remember { mutableStateOf(settingsStore.dataRetentionDays) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Backup & Storage",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto-Backup Enabled", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Automatically backup call recordings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backupEnabled,
                    onCheckedChange = {
                        backupEnabled = it
                        settingsStore.recordingBackupEnabled = it
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data Retention", fontWeight = FontWeight.SemiBold)
                Text(
                    "Backed-up data automatically deleted after $retentionDays days (unless pinned)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            retentionDays = 90
                            settingsStore.dataRetentionDays = 90
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("3mo")
                    }
                    Button(
                        onClick = {
                            retentionDays = 180
                            settingsStore.dataRetentionDays = 180
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("6mo")
                    }
                    Button(
                        onClick = {
                            retentionDays = 365
                            settingsStore.dataRetentionDays = 365
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("1yr")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backed-up Items", fontWeight = FontWeight.SemiBold)
                Text(
                    "No backed-up recordings yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
