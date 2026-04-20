package com.neverdrop.data.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.neverdrop.MainActivity
import com.neverdrop.R
import com.neverdrop.domain.model.Task
import java.time.Duration
import java.time.Instant

object TaskNotificationBuilder {

    fun buildTaskNotification(
        context: Context,
        task: Task,
        urgencyScore: Double,
        title: String? = null,
        body: String? = null
    ): NotificationCompat.Builder {
        val channel = NotificationChannelManager.channelForUrgency(urgencyScore)

        val contentIntent = PendingIntent.getActivity(
            context, task.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = PendingIntent.getBroadcast(
            context, (task.id * 10 + 1).toInt(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COMPLETE
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
                putExtra(NotificationActionReceiver.EXTRA_CHANNEL, channel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context, (task.id * 10 + 2).toInt(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SNOOZE
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
                putExtra(NotificationActionReceiver.EXTRA_CHANNEL, channel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = title ?: buildTitle(task, urgencyScore)
        val notifBody = body ?: buildBody(task)

        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Complete", completeIntent)
            .addAction(0, "Snooze", snoozeIntent)
            .setPriority(
                when {
                    urgencyScore >= 60.0 -> NotificationCompat.PRIORITY_HIGH
                    urgencyScore >= 40.0 -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
    }

    private fun buildTitle(task: Task, urgencyScore: Double): String {
        val prefix = when {
            urgencyScore >= 80.0 -> "URGENT: "
            urgencyScore >= 60.0 -> "Reminder: "
            else -> ""
        }
        return "$prefix${task.title}"
    }

    private fun buildBody(task: Task): String {
        val parts = mutableListOf<String>()

        task.relatedPerson?.let { parts.add("For: $it") }

        task.deadline?.let { deadline ->
            val now = Instant.now()
            if (now.isAfter(deadline)) {
                val overdue = Duration.between(deadline, now)
                val hours = overdue.toHours()
                parts.add("OVERDUE by ${if (hours > 0) "${hours}h" else "${overdue.toMinutes()}m"}")
            } else {
                val remaining = Duration.between(now, deadline)
                val hours = remaining.toHours()
                if (hours < 24) {
                    parts.add("Due in ${if (hours > 0) "${hours}h" else "${remaining.toMinutes()}m"}")
                }
            }
        }

        if (task.snoozeCount > 0) {
            parts.add("Snoozed ${task.snoozeCount} time${if (task.snoozeCount > 1) "s" else ""}")
        }

        if (task.description.isNotBlank()) {
            parts.add(task.description)
        }

        return parts.joinToString(" | ")
    }
}
