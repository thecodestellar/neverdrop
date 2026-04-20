package com.neverdrop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neverdrop.ui.screens.home.TaskWithUrgency
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TaskCard(
    taskWithUrgency: TaskWithUrgency,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val task = taskWithUrgency.task
    val urgency = taskWithUrgency.urgencyScore

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UrgencyIndicator(score = urgency)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.relatedPerson != null) {
                        Text(
                            text = task.relatedPerson,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = String.format("%.0f", urgency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Metadata
                Column {
                    Text(
                        text = task.commitmentType.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    task.deadline?.let { deadline ->
                        val remaining = Duration.between(Instant.now(), deadline)
                        val text = if (remaining.isNegative) {
                            "Overdue by ${-remaining.toHours()}h"
                        } else {
                            val formatted = deadline.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                            "Due: $formatted"
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    if (task.snoozeCount > 0) {
                        Text(
                            text = "Snoozed ${task.snoozeCount}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Actions
                Row {
                    IconButton(onClick = onComplete) {
                        Icon(Icons.Default.Check, contentDescription = "Complete",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onSnooze) {
                        Icon(Icons.Default.Snooze, contentDescription = "Snooze",
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = onDrop) {
                        Icon(Icons.Default.Close, contentDescription = "Drop",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
