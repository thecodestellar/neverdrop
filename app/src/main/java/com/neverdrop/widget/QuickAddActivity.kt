package com.neverdrop.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.neverdrop.NeverDropApp
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import com.neverdrop.ui.screens.capture.SpeechRecognizerHelper
import com.neverdrop.ui.theme.NeverDropTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class QuickAddActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "quick_add_mode"
        const val MODE_TEXT = "text"
        const val MODE_VOICE = "voice"
    }

    private var startInVoiceMode = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInVoiceMode = true
            setupContent()
        } else {
            startInVoiceMode = false
            setupContent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TEXT

        if (mode == MODE_VOICE) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                startInVoiceMode = true
                setupContent()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            startInVoiceMode = false
            setupContent()
        }
    }

    private fun setupContent() {
        setContent {
            NeverDropTheme {
                QuickAddContent(
                    startInVoiceMode = startInVoiceMode,
                    onSave = { title ->
                        saveTask(title)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun saveTask(title: String) {
        if (title.isBlank()) {
            finish()
            return
        }

        val app = application as NeverDropApp
        CoroutineScope(Dispatchers.IO).launch {
            app.taskRepository.addTask(
                Task(
                    title = title.trim(),
                    commitmentType = CommitmentType.SELF_GOAL,
                    priority = TaskPriority.MEDIUM,
                    createdAt = Instant.now()
                )
            )
        }

        Toast.makeText(this, "Captured!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
private fun QuickAddContent(
    startInVoiceMode: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val speechHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = { text ->
                title = text
                isListening = false
            },
            onPartialResult = { text ->
                title = text
            },
            onListeningStateChanged = { listening ->
                isListening = listening
            },
            onError = { _ ->
                isListening = false
            }
        )
    }

    DisposableEffect(Unit) {
        if (startInVoiceMode && speechHelper.isAvailable) {
            isListening = true
            speechHelper.startListening()
        }
        onDispose { speechHelper.destroy() }
    }

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quick Capture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                val micColor by animateColorAsState(
                    if (isListening) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "micColor"
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(if (isListening) "Listening..." else "What do you need to do?")
                    },
                    singleLine = true,
                    trailingIcon = {
                        if (speechHelper.isAvailable) {
                            IconButton(onClick = {
                                if (isListening) {
                                    speechHelper.stopListening()
                                } else {
                                    isListening = true
                                    speechHelper.startListening()
                                }
                            }) {
                                Icon(
                                    if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice input",
                                    tint = micColor
                                )
                            }
                        }
                    }
                )

                Button(
                    onClick = { onSave(title) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
