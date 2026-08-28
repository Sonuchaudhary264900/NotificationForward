package com.sonuchaudhary.notificationforward.ui.tabs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sonuchaudhary.notificationforward.settings.DeviceRole
import com.sonuchaudhary.notificationforward.settings.RoleStore

@Composable
fun PermissionsScreen(roleStore: RoleStore) {
    val context = LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
    }
    var fileAccessGranted by remember {
        mutableStateOf(hasFileAccess(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        PermissionCard(
            title = "Notifications",
            description = "Required to send alerts and monitoring status",
            isGranted = notificationGranted,
            onRequest = {
                openAppSettings(context)
                notificationGranted = hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            }
        )

        if (roleStore.role == DeviceRole.CHILD) {
            PermissionCard(
                title = "File Access",
                description = "Required to backup call recordings",
                isGranted = fileAccessGranted,
                onRequest = {
                    openAppSettings(context)
                    fileAccessGranted = hasFileAccess(context)
                }
            )
        }

        if (roleStore.role == DeviceRole.PARENT) {
            PermissionCard(
                title = "Gallery",
                description = "View backed-up media from child's device",
                isGranted = fileAccessGranted,
                onRequest = {
                    openAppSettings(context)
                    fileAccessGranted = hasFileAccess(context)
                }
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Icon(
                        if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = if (isGranted) "Granted" else "Not granted",
                        modifier = Modifier.padding(start = 8.dp),
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (!isGranted) {
                Button(onClick = onRequest, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Grant")
                }
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun hasFileAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.fromParts("package", context.packageName, null)
    context.startActivity(intent)
}
