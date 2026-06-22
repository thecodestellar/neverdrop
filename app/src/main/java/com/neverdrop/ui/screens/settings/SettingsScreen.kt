package com.neverdrop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neverdrop.data.google.GoogleAuthManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    googleAuthManager: GoogleAuthManager,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModel(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Intelligence Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI Intelligence Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Chat capture, screenshot extraction, and your morning briefing use AI instead of pattern matching. A private on-device model is preferred when installed; the cloud (Claude) is used otherwise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable AI features")
                        Switch(
                            checked = uiState.aiEnabled,
                            onCheckedChange = viewModel::toggleAi
                        )
                    }

                    if (uiState.aiEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var apiKeyInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Anthropic API key") },
                            placeholder = {
                                Text(if (uiState.hasApiKey) "Key saved — enter to replace" else "sk-ant-...")
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.updateApiKey(apiKeyInput.trim())
                                    apiKeyInput = ""
                                },
                                enabled = apiKeyInput.isNotBlank()
                            ) { Text("Save key") }
                            if (uiState.hasApiKey) {
                                OutlinedButton(onClick = {
                                    viewModel.clearApiKey()
                                    apiKeyInput = ""
                                }) { Text("Remove key") }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (uiState.hasApiKey) "Cloud key saved."
                            else "No cloud key set. Without a key (and without an on-device model), NeverDrop uses local pattern matching.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.hasApiKey) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // On-device (private) model
                        Text(
                            "On-device model (private)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Run a Gemma model entirely on your phone — nothing leaves the device. Import a MediaPipe-compatible .task model file. Preferred over the cloud when installed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Prefer on-device when available")
                            Switch(
                                checked = uiState.onDeviceAiEnabled,
                                onCheckedChange = viewModel::toggleOnDeviceAi
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.isImportingModel) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Text(
                                    "  Importing model… (large files may take a while)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (uiState.modelInstalled) {
                            val sizeMb = uiState.modelSizeBytes / 1024.0 / 1024.0
                            Text(
                                "Model installed (${String.format("%.0f", sizeMb)} MB)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }) {
                                    Text("Replace")
                                }
                                OutlinedButton(onClick = viewModel::removeModel) {
                                    Text("Remove model")
                                }
                            }
                        } else {
                            Button(onClick = { modelPicker.launch(arrayOf("*/*")) }) {
                                Text("Import model file")
                            }
                        }

                        uiState.modelError?.let { err ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Import failed: $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Cloud Sync Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Cloud Sync (Supabase)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sync your tasks across devices via Supabase — hosted or self-hosted on your own server. Sign in with Google (above) first; the same account authenticates sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var urlInput by remember { mutableStateOf(uiState.supabaseUrl) }
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Project URL") },
                        placeholder = { Text("https://xyz.supabase.co") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    var anonKeyInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = anonKeyInput,
                        onValueChange = { anonKeyInput = it },
                        label = { Text("Anon (public) key") },
                        placeholder = {
                            Text(if (uiState.hasAnonKey) "Key saved — enter to replace" else "eyJ...")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.updateSupabaseUrl(urlInput.trim())
                            if (anonKeyInput.isNotBlank()) {
                                viewModel.updateSupabaseAnonKey(anonKeyInput.trim())
                                anonKeyInput = ""
                            }
                        },
                        enabled = urlInput.isNotBlank()
                    ) { Text("Save connection") }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable cloud sync")
                        Switch(
                            checked = uiState.cloudSyncEnabled,
                            onCheckedChange = viewModel::toggleCloudSync
                        )
                    }

                    if (uiState.cloudSyncEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::runCloudSyncNow,
                            enabled = !uiState.isCloudSyncing
                        ) {
                            if (uiState.isCloudSyncing) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                Text("  Syncing…")
                            } else {
                                Text("Sync now")
                            }
                        }
                    }

                    uiState.cloudSyncMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("Sync failed")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Morning Briefing Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Morning Briefing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable daily briefing")
                        Switch(
                            checked = uiState.briefingEnabled,
                            onCheckedChange = viewModel::toggleBriefing
                        )
                    }

                    if (uiState.briefingEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Briefing time: ${String.format("%02d:%02d", uiState.briefingHour, uiState.briefingMinute)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                viewModel.updateBriefingTime(7, 0)
                            }) { Text("7:00") }
                            OutlinedButton(onClick = {
                                viewModel.updateBriefingTime(8, 0)
                            }) { Text("8:00") }
                            OutlinedButton(onClick = {
                                viewModel.updateBriefingTime(9, 0)
                            }) { Text("9:00") }
                        }
                    }
                }
            }

            // Evening Review Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Evening Review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable evening review")
                        Switch(
                            checked = uiState.eveningReviewEnabled,
                            onCheckedChange = viewModel::toggleEveningReview
                        )
                    }

                    if (uiState.eveningReviewEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Review time: ${String.format("%02d:%02d", uiState.eveningReviewHour, uiState.eveningReviewMinute)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                viewModel.updateEveningReviewTime(20, 0)
                            }) { Text("8 PM") }
                            OutlinedButton(onClick = {
                                viewModel.updateEveningReviewTime(21, 0)
                            }) { Text("9 PM") }
                            OutlinedButton(onClick = {
                                viewModel.updateEveningReviewTime(22, 0)
                            }) { Text("10 PM") }
                        }
                    }
                }
            }

            // Weekly Intelligence Report Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Weekly Intelligence Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "AI-generated weekly insight: patterns, category analysis, relationship health, and actionable advice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable weekly report")
                        Switch(
                            checked = uiState.weeklyReportEnabled,
                            onCheckedChange = viewModel::toggleWeeklyReport
                        )
                    }
                }
            }

            // Notification Mining Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Notification Mining",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Scan incoming notifications for deliveries, appointments, bills, and travel updates. Auto-creates tasks from time-sensitive items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable notification mining")
                        Switch(
                            checked = uiState.notificationMiningEnabled,
                            onCheckedChange = viewModel::toggleNotificationMining
                        )
                    }
                    if (uiState.notificationMiningEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Requires notification access permission. Go to Settings > Apps > Special access > Notification access to grant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Google Account Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Gmail & Calendar Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.googleEmail != null) {
                        Text(
                            "Connected: ${uiState.googleEmail}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-sync every 6 hours")
                            Switch(
                                checked = uiState.autoSyncEnabled,
                                onCheckedChange = viewModel::toggleAutoSync
                            )
                        }

                        if (uiState.lastSyncTime > 0) {
                            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                            Text(
                                "Last sync: ${dateFormat.format(Date(uiState.lastSyncTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::triggerManualSync,
                                enabled = !uiState.isSyncing
                            ) {
                                Text(if (uiState.isSyncing) "Syncing..." else "Sync Now")
                            }
                            OutlinedButton(onClick = viewModel::signOut) {
                                Text("Sign Out")
                            }
                        }
                    } else {
                        Text(
                            "Connect your Google account to automatically import action items from Gmail and events from Calendar.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val result = googleAuthManager.signIn(context)
                                result.onSuccess { user ->
                                    viewModel.onGoogleSignInResult(user.email)
                                }
                            }
                        }) {
                            Text("Sign in with Google")
                        }
                    }
                }
            }

            // Notification Info Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "NeverDrop uses multi-tier notifications based on urgency scores:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Critical (80+) - High importance with vibration", style = MaterialTheme.typography.bodySmall)
                    Text("High (60-79) - High importance alerts", style = MaterialTheme.typography.bodySmall)
                    Text("Medium (40-59) - Default notifications", style = MaterialTheme.typography.bodySmall)
                    Text("Low (<40) - Silent reminders", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Deadline alerts check every 30 min. Snoozed tasks escalate after 3+ snoozes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
