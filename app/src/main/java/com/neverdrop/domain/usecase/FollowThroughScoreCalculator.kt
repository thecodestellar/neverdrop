package com.neverdrop.domain.usecase

import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant

/**
 * Calculates follow-through metrics: the percentage of commitments
 * fulfilled on time within a given time window.
 */
object FollowThroughScoreCalculator {

    data class Score(
        val percentage: Double,
        val completedOnTime: Int,
        val completedLate: Int,
        val dropped: Int,
        val active: Int,
        val total: Int,
        val streakDays: Int
    )

    fun calculate(tasks: List<Task>, windowDays: Int = 7, now: Instant = Instant.now()): Score {
        val windowStart = now.minus(Duration.ofDays(windowDays.toLong()))
        val relevant = tasks.filter { it.createdAt.isAfter(windowStart) || it.status == TaskStatus.ACTIVE }

        if (relevant.isEmpty()) return Score(100.0, 0, 0, 0, 0, 0, 0)

        var completedOnTime = 0
        var completedLate = 0
        var dropped = 0
        var active = 0

        for (task in relevant) {
            when (task.status) {
                TaskStatus.COMPLETED -> {
                    if (task.deadline != null && task.completedAt != null &&
                        task.completedAt.isAfter(task.deadline)
                    ) {
                        completedLate++
                    } else {
                        completedOnTime++
                    }
                }
                TaskStatus.DROPPED -> dropped++
                TaskStatus.ACTIVE, TaskStatus.SNOOZED -> active++
                TaskStatus.ARCHIVED -> { /* excluded */ }
            }
        }

        val resolved = completedOnTime + completedLate + dropped
        val percentage = if (resolved > 0) {
            (completedOnTime.toDouble() / resolved) * 100.0
        } else {
            100.0 // No resolved items = perfect score
        }

        val streakDays = calculateStreak(tasks, now)

        return Score(
            percentage = percentage,
            completedOnTime = completedOnTime,
            completedLate = completedLate,
            dropped = dropped,
            active = active,
            total = relevant.size,
            streakDays = streakDays
        )
    }

    /**
     * Counts consecutive days (ending today) with zero dropped items.
     */
    private fun calculateStreak(tasks: List<Task>, now: Instant): Int {
        var streak = 0
        var day = now
        for (i in 0 until 365) {
            val dayStart = day.minus(Duration.ofDays(i.toLong()))
            val dayEnd = dayStart.plus(Duration.ofDays(1))
            val droppedToday = tasks.any { task ->
                task.status == TaskStatus.DROPPED &&
                    task.completedAt != null &&
                    task.completedAt.isAfter(dayStart) &&
                    task.completedAt.isBefore(dayEnd)
            }
            if (droppedToday) break
            streak++
        }
        return streak
    }
}
