package com.sonuchaudhary.notificationforward.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonuchaudhary.notificationforward.ui.common.BackButton

private val CONSENT_POINTS = listOf(
    "A parent will be able to see this device's status once it's paired.",
    "This app's icon always stays visible — it can never be hidden from the home screen or app list.",
    "Whenever a paired parent is actively viewing this device's location, camera, or screen, a notification will say so on this device, and it can't be dismissed while that's happening.",
    "You (or whoever uses this device) should know this app is here and what it does."
)

/**
 * Hard requirement: a pairing code cannot be generated before this is accepted. This is the
 * child-device gate — see RoleStore.hasConsented and the nav graph ordering in AppNavHost.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        BackButton(onClick = onBack)
        Text(
            "Before you continue",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "This device is being set up as a monitored device.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(CONSENT_POINTS) { point ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(point, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text("I understand and agree")
        }
    }
}
