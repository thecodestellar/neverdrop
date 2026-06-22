package com.neverdrop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import com.neverdrop.domain.model.TaskStatus
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["uuid"], unique = true)]
)
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
    val lastSnoozedAtEpochMillis: Long? = null,
    // --- cloud-sync metadata ---
    /** Stable, device-independent identity (the Supabase row id). */
    val uuid: String = UUID.randomUUID().toString(),
    /** Last modification time; drives last-write-wins. */
    val updatedAtEpochMillis: Long = Instant.now().toEpochMilli(),
    /** True when there are local changes not yet pushed to the cloud. */
    val dirty: Boolean = true,
    /** Soft-delete tombstone so deletions propagate across devices. */
    val deleted: Boolean = false
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
        lastSnoozedAt = lastSnoozedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        uuid = uuid,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )

    companion object {
        /** Build an entity from a local edit: always marked dirty so it gets pushed. */
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
            lastSnoozedAtEpochMillis = task.lastSnoozedAt?.toEpochMilli(),
            uuid = task.uuid,
            updatedAtEpochMillis = task.updatedAt.toEpochMilli(),
            dirty = true,
            deleted = false
        )
    }
}
