package com.neverdrop.ui.screens.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.TaskPriority

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Speech recognizer
    val speechHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = viewModel::onVoiceResult,
            onPartialResult = viewModel::onVoicePartialResult,
            onListeningStateChanged = viewModel::onListeningStateChanged,
            onError = viewModel::onVoiceError
        )
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.destroy() }
    }

    // Permission launcher
    var pendingField: VoiceTargetField? = null
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingField != null) {
            viewModel.startVoiceInput(pendingField!!)
            speechHelper.startListening()
        }
    }

    fun startVoice(field: VoiceTargetField) {
        if (!speechHelper.isAvailable) {
            viewModel.onVoiceError("Speech recognition not available on this device")
            return
        }
        if (uiState.isListening) {
            speechHelper.stopListening()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startVoiceInput(field)
            speechHelper.startListening()
        } else {
            pendingField = field
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    LaunchedEffect(uiState.voiceError) {
        uiState.voiceError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearVoiceError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title with mic
            val titleMicColor by animateColorAsState(
                if (uiState.isListening && uiState.activeVoiceField == VoiceTargetField.TITLE)
                    Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "titleMicColor"
            )
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("What do you need to do?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (speechHelper.isAvailable) {
                        IconButton(onClick = { startVoice(VoiceTargetField.TITLE) }) {
                            Icon(
                                if (uiState.isListening && uiState.activeVoiceField == VoiceTargetField.TITLE)
                                    Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice input for title",
                                tint = titleMicColor
                            )
                        }
                    }
                }
            )

            // Description with mic
            val descMicColor by animateColorAsState(
                if (uiState.isListening && uiState.activeVoiceField == VoiceTargetField.DESCRIPTION)
                    Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "descMicColor"
            )
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Details (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    if (speechHelper.isAvailable) {
                        IconButton(onClick = { startVoice(VoiceTargetField.DESCRIPTION) }) {
                            Icon(
                                if (uiState.isListening && uiState.activeVoiceField == VoiceTargetField.DESCRIPTION)
                                    Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice input for description",
                                tint = descMicColor
                            )
                        }
                    }
                }
            )

            // Related person
            OutlinedTextField(
                value = uiState.relatedPerson,
                onValueChange = viewModel::updateRelatedPerson,
                label = { Text("Related person (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Commitment type
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommitmentType.entries.forEach { type ->
                    FilterChip(
                        selected = uiState.commitmentType == type,
                        onClick = { viewModel.updateCommitmentType(type) },
                        label = {
                            Text(
                                type.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            // Priority
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = uiState.priority == priority,
                        onClick = { viewModel.updatePriority(priority) },
                        label = {
                            Text(
                                priority.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            // Deadline toggle
            Column {
                Text("Deadline", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = uiState.hasDeadline,
                    onCheckedChange = viewModel::toggleDeadline
                )
                if (uiState.hasDeadline && uiState.deadlineDate != null) {
                    Text(
                        text = "Due: ${uiState.deadlineDate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = viewModel::saveTask,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.title.isNotBlank()
            ) {
                Text("Save Commitment")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
