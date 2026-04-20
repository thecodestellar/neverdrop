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

class DeadlineAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "deadline_alert_worker"
        private const val PREFS_NAME = "deadline_alerts"
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
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val now = Instant.now()

        // Check tasks due within 1h, 3h, 24h windows
        val windows = listOf(
            Duration.ofHours(1) to "1h",
            Duration.ofHours(3) to "3h",
            Duration.ofHours(24) to "24h"
        )

        val dueSoonTasks = repository.getTasksDueBefore(now.plus(Duration.ofHours(24)))

        for (task in dueSoonTasks) {
            val deadline = task.deadline ?: continue
            val timeUntil = Duration.between(now, deadline)

            for ((window, windowKey) in windows) {
                if (timeUntil <= window && timeUntil > Duration.ZERO) {
                    val notifKey = "notified_${task.id}_$windowKey"
                    if (prefs.getBoolean(notifKey, false)) continue

                    val urgency = UrgencyCalculator.calculate(task)

                    // Anti-fatigue: suppress low-urgency notifications during bad hours
                    if (tracker.shouldSuppressNow(urgency)) continue

                    // Anti-fatigue: adapt channel based on learned preferences
                    val defaultChannel = NotificationChannelManager.channelForUrgency(urgency)
                    val adaptedChannel = tracker.suggestChannel(defaultChannel)

                    val notification = TaskNotificationBuilder.buildTaskNotification(
                        context = applicationContext,
                        task = task,
                        urgencyScore = urgency
                    ).build()

                    notificationManager.notify(task.id.toInt(), notification)
                    tracker.recordNotificationSent(adaptedChannel)
                    prefs.edit().putBoolean(notifKey, true).apply()
                    break // Only send highest-priority window notification
                }
            }

            // Overdue notifications
            if (timeUntil < Duration.ZERO) {
                val overdueKey = "notified_${task.id}_overdue"
                val lastOverdueNotif = prefs.getLong(overdueKey, 0L)
                val hoursSinceNotif = Duration.between(
                    Instant.ofEpochMilli(lastOverdueNotif), now
                ).toHours()

                if (lastOverdueNotif == 0L || hoursSinceNotif >= 4) {
                    val urgency = UrgencyCalculator.calculate(task)

                    // Anti-fatigue: suppress low-urgency overdue notifications during bad hours
                    if (tracker.shouldSuppressNow(urgency)) continue

                    val channel = NotificationChannelManager.channelForUrgency(urgency)
                    val notification = TaskNotificationBuilder.buildTaskNotification(
                        context = applicationContext,
                        task = task,
                        urgencyScore = urgency
                    ).build()

                    notificationManager.notify(task.id.toInt(), notification)
                    tracker.recordNotificationSent(channel)
                    prefs.edit().putLong(overdueKey, now.toEpochMilli()).apply()
                }
            }
        }

        return Result.success()
    }
}
