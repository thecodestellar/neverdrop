package com.neverdrop.domain.model

import java.time.Instant
import java.util.UUID

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
    val lastSnoozedAt: Instant? = null,
    /** Stable, device-independent identity used for cloud sync. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Last local modification time; drives last-write-wins conflict resolution. */
    val updatedAt: Instant = Instant.now()
) {
    val isOverdue: Boolean
        get() = deadline != null && Instant.now().isAfter(deadline) && status == TaskStatus.ACTIVE
}
