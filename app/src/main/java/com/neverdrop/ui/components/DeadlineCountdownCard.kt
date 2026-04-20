package com.neverdrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neverdrop.domain.model.Task
import java.time.Duration
import java.time.Instant

@Composable
fun DeadlineCountdownCard(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    val now = Instant.now()
    val urgentTasks = tasks
        .filter { it.deadline != null }
        .map { it to Duration.between(now, it.deadline!!) }
        .filter { (_, remaining) -> remaining.toHours() in -48..24 }
        .sortedBy { it.second }
        .take(3)

    if (urgentTasks.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Deadline Countdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            urgentTasks.forEach { (task, remaining) ->
                CountdownItem(task = task, remaining = remaining)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CountdownItem(task: Task, remaining: Duration) {
    val isOverdue = remaining.isNegative
    val totalHours = if (isOverdue) remaining.abs().toHours() else remaining.toHours()
    val totalMinutes = if (isOverdue) remaining.abs().toMinutes() else remaining.toMinutes()

    val timeText = when {
        isOverdue && totalHours >= 24 -> "OVERDUE ${totalHours / 24}d ${totalHours % 24}h"
        isOverdue && totalHours > 0 -> "OVERDUE ${totalHours}h ${totalMinutes % 60}m"
        isOverdue -> "OVERDUE ${totalMinutes}m"
        totalHours >= 24 -> "${totalHours / 24}d ${totalHours % 24}h left"
        totalHours > 0 -> "${totalHours}h ${totalMinutes % 60}m left"
        else -> "${totalMinutes}m left"
    }

    val urgencyColor by animateColorAsState(
        targetValue = when {
            isOverdue -> Color(0xFFE53935)
            totalHours < 1 -> Color(0xFFE53935)
            totalHours < 3 -> Color(0xFFFF9800)
            totalHours < 12 -> Color(0xFFFFC107)
            else -> Color(0xFF4CAF50)
        },
        label = "urgencyColor"
    )

    // Pulse animation for critical items
    val alpha = if (isOverdue || totalHours < 1) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        animatedAlpha
    } else {
        1f
    }

    // Progress: how much time has elapsed since creation
    val progress = if (!isOverdue && task.createdAt != null && task.deadline != null) {
        val totalDuration = Duration.between(task.createdAt, task.deadline)
        if (totalDuration.toMinutes() > 0) {
            1f - (remaining.toMinutes().toFloat() / totalDuration.toMinutes())
        } else 1f
    } else if (isOverdue) {
        1f
    } else {
        0f
    }

    Column(modifier = Modifier.alpha(alpha)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                timeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = urgencyColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = urgencyColor,
            trackColor = urgencyColor.copy(alpha = 0.15f),
        )

        task.relatedPerson?.let {
            Text(
                "For: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
        }
    }
}
