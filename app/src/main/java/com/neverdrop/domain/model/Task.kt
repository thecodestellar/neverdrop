package com.neverdrop.domain.model

import java.time.Instant

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val commitmentType: CommitmentType = CommitmentType.SELF_GOAL,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val relatedPerson: String? = null,
    val deadline: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val snoozeCount: Int = 0,
    val lastSnoozedAt: Instant? = null
) {
    val isOverdue: Boolean
        get() = deadline != null && Instant.now().isAfter(deadline) && status == TaskStatus.ACTIVE
}
