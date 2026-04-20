package com.neverdrop.data.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neverdrop.MainActivity
import com.neverdrop.NeverDropApp
import com.neverdrop.R
import com.neverdrop.data.notification.NotificationChannelManager
import com.neverdrop.domain.model.TaskStatus
import com.neverdrop.domain.usecase.FollowThroughScoreCalculator
import com.neverdrop.domain.usecase.UrgencyCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EveningReviewWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "evening_review_worker"
        private const val NOTIFICATION_ID = 99998
    }

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val app = applicationContext as NeverDropApp
        val repository = app.taskRepository

        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()

        val allTasks = repository.getAllTasksList()
        val activeTasks = repository.getActiveTasksList()

        // Today's accomplishments
        val completedToday = allTasks.filter { task ->
            task.status == TaskStatus.COMPLETED &&
                task.completedAt != null &&
                task.completedAt.isAfter(todayStart) &&
                task.completedAt.isBefore(todayEnd)
        }

        // What slipped today (snoozed or overdue)
        val slippedToday = activeTasks.filter { task ->
            (task.snoozeCount > 0 && task.lastSnoozedAt != null &&
                task.lastSnoozedAt.isAfter(todayStart)) ||
                (task.deadline != null && task.deadline.isBefore(now) &&
                    task.deadline.isAfter(todayStart.minus(Duration.ofDays(1))))
        }

        // Tomorrow's top 3 by urgency
        val tomorrowStart = todayEnd
        val tomorrowEnd = today.plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val tomorrowTasks = activeTasks
            .map { it to UrgencyCalculator.calculate(it) }
            .sortedByDescending { it.second }
            .take(3)

        // Follow-through score
        val score = FollowThroughScoreCalculator.calculate(allTasks)

        // Build the notification
        val body = buildString {
            // Accomplishments
            if (completedToday.isNotEmpty()) {
                append("Completed: ${completedToday.size} task(s)")
                completedToday.take(3).forEach { append("\n  - ${it.title}") }
            } else {
                append("No tasks completed today.")
            }

            // What slipped
            if (slippedToday.isNotEmpty()) {
                append("\n\nSlipped: ${slippedToday.size} task(s)")
                slippedToday.take(3).forEach { append("\n  - ${it.title}") }
            }

            // Tomorrow's top 3
            if (tomorrowTasks.isNotEmpty()) {
                append("\n\nTomorrow's focus:")
                tomorrowTasks.forEach { (task, urgency) ->
                    append("\n  - ${task.title} (urgency: ${urgency.toInt()})")
                }
            }

            // Streak
            append("\n\nFollow-through: ${score.percentage.toInt()}%")
            if (score.streakDays > 0) {
                append(" | ${score.streakDays}-day streak!")
            }
        }

        val title = when {
            completedToday.size >= 3 -> "Great day! ${completedToday.size} tasks done"
            completedToday.isNotEmpty() -> "Nice work! ${completedToday.size} task(s) completed"
            slippedToday.isNotEmpty() -> "Tomorrow's a fresh start"
            else -> "Your evening review"
        }

        val contentIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext, NotificationChannelManager.CHANNEL_BRIEFING
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("${completedToday.size} done | ${slippedToday.size} slipped | ${score.percentage.toInt()}% follow-through")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)

        return Result.success()
    }
}
