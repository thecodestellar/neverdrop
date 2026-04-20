package com.neverdrop

import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import com.neverdrop.domain.model.TaskStatus
import com.neverdrop.domain.usecase.UrgencyCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UrgencyCalculatorTest {

    @Test
    fun `completed task has zero urgency`() {
        val task = Task(
            title = "Done task",
            status = TaskStatus.COMPLETED
        )
        assertEquals(0.0, UrgencyCalculator.calculate(task), 0.01)
    }

    @Test
    fun `promise to someone scores higher than self goal`() {
        val now = Instant.now()
        val promise = Task(title = "Promise", commitmentType = CommitmentType.PROMISE_TO_SOMEONE, createdAt = now)
        val selfGoal = Task(title = "Self", commitmentType = CommitmentType.SELF_GOAL, createdAt = now)

        assertTrue(UrgencyCalculator.calculate(promise, now) > UrgencyCalculator.calculate(selfGoal, now))
    }

    @Test
    fun `overdue task scores higher than task with time remaining`() {
        val now = Instant.now()
        val overdue = Task(
            title = "Overdue",
            deadline = now.minus(Duration.ofHours(2)),
            createdAt = now.minus(Duration.ofDays(1))
        )
        val onTime = Task(
            title = "On Time",
            deadline = now.plus(Duration.ofDays(2)),
            createdAt = now.minus(Duration.ofDays(1))
        )
        assertTrue(UrgencyCalculator.calculate(overdue, now) > UrgencyCalculator.calculate(onTime, now))
    }

    @Test
    fun `snooze count increases urgency`() {
        val now = Instant.now()
        val noSnooze = Task(title = "No snooze", createdAt = now)
        val snoozed = Task(title = "Snoozed", snoozeCount = 4, createdAt = now)

        assertTrue(UrgencyCalculator.calculate(snoozed, now) > UrgencyCalculator.calculate(noSnooze, now))
    }

    @Test
    fun `related person boosts urgency`() {
        val now = Instant.now()
        val withPerson = Task(title = "With person", relatedPerson = "Sarah", createdAt = now)
        val withoutPerson = Task(title = "No person", createdAt = now)

        assertTrue(UrgencyCalculator.calculate(withPerson, now) > UrgencyCalculator.calculate(withoutPerson, now))
    }

    @Test
    fun `urgency score is clamped between 0 and 100`() {
        val now = Instant.now()
        val extremeTask = Task(
            title = "Extreme",
            commitmentType = CommitmentType.PROMISE_TO_SOMEONE,
            priority = TaskPriority.CRITICAL,
            relatedPerson = "Boss",
            deadline = now.minus(Duration.ofDays(30)),
            createdAt = now.minus(Duration.ofDays(60)),
            snoozeCount = 20
        )
        val score = UrgencyCalculator.calculate(extremeTask, now)
        assertTrue(score <= 100.0)
        assertTrue(score >= 0.0)
    }
}
