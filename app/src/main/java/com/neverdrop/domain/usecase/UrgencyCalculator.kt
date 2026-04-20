package com.neverdrop.domain.usecase

import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates a dynamic urgency score (0.0–100.0) for a task.
 *
 * The score uses an exponential decay model that increases as deadlines approach
 * and spikes when overdue. Tasks without deadlines get a baseline score based on
 * age, commitment type, and snooze count.
 */
object UrgencyCalculator {

    fun calculate(task: Task, now: Instant = Instant.now()): Double {
        if (task.status != TaskStatus.ACTIVE) return 0.0

        val baseScore = commitmentTypeWeight(task.commitmentType)
        val deadlineScore = deadlineUrgency(task, now)
        val snoozeBoost = snoozeEscalation(task.snoozeCount)
        val ageScore = ageWeight(task.createdAt, now)
        val personBoost = if (task.relatedPerson != null) 10.0 else 0.0

        val raw = baseScore + deadlineScore + snoozeBoost + ageScore + personBoost
        return min(100.0, max(0.0, raw))
    }

    private fun commitmentTypeWeight(type: CommitmentType): Double = when (type) {
        CommitmentType.PROMISE_TO_SOMEONE -> 25.0
        CommitmentType.TIME_SENSITIVE_EVENT -> 20.0
        CommitmentType.RELATIONSHIP_MAINTENANCE -> 15.0
        CommitmentType.RECURRING_DUTY -> 10.0
        CommitmentType.SELF_GOAL -> 5.0
    }

    /**
     * Exponential urgency curve: score rises slowly at first, then spikes
     * as the deadline approaches. Overdue items get maximum deadline urgency.
     */
    private fun deadlineUrgency(task: Task, now: Instant): Double {
        val deadline = task.deadline ?: return 0.0
        val totalDuration = Duration.between(task.createdAt, deadline)
        val remaining = Duration.between(now, deadline)

        if (remaining.isNegative) {
            // Overdue: score increases with how overdue it is
            val overdueHours = Duration.between(deadline, now).toHours().toDouble()
            return min(50.0, 35.0 + overdueHours * 0.5)
        }

        if (totalDuration.isZero || totalDuration.isNegative) return 30.0

        // Fraction of time elapsed (0.0 = just created, 1.0 = at deadline)
        val elapsed = 1.0 - (remaining.toMillis().toDouble() / totalDuration.toMillis().toDouble())
        // Exponential curve: slow early, fast near deadline
        val urgency = 35.0 * (exp(2.0 * elapsed) - 1.0) / (exp(2.0) - 1.0)
        return max(0.0, urgency)
    }

    /**
     * Each snooze increases urgency. After 3+ snoozes, the boost is significant
     * to trigger snooze intelligence intervention.
     */
    private fun snoozeEscalation(snoozeCount: Int): Double = when {
        snoozeCount == 0 -> 0.0
        snoozeCount <= 2 -> snoozeCount * 3.0
        else -> 6.0 + (snoozeCount - 2) * 5.0 // Accelerating penalty
    }

    /**
     * Older unresolved tasks slowly gain urgency to prevent them from being
     * forgotten entirely.
     */
    private fun ageWeight(createdAt: Instant, now: Instant): Double {
        val ageDays = Duration.between(createdAt, now).toDays().toDouble()
        return min(10.0, ageDays * 0.5)
    }
}
