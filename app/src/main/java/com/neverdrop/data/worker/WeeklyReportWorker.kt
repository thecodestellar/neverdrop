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
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.TaskStatus
import com.neverdrop.domain.usecase.ForgettingProfileAnalyzer
import com.neverdrop.domain.usecase.FollowThroughScoreCalculator
import com.neverdrop.domain.usecase.RelationshipTracker
import java.time.Duration
import java.time.Instant

class WeeklyReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weekly_report_worker"
        private const val NOTIFICATION_ID = 99997
    }

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val app = applicationContext as NeverDropApp
        val allTasks = app.taskRepository.getAllTasksList()

        if (allTasks.isEmpty()) return Result.success()

        val now = Instant.now()
        val weekAgo = now.minus(Duration.ofDays(7))

        // This week's tasks
        val thisWeek = allTasks.filter { it.createdAt.isAfter(weekAgo) }
        val completedThisWeek = thisWeek.count { it.status == TaskStatus.COMPLETED }
        val droppedThisWeek = thisWeek.count { it.status == TaskStatus.DROPPED }
        val createdThisWeek = thisWeek.size

        // Score
        val score = FollowThroughScoreCalculator.calculate(allTasks, windowDays = 7)

        // Category breakdown
        val byType = thisWeek.groupBy { it.commitmentType }
        val typeBreakdown = byType.entries
            .sortedByDescending { it.value.size }
            .joinToString("\n") { (type, tasks) ->
                val completed = tasks.count { it.status == TaskStatus.COMPLETED }
                val typeName = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                "  $typeName: ${tasks.size} total, $completed completed"
            }

        // Forgetting insights
        val forgettingProfile = ForgettingProfileAnalyzer.analyze(allTasks)
        val topInsight = forgettingProfile.insights
            .firstOrNull { it.severity != ForgettingProfileAnalyzer.Severity.INFO }

        // Relationship check
        val relationships = RelationshipTracker.analyze(allTasks)
        val neglected = relationships.needsAttention.take(2)

        // Build report
        val body = buildString {
            append("WEEKLY INTELLIGENCE REPORT\n")
            append("=========================\n\n")

            append("This week: $createdThisWeek tasks created\n")
            append("Completed: $completedThisWeek | Dropped: $droppedThisWeek\n")
            append("Follow-through: ${score.percentage.toInt()}%")
            if (score.streakDays > 0) append(" | ${score.streakDays}-day streak")
            append("\n")

            if (typeBreakdown.isNotEmpty()) {
                append("\nBy category:\n$typeBreakdown\n")
            }

            topInsight?.let {
                append("\nInsight: ${it.text}\n")
            }

            if (neglected.isNotEmpty()) {
                append("\nRelationships needing attention:\n")
                neglected.forEach { person ->
                    append("  ${person.name} — ${person.healthLabel} (${person.pendingTasks.size} pending)\n")
                }
            }

            // Actionable advice
            append("\n")
            append(generateAdvice(score, droppedThisWeek, completedThisWeek, forgettingProfile))
        }

        val title = when {
            score.percentage >= 90 -> "Excellent week! ${score.percentage.toInt()}% follow-through"
            score.percentage >= 70 -> "Good week — ${completedThisWeek} tasks done"
            score.percentage >= 50 -> "Room to improve — $droppedThisWeek dropped this week"
            else -> "Let's reset — your weekly review"
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
            .setContentText("$completedThisWeek completed | $droppedThisWeek dropped | ${score.percentage.toInt()}%")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)

        return Result.success()
    }

    private fun generateAdvice(
        score: FollowThroughScoreCalculator.Score,
        dropped: Int,
        completed: Int,
        profile: ForgettingProfileAnalyzer.ForgettingProfile
    ): String {
        val advice = mutableListOf<String>()

        if (score.percentage < 70 && dropped > 2) {
            advice.add("Try capturing fewer, more intentional tasks next week. Quality over quantity.")
        }

        if (score.streakDays >= 7) {
            advice.add("Amazing ${score.streakDays}-day streak! Keep the momentum going.")
        } else if (score.streakDays == 0 && dropped > 0) {
            advice.add("Start fresh tomorrow — one perfect day starts a new streak.")
        }

        if (profile.avgSnoozeBeforeDrop >= 3) {
            advice.add("You snooze tasks ${profile.avgSnoozeBeforeDrop.toInt()}x before dropping. Try breaking them into smaller steps.")
        }

        if (completed >= 10) {
            advice.add("$completed tasks completed — that's serious productivity!")
        }

        return if (advice.isEmpty()) {
            "Keep going — every completed task builds your follow-through muscle."
        } else {
            advice.joinToString("\n")
        }
    }
}
