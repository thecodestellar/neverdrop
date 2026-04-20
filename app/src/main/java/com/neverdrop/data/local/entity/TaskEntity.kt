package com.neverdrop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import com.neverdrop.domain.model.TaskStatus
import java.time.Instant

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val commitmentType: String = CommitmentType.SELF_GOAL.name,
    val priority: String = TaskPriority.MEDIUM.name,
    val status: String = TaskStatus.ACTIVE.name,
    val relatedPerson: String? = null,
    val deadlineEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = Instant.now().toEpochMilli(),
    val completedAtEpochMillis: Long? = null,
    val snoozeCount: Int = 0,
    val lastSnoozedAtEpochMillis: Long? = null
) {
    fun toDomain(): Task = Task(
        id = id,
        title = title,
        description = description,
        commitmentType = CommitmentType.valueOf(commitmentType),
        priority = TaskPriority.valueOf(priority),
        status = TaskStatus.valueOf(status),
        relatedPerson = relatedPerson,
        deadline = deadlineEpochMillis?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        completedAt = completedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        snoozeCount = snoozeCount,
        lastSnoozedAt = lastSnoozedAtEpochMillis?.let { Instant.ofEpochMilli(it) }
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            title = task.title,
            description = task.description,
            commitmentType = task.commitmentType.name,
            priority = task.priority.name,
            status = task.status.name,
            relatedPerson = task.relatedPerson,
            deadlineEpochMillis = task.deadline?.toEpochMilli(),
            createdAtEpochMillis = task.createdAt.toEpochMilli(),
            completedAtEpochMillis = task.completedAt?.toEpochMilli(),
            snoozeCount = task.snoozeCount,
            lastSnoozedAtEpochMillis = task.lastSnoozedAt?.toEpochMilli()
        )
    }
}
