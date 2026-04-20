package com.neverdrop.ui.screens.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class DayStats(
    val date: LocalDate,
    val completed: Int,
    val dropped: Int,
    val isNeverDropDay: Boolean // Zero dropped items
)

@Composable
fun StreakCard(
    tasks: List<Task>,
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    val dayStats = calculateWeekStats(tasks)
    val streakColor = when {
        streakDays >= 7 -> Color(0xFF4CAF50)
        streakDays >= 3 -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "NeverDrop Streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            streakDays == 0 -> "Start your streak today!"
                            streakDays == 1 -> "1 day - keep going!"
                            streakDays < 7 -> "$streakDays days - building momentum!"
                            streakDays < 30 -> "$streakDays days - on fire!"
                            else -> "$streakDays days - unstoppable!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$streakDays",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = streakColor
                    )
                    Text(
                        "days",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7-day bar chart
            WeekBarChart(dayStats = dayStats)

            // Day labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dayStats.forEach { day ->
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp),
                        color = if (day.isNeverDropDay) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekBarChart(dayStats: List<DayStats>) {
    val maxCount = dayStats.maxOf { it.completed + it.dropped }.coerceAtLeast(1)
    val completedColor = Color(0xFF4CAF50)
    val droppedColor = Color(0xFFE53935)
    val emptyColor = Color(0xFFE0E0E0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val barWidth = size.width / (dayStats.size * 2f)
        val gap = barWidth

        dayStats.forEachIndexed { index, day ->
            val x = index * (barWidth + gap) + gap / 2
            val totalHeight = size.height
            val completedHeight = (day.completed.toFloat() / maxCount) * totalHeight
            val droppedHeight = (day.dropped.toFloat() / maxCount) * totalHeight

            if (day.completed == 0 && day.dropped == 0) {
                // Empty day — show minimal bar
                drawRoundRect(
                    color = emptyColor,
                    topLeft = Offset(x, totalHeight - 4f),
                    size = Size(barWidth, 4f),
                    cornerRadius = CornerRadius(4f)
                )
            } else {
                // Completed portion
                if (day.completed > 0) {
                    drawRoundRect(
                        color = completedColor,
                        topLeft = Offset(x, totalHeight - completedHeight),
                        size = Size(barWidth, completedHeight),
                        cornerRadius = CornerRadius(4f)
                    )
                }
                // Dropped portion (stacked on top)
                if (day.dropped > 0) {
                    drawRoundRect(
                        color = droppedColor,
                        topLeft = Offset(x, totalHeight - completedHeight - droppedHeight),
                        size = Size(barWidth, droppedHeight),
                        cornerRadius = CornerRadius(4f)
                    )
                }
            }
        }
    }
}

private fun calculateWeekStats(tasks: List<Task>): List<DayStats> {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    return (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

        val completed = tasks.count { task ->
            task.status == TaskStatus.COMPLETED &&
                task.completedAt != null &&
                task.completedAt.isAfter(dayStart) &&
                task.completedAt.isBefore(dayEnd)
        }

        val dropped = tasks.count { task ->
            task.status == TaskStatus.DROPPED &&
                task.completedAt != null &&
                task.completedAt.isAfter(dayStart) &&
                task.completedAt.isBefore(dayEnd)
        }

        DayStats(
            date = date,
            completed = completed,
            dropped = dropped,
            isNeverDropDay = dropped == 0
        )
    }
}
