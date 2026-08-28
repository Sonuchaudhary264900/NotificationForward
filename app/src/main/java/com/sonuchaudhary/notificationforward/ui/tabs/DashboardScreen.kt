package com.sonuchaudhary.notificationforward.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonuchaudhary.notificationforward.settings.DeviceRole
import com.sonuchaudhary.notificationforward.settings.RoleStore

@Composable
fun DashboardScreen(roleStore: RoleStore) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when (roleStore.role) {
            DeviceRole.PARENT -> ParentDashboard(roleStore)
            DeviceRole.CHILD -> ChildDashboard(roleStore)
            DeviceRole.NONE -> Text("Set up your role first")
        }
    }
}

@Composable
private fun ParentDashboard(roleStore: RoleStore) {
    if (!roleStore.isPaired) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("No paired devices yet", fontWeight = FontWeight.SemiBold)
                Text(
                    "Go through pairing to connect a child's device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Paired Device", fontWeight = FontWeight.SemiBold)
                Text(
                    "Family: ${roleStore.familyId ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Status: ${roleStore.pairingStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (roleStore.pairingStatus == "ACTIVE")
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChildDashboard(roleStore: RoleStore) {
    if (!roleStore.isPaired) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Not paired yet", fontWeight = FontWeight.SemiBold)
                Text(
                    "Complete pairing with a parent device to enable monitoring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Monitoring Active", fontWeight = FontWeight.SemiBold)
                Text(
                    "A parent is monitoring this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (roleStore.consentAcceptedAt > 0) {
                    Text(
                        "Consent acknowledged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}