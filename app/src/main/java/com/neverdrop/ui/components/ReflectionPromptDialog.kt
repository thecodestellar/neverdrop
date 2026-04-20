package com.neverdrop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neverdrop.domain.model.Task

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReflectionPromptDialog(
    task: Task,
    onDismiss: () -> Unit,
    onSubmit: (feeling: String, note: String) -> Unit
) {
    var selectedFeeling by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val wasDelayed = task.snoozeCount > 0
    val prompt = when {
        task.snoozeCount >= 3 -> "You snoozed this ${task.snoozeCount} times before completing it."
        wasDelayed -> "You snoozed this before completing. How did it feel to finally get it done?"
        else -> "Nice work completing this task! Quick reflection:"
    }

    val feelings = listOf("Relieved", "Proud", "Stressed", "Neutral", "Energized")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reflection", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "\"${task.title}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodySmall
                )

                if (wasDelayed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Was the delay worth the stress?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("How do you feel?", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    feelings.forEach { feeling ->
                        FilterChip(
                            selected = selectedFeeling == feeling,
                            onClick = { selectedFeeling = feeling },
                            label = { Text(feeling, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Any thoughts? (optional)") },
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(selectedFeeling, note) },
                enabled = selectedFeeling.isNotEmpty()
            ) {
                Text("Save Reflection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}
