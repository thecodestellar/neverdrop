package com.neverdrop.data.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neverdrop.NeverDropApp
import com.neverdrop.data.notification.NotificationChannelManager
import com.neverdrop.data.notification.TaskNotificationBuilder
import com.neverdrop.domain.usecase.UrgencyCalculator
import java.time.Duration
import java.time.Instant

class EscalationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "escalation_worker"
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
        val tracker = app.notificationResponseTracker
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val now = Instant.now()

        // Escalate heavily-snoozed tasks
        val snoozedTasks = repository.getSnoozedTasks()
        for (task in snoozedTasks) {
            if (task.snoozeCount < 3) continue
            val lastSnoozed = task.lastSnoozedAt ?: continue
            val hoursSinceSnooze = Duration.between(lastSnoozed, now).toHours()

            if (hoursSinceSnooze >= 4) {
                val urgency = UrgencyCalculator.calculate(task)
                // Boost urgency for escalation
                val escalatedUrgency = (urgency + 20.0).coerceAtMost(100.0)
                val channel = NotificationChannelManager.channelForUrgency(escalatedUrgency)
                val notification = TaskNotificationBuilder.buildTaskNotification(
                    context = applicationContext,
                    task = task,
                    urgencyScore = escalatedUrgency,
                    title = "Action needed: ${task.title}",
                    body = "You've snoozed this ${task.snoozeCount} times. Time to act or drop it."
                ).build()

                notificationManager.notify(task.id.toInt(), notification)
                tracker.recordNotificationSent(channel)
            }
        }

        // Re-notify overdue tasks
        val overdueTasks = repository.getOverdueTasks()
        for (task in overdueTasks) {
            val urgency = UrgencyCalculator.calculate(task)
            if (urgency >= 70.0) {
                // Anti-fatigue: suppress during bad hours (but never critical)
                if (tracker.shouldSuppressNow(urgency)) continue

                val channel = NotificationChannelManager.channelForUrgency(urgency)
                val notification = TaskNotificationBuilder.buildTaskNotification(
                    context = applicationContext,
                    task = task,
                    urgencyScore = urgency
                ).build()

                notificationManager.notify(task.id.toInt(), notification)
                tracker.recordNotificationSent(channel)
            }
        }

        return Result.success()
    }
}
