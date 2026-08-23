package com.sonuchaudhary.notificationforward

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonuchaudhary.notificationforward.data.NotificationRepository
import com.sonuchaudhary.notificationforward.data.QueueItem
import com.sonuchaudhary.notificationforward.data.QueueStats
import com.sonuchaudhary.notificationforward.data.QueueStatus
import com.sonuchaudhary.notificationforward.data.RecordingItem
import com.sonuchaudhary.notificationforward.data.RecordingRepository
import com.sonuchaudhary.notificationforward.data.RecordingStats
import com.sonuchaudhary.notificationforward.network.WebhookClient
import com.sonuchaudhary.notificationforward.settings.AppSettings
import com.sonuchaudhary.notificationforward.settings.AuthMode
import com.sonuchaudhary.notificationforward.settings.FilterMode
import com.sonuchaudhary.notificationforward.settings.SettingsStore
import com.sonuchaudhary.notificationforward.ui.theme.AppTheme
import com.sonuchaudhary.notificationforward.worker.WorkerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    WEBHOOK("Webhook", Icons.Filled.Link),
    FILTER("Filter", Icons.Filled.Tune),
    QUEUE("Queue", Icons.AutoMirrored.Filled.List),
    RECORDINGS("Recordings", Icons.Filled.Mic)
}

private data class UiRecordingSettings(
    val enabled: Boolean,
    val botToken: String,
    val chatId: String,
    val folderPath: String
)

private data class UiSettings(
    val webhookUrl: String,
    val webhookMethod: String,
    val forwardingEnabled: Boolean,
    val filterMode: FilterMode,
    val filterPackagesRaw: String,
    val authMode: AuthMode,
    val bearerToken: String,
    val customHeadersRaw: String,
    val queryParamsRaw: String,
    val payloadTemplateRaw: String,
    val maxRetriesRaw: String,
    val batchSizeRaw: String
)

private fun AppSettings.toUiSettings(): UiSettings {
    return UiSettings(
        webhookUrl = webhookUrl,
        webhookMethod = webhookMethod,
        forwardingEnabled = forwardingEnabled,
        filterMode = filterMode,
        filterPackagesRaw = filterPackages.joinToString("\n"),
        authMode = authMode,
        bearerToken = bearerToken,
        customHeadersRaw = customHeadersRaw,
        queryParamsRaw = queryParamsRaw,
        payloadTemplateRaw = payloadTemplateRaw,
        maxRetriesRaw = maxRetries.toString(),
        batchSizeRaw = batchSize.toString()
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SettingsStore(this)

        setContent {
            AppTheme {
                MainScreen(settingsStore = settingsStore)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { NotificationRepository(context) }
    val recordingRepository = remember { RecordingRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var uiSettings by remember { mutableStateOf(settingsStore.readAll().toUiSettings()) }
    var uiRecordingSettings by remember {
        mutableStateOf(
            UiRecordingSettings(
                enabled = settingsStore.recordingBackupEnabled,
                botToken = settingsStore.recordingBotToken,
                chatId = settingsStore.recordingChatId,
                folderPath = settingsStore.recordingFolderPath
            )
        )
    }

    val stats by repository.observeStats().collectAsState(
        initial = QueueStats(0, 0, 0, 0)
    )
    val recent by repository.observeRecent(30).collectAsState(initial = emptyList())
    val recordingStats by recordingRepository.observeStats().collectAsState(
        initial = RecordingStats(0, 0, 0, 0)
    )
    val recordingRecent by recordingRepository.observeRecent(30).collectAsState(initial = emptyList())

    val requestLegacyStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notification Forward") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.HOME -> HomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                stats = stats,
                onRetryAllFailed = {
                    scope.launch {
                        repository.retryAllFailed()
                        WorkerScheduler.enqueueImmediate(context)
                        snackbarHostState.showSnackbar("All failed items re-queued")
                    }
                }
            )

            AppTab.WEBHOOK -> WebhookScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                uiSettings = uiSettings,
                onSettingsChange = { uiSettings = it },
                onSave = {
                    saveSettings(settingsStore, uiSettings)
                    WorkerScheduler.enqueueImmediate(context)
                    scope.launch { snackbarHostState.showSnackbar("Webhook settings saved") }
                },
                onTestWebhook = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            WebhookClient().send(
                                url = uiSettings.webhookUrl,
                                method = uiSettings.webhookMethod,
                                headers = buildHeadersPreview(
                                    authMode = uiSettings.authMode,
                                    token = uiSettings.bearerToken,
                                    customHeadersRaw = uiSettings.customHeadersRaw
                                ),
                                queryParams = parseKeyValuePairs(uiSettings.queryParamsRaw),
                                payloadTemplate = uiSettings.payloadTemplateRaw,
                                item = QueueItem(
                                    packageName = "com.test.package",
                                    appName = "Webhook Test",
                                    title = "Test Notification",
                                    text = "This is a test payload from Notification Forward",
                                    postedAt = System.currentTimeMillis(),
                                    notificationKey = "test-${System.currentTimeMillis()}"
                                ),
                                deviceId = "test-device"
                            )
                        }
                        snackbarHostState.showSnackbar(
                            if (result.success) "Webhook test success ✓" else "Webhook test failed: ${result.message}"
                        )
                    }
                }
            )

            AppTab.FILTER -> FilterScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                uiSettings = uiSettings,
                onSettingsChange = { uiSettings = it },
                onSave = {
                    saveSettings(settingsStore, uiSettings)
                    scope.launch { snackbarHostState.showSnackbar("Filter & retry settings saved") }
                }
            )

            AppTab.QUEUE -> QueueScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                recent = recent,
                onDeleteItem = { itemId ->
                    scope.launch {
                        repository.deleteQueueItem(itemId)
                        snackbarHostState.showSnackbar("Queue item deleted")
                    }
                },
                onClearQueue = {
                    scope.launch {
                        repository.clearQueue()
                        snackbarHostState.showSnackbar("Queue cleared")
                    }
                }
            )

            AppTab.RECORDINGS -> RecordingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                uiRecordingSettings = uiRecordingSettings,
                onSettingsChange = { uiRecordingSettings = it },
                onSave = {
                    settingsStore.recordingBackupEnabled = uiRecordingSettings.enabled
                    settingsStore.recordingBotToken = uiRecordingSettings.botToken
                    settingsStore.recordingChatId = uiRecordingSettings.chatId
                    settingsStore.recordingFolderPath = uiRecordingSettings.folderPath
                    WorkerScheduler.enqueueRecordingScanNow(context)
                    scope.launch { snackbarHostState.showSnackbar("Recording backup settings saved") }
                },
                onScanNow = {
                    WorkerScheduler.enqueueRecordingScanNow(context)
                    scope.launch { snackbarHostState.showSnackbar("Scanning for new recordings…") }
                },
                onGrantAccess = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    } else {
                        requestLegacyStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                recordingStats = recordingStats,
                recordingRecent = recordingRecent,
                onDeleteRecordingItem = { itemId ->
                    scope.launch {
                        recordingRepository.deleteItem(itemId)
                        snackbarHostState.showSnackbar("Recording item deleted")
                    }
                },
                onClearRecordings = {
                    scope.launch {
                        recordingRepository.clearAll()
                        snackbarHostState.showSnackbar("Recording queue cleared")
                    }
                },
                onRetryAllFailedRecordings = {
                    scope.launch {
                        recordingRepository.retryAllFailed()
                        WorkerScheduler.enqueueRecordingScanNow(context)
                        snackbarHostState.showSnackbar("All failed recordings re-queued")
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        WorkerScheduler.ensurePeriodic(context)
        WorkerScheduler.ensureRecordingPeriodic(context)
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    stats: QueueStats,
    onRetryAllFailed: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Service Status", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Notification Access")
                        val enabled = isNotificationListenerEnabled(context)
                        StatusBadge(
                            text = if (enabled) "Granted" else "Not granted",
                            success = enabled
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Battery Optimization")
                        val unrestricted = isBatteryUnrestricted(context)
                        StatusBadge(
                            text = if (unrestricted) "No restriction" else "Restricted",
                            success = unrestricted
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    ) {
                        Text("Open Access Settings")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openBatterySettings(context) }
                    ) {
                        Text("Open Battery Settings")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { WorkerScheduler.enqueueImmediate(context) }
                    ) {
                        Text("Sync Queue Now")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Queue Summary", fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Pending",
                            value = stats.pendingCount.toString(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Sending",
                            value = stats.sendingCount.toString(),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Sent",
                            value = stats.sentCount.toString(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Failed",
                            value = stats.failedCount.toString(),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (stats.failedCount > 0) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRetryAllFailed,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Retry All Failed (${stats.failedCount})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebhookScreen(
    modifier: Modifier,
    uiSettings: UiSettings,
    onSettingsChange: (UiSettings) -> Unit,
    onSave: () -> Unit,
    onTestWebhook: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Webhook Settings", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable forwarding")
                        Switch(
                            checked = uiSettings.forwardingEnabled,
                            onCheckedChange = { onSettingsChange(uiSettings.copy(forwardingEnabled = it)) }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiSettings.webhookUrl,
                        onValueChange = { onSettingsChange(uiSettings.copy(webhookUrl = it)) },
                        label = { Text("Webhook URL") },
                        singleLine = true
                    )

                    DropdownSelector(
                        label = "HTTP method",
                        value = uiSettings.webhookMethod,
                        options = listOf("GET", "POST", "PUT", "PATCH"),
                        onSelected = {
                            onSettingsChange(uiSettings.copy(webhookMethod = it))
                        }
                    )

                    DropdownSelector(
                        label = "Auth mode",
                        value = uiSettings.authMode.name,
                        options = AuthMode.entries.map { it.name },
                        onSelected = {
                            onSettingsChange(uiSettings.copy(authMode = AuthMode.valueOf(it)))
                        }
                    )

                    if (uiSettings.authMode == AuthMode.BEARER) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiSettings.bearerToken,
                            onValueChange = { onSettingsChange(uiSettings.copy(bearerToken = it)) },
                            label = { Text("Bearer token") }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        value = uiSettings.customHeadersRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(customHeadersRaw = it)) },
                        label = { Text("Custom headers (Key: Value per line)") }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        value = uiSettings.queryParamsRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(queryParamsRaw = it)) },
                        label = { Text("Query params (key=value per line)") }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        value = uiSettings.payloadTemplateRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(payloadTemplateRaw = it)) },
                        label = { Text("Payload template (JSON with {title} {text} etc.)") }
                    )

                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                        Text("Save Webhook Settings")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onTestWebhook) {
                        Text("Test Webhook")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterScreen(
    modifier: Modifier,
    uiSettings: UiSettings,
    onSettingsChange: (UiSettings) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Filter & Retry", fontWeight = FontWeight.SemiBold)

                    DropdownSelector(
                        label = "Filter mode",
                        value = uiSettings.filterMode.name,
                        options = FilterMode.entries.map { it.name },
                        onSelected = {
                            onSettingsChange(uiSettings.copy(filterMode = FilterMode.valueOf(it)))
                        }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        value = uiSettings.filterPackagesRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(filterPackagesRaw = it)) },
                        label = { Text("Packages list (comma/newline)") }
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiSettings.maxRetriesRaw,
                        onValueChange = {
                            onSettingsChange(uiSettings.copy(maxRetriesRaw = it.filter { c -> c.isDigit() }))
                        },
                        label = { Text("Max retries") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiSettings.batchSizeRaw,
                        onValueChange = {
                            onSettingsChange(uiSettings.copy(batchSizeRaw = it.filter { c -> c.isDigit() }))
                        },
                        label = { Text("Batch size") },
                        singleLine = true
                    )

                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                        Text("Save Filter & Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueScreen(
    modifier: Modifier,
    recent: List<QueueItem>,
    onDeleteItem: (Long) -> Unit,
    onClearQueue: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recent Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onClearQueue,
                        enabled = recent.isNotEmpty()
                    ) {
                        Text("Clear All Queue")
                    }
                }
            }
        }
        items(recent) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.appName, fontWeight = FontWeight.SemiBold)
                        QueueStatusBadge(status = item.status)
                    }
                    Text(item.title.ifBlank { "(no title)" })
                    Text(item.text.ifBlank { "(no text)" })
                    Text(item.packageName, style = MaterialTheme.typography.bodySmall)
                    Text("Attempt: ${item.attemptCount}", style = MaterialTheme.typography.bodySmall)
                    if (!item.lastError.isNullOrBlank()) {
                        Text(
                            "Err: ${item.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDeleteItem(item.id) }
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun RecordingScreen(
    modifier: Modifier,
    uiRecordingSettings: UiRecordingSettings,
    onSettingsChange: (UiRecordingSettings) -> Unit,
    onSave: () -> Unit,
    onScanNow: () -> Unit,
    onGrantAccess: () -> Unit,
    recordingStats: RecordingStats,
    recordingRecent: List<RecordingItem>,
    onDeleteRecordingItem: (Long) -> Unit,
    onClearRecordings: () -> Unit,
    onRetryAllFailedRecordings: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Call Recording Backup", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Automatically uploads new call recordings to a Telegram chat as they're recorded. " +
                            "Existing recordings are never touched — only ones made after this is enabled.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable backup")
                        Switch(
                            checked = uiRecordingSettings.enabled,
                            onCheckedChange = { onSettingsChange(uiRecordingSettings.copy(enabled = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("All Files Access")
                        val granted = isAllFilesAccessGranted(context)
                        StatusBadge(text = if (granted) "Granted" else "Not granted", success = granted)
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onGrantAccess) {
                        Text("Grant Storage Access")
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiRecordingSettings.botToken,
                        onValueChange = { onSettingsChange(uiRecordingSettings.copy(botToken = it)) },
                        label = { Text("Telegram bot token") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiRecordingSettings.chatId,
                        onValueChange = { onSettingsChange(uiRecordingSettings.copy(chatId = it)) },
                        label = { Text("Telegram chat ID") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiRecordingSettings.folderPath,
                        onValueChange = { onSettingsChange(uiRecordingSettings.copy(folderPath = it)) },
                        label = { Text("Recording folder path") },
                        singleLine = true
                    )

                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                        Text("Save Recording Settings")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onScanNow) {
                        Text("Scan Now")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Upload Summary", fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Pending",
                            value = recordingStats.pendingCount.toString(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Sent",
                            value = recordingStats.sentCount.toString(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Failed",
                            value = recordingStats.failedCount.toString(),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (recordingStats.failedCount > 0) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRetryAllFailedRecordings,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Retry All Failed (${recordingStats.failedCount})")
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onClearRecordings,
                        enabled = recordingRecent.isNotEmpty()
                    ) {
                        Text("Clear All")
                    }
                }
            }
        }

        items(recordingRecent) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.fileName, fontWeight = FontWeight.SemiBold)
                        QueueStatusBadge(status = item.status)
                    }
                    Text("${item.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    Text("Attempt: ${item.attemptCount}", style = MaterialTheme.typography.bodySmall)
                    if (!item.lastError.isNullOrBlank()) {
                        Text(
                            "Err: ${item.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDeleteRecordingItem(item.id) }
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, success: Boolean) {
    val container = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun QueueStatusBadge(status: QueueStatus) {
    val (container, content) = when (status) {
        QueueStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        QueueStatus.SENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        QueueStatus.SENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        QueueStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun QueueStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun saveSettings(settingsStore: SettingsStore, uiSettings: UiSettings) {
    settingsStore.webhookUrl = uiSettings.webhookUrl
    settingsStore.webhookMethod = uiSettings.webhookMethod
    settingsStore.forwardingEnabled = uiSettings.forwardingEnabled
    settingsStore.filterMode = uiSettings.filterMode
    settingsStore.filterPackages = SettingsStore.parsePackages(uiSettings.filterPackagesRaw)
    settingsStore.authMode = uiSettings.authMode
    settingsStore.bearerToken = uiSettings.bearerToken
    settingsStore.customHeadersRaw = uiSettings.customHeadersRaw
    settingsStore.queryParamsRaw = uiSettings.queryParamsRaw
    settingsStore.payloadTemplateRaw = uiSettings.payloadTemplateRaw
    settingsStore.maxRetries = uiSettings.maxRetriesRaw.toIntOrNull() ?: 10
    settingsStore.batchSize = uiSettings.batchSizeRaw.toIntOrNull() ?: 20
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (enabled.isNullOrBlank()) {
        return false
    }
    val target = ComponentName(context, com.sonuchaudhary.notificationforward.service.AppNotificationListenerService::class.java)
    return enabled.contains(target.flattenToString())
}

private fun parseKeyValuePairs(raw: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    raw.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        val idx = trimmed.indexOf('=')
        if (idx > 0) {
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    }
    return map
}

private fun buildHeadersPreview(
    authMode: AuthMode,
    token: String,
    customHeadersRaw: String
): Map<String, String> {
    val headers = linkedMapOf("Content-Type" to "application/json")
    if (authMode == AuthMode.BEARER && token.isNotBlank()) {
        headers["Authorization"] = "Bearer $token"
    }
    customHeadersRaw.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.contains(':')) {
            val idx = trimmed.indexOf(':')
            headers[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
        }
    }
    return headers
}

private fun openBatterySettings(context: Context) {
    val primaryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val fallbackIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )

    runCatching { context.startActivity(primaryIntent) }
        .onFailure { context.startActivity(fallbackIntent) }
}

private fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun isAllFilesAccessGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
