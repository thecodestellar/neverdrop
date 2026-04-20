package com.neverdrop.domain.usecase

import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant

/**
 * Builds a relationship graph from tasks, tracking pending commitments,
 * last contact, and relationship health for each person.
 */
object RelationshipTracker {

    data class PersonRelationship(
        val name: String,
        val pendingTasks: List<Task>,
        val completedTasks: List<Task>,
        val droppedTasks: List<Task>,
        val totalTasks: Int,
        val lastActivityAt: Instant?,
        val daysSinceLastActivity: Long,
        val healthScore: Double, // 0-100
        val healthLabel: String
    )

    data class RelationshipSummary(
        val people: List<PersonRelationship>,
        val totalPeople: Int,
        val needsAttention: List<PersonRelationship>, // Low health or long since contact
        val topCommitments: List<PersonRelationship>  // Most pending tasks
    )

    fun analyze(tasks: List<Task>, now: Instant = Instant.now()): RelationshipSummary {
        val personTasks = tasks
            .filter { !it.relatedPerson.isNullOrBlank() }
            .groupBy { it.relatedPerson!!.trim() }

        val people = personTasks.map { (name, tasks) ->
            buildPersonRelationship(name, tasks, now)
        }.sortedByDescending { it.pendingTasks.size }

        val needsAttention = people
            .filter { it.healthScore < 50 || it.daysSinceLastActivity > 14 }
            .sortedBy { it.healthScore }

        val topCommitments = people
            .filter { it.pendingTasks.isNotEmpty() }
            .sortedByDescending { it.pendingTasks.size }
            .take(5)

        return RelationshipSummary(
            people = people,
            totalPeople = people.size,
            needsAttention = needsAttention,
            topCommitments = topCommitments
        )
    }

    private fun buildPersonRelationship(
        name: String,
        tasks: List<Task>,
        now: Instant
    ): PersonRelationship {
        val pending = tasks.filter { it.status == TaskStatus.ACTIVE || it.status == TaskStatus.SNOOZED }
        val completed = tasks.filter { it.status == TaskStatus.COMPLETED }
        val dropped = tasks.filter { it.status == TaskStatus.DROPPED }

        // Last activity = most recent completedAt, lastSnoozedAt, or createdAt
        val lastActivity = tasks.mapNotNull { task ->
            listOfNotNull(task.completedAt, task.lastSnoozedAt, task.createdAt).maxOrNull()
        }.maxOrNull()

        val daysSince = if (lastActivity != null) {
            Duration.between(lastActivity, now).toDays()
        } else {
            999L
        }

        val healthScore = calculateHealth(pending, completed, dropped, daysSince)
        val healthLabel = when {
            healthScore >= 80 -> "Strong"
            healthScore >= 60 -> "Good"
            healthScore >= 40 -> "Needs attention"
            healthScore >= 20 -> "At risk"
            else -> "Critical"
        }

        return PersonRelationship(
            name = name,
            pendingTasks = pending,
            completedTasks = completed,
            droppedTasks = dropped,
            totalTasks = tasks.size,
            lastActivityAt = lastActivity,
            daysSinceLastActivity = daysSince,
            healthScore = healthScore,
            healthLabel = healthLabel
        )
    }

    private fun calculateHealth(
        pending: List<Task>,
        completed: List<Task>,
        dropped: List<Task>,
        daysSinceContact: Long
    ): Double {
        var score = 100.0

        // Drop rate penalty (max -40)
        val total = completed.size + dropped.size
        if (total > 0) {
            val dropRate = dropped.size.toDouble() / total
            score -= dropRate * 40.0
        }

        // Pending overdue penalty (max -30)
        val now = Instant.now()
        val overdueCount = pending.count { it.deadline != null && it.deadline.isBefore(now) }
        score -= (overdueCount * 10.0).coerceAtMost(30.0)

        // Inactivity penalty (max -30)
        score -= when {
            daysSinceContact > 30 -> 30.0
            daysSinceContact > 14 -> 20.0
            daysSinceContact > 7 -> 10.0
            else -> 0.0
        }

        return score.coerceIn(0.0, 100.0)
    }
}
