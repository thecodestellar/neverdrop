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
import com.neverdrop.domain.usecase.FollowThroughScoreCalculator
import com.neverdrop.domain.usecase.UrgencyCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MorningBriefingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "morning_briefing_worker"
        private const val NOTIFICATION_ID = 99999
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

        // Get today's tasks
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val todayTasks = repository.getTasksDueBetween(todayStart, todayEnd)
        val overdueTasks = repository.getOverdueTasks()
        val activeTasks = repository.getActiveTasksList()
        val allTasks = repository.getAllTasksList()

        // Calculate follow-through score
        val score = FollowThroughScoreCalculator.calculate(allTasks)

        // Find top urgent task
        val topUrgent = activeTasks
            .map { it to UrgencyCalculator.calculate(it) }
            .maxByOrNull { it.second }

        // Build summary
        val summaryParts = mutableListOf<String>()
        summaryParts.add("${activeTasks.size} active tasks")
        if (todayTasks.isNotEmpty()) summaryParts.add("${todayTasks.size} due today")
        if (overdueTasks.isNotEmpty()) summaryParts.add("${overdueTasks.size} overdue")
        summaryParts.add("Follow-through: ${score.percentage.toInt()}%")
        if (score.streakDays > 0) summaryParts.add("${score.streakDays}-day streak!")

        val statsLine = summaryParts.joinToString(" | ")
        val heuristicBody = buildString {
            append(statsLine)
            topUrgent?.let { (task, urgency) ->
                append("\n\nTop priority: ${task.title} (urgency: ${urgency.toInt()})")
            }
        }

        // Let the AI engine (on-device Gemma, else cloud Claude) write a warm, coach-style
        // briefing when available; fall back to the plain stats summary otherwise.
        val prefs = app.userPreferences
        val stats = buildString {
            append(statsLine)
            topUrgent?.let { (task, _) -> append("\nMost urgent commitment: ${task.title}") }
            val owed = activeTasks.mapNotNull { it.relatedPerson }.distinct()
            if (owed.isNotEmpty()) append("\nPeople you owe follow-ups: ${owed.joinToString(", ")}")
        }
        val body = com.neverdrop.data.ai.AiEngine.generateBriefing(prefs, stats) ?: heuristicBody

        val contentIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext, NotificationChannelManager.CHANNEL_BRIEFING
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Good morning! Your NeverDrop briefing")
            .setContentText("${activeTasks.size} tasks | ${overdueTasks.size} overdue | ${score.percentage.toInt()}% follow-through")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)

        return Result.success()
    }
}
