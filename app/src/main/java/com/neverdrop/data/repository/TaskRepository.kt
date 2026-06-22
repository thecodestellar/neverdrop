package com.neverdrop.data.repository

import com.neverdrop.data.local.dao.TaskDao
import com.neverdrop.data.local.entity.TaskEntity
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class TaskRepository(private val taskDao: TaskDao) {

    fun getAllTasks(): Flow<List<Task>> =
        taskDao.getAllTasks().map { entities -> entities.map { it.toDomain() } }

    fun getActiveTasks(): Flow<List<Task>> =
        taskDao.getActiveTasks().map { entities -> entities.map { it.toDomain() } }

    suspend fun addTask(task: Task): Long =
        taskDao.insertTask(TaskEntity.fromDomain(task.copy(updatedAt = Instant.now())))

    suspend fun updateTask(task: Task) {
        // REPLACE by primary key; fromDomain stamps dirty=true so the edit is pushed.
        taskDao.insertTask(TaskEntity.fromDomain(task.copy(updatedAt = Instant.now())))
    }

    suspend fun completeTask(id: Long) =
        taskDao.updateTaskStatus(id, TaskStatus.COMPLETED.name, Instant.now().toEpochMilli(), now())

    suspend fun dropTask(id: Long) =
        taskDao.updateTaskStatus(id, TaskStatus.DROPPED.name, Instant.now().toEpochMilli(), now())

    suspend fun snoozeTask(id: Long) =
        taskDao.incrementSnoozeCount(id, Instant.now().toEpochMilli(), now())

    suspend fun deleteTask(task: Task) =
        taskDao.softDelete(task.id, now())

    suspend fun getTasksDueBefore(before: Instant): List<Task> =
        taskDao.getTasksDueBefore(before.toEpochMilli()).map { it.toDomain() }

    suspend fun getSnoozedTasks(): List<Task> =
        taskDao.getSnoozedTasks().map { it.toDomain() }

    suspend fun getOverdueTasks(): List<Task> =
        taskDao.getOverdueTasks(Instant.now().toEpochMilli()).map { it.toDomain() }

    suspend fun getTasksDueBetween(start: Instant, end: Instant): List<Task> =
        taskDao.getTasksDueBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }

    suspend fun getActiveTasksList(): List<Task> =
        taskDao.getActiveTasksList().map { it.toDomain() }

    suspend fun getAllTasksList(): List<Task> =
        taskDao.getAllTasksList().map { it.toDomain() }

    // --- cloud sync hooks (operate at the entity level to carry sync metadata) ---

    /** Rows with un-pushed local changes (including deleted tombstones). */
    suspend fun dirtyEntities(): List<TaskEntity> = taskDao.getDirtyTasks()

    suspend fun markSynced(uuids: List<String>) {
        if (uuids.isNotEmpty()) taskDao.markSynced(uuids)
    }

    /**
     * Apply a row pulled from the cloud using last-write-wins. A strictly-newer local row is
     * kept (it will be pushed on the next cycle); otherwise the remote row overwrites it.
     * Remote-applied rows are marked clean so they aren't pushed straight back.
     */
    suspend fun applyRemote(remote: TaskEntity) {
        val local = taskDao.getByUuid(remote.uuid)
        if (local != null && local.updatedAtEpochMillis > remote.updatedAtEpochMillis) return
        taskDao.insertTask(remote.copy(id = local?.id ?: 0, dirty = false))
    }

    private fun now(): Long = Instant.now().toEpochMilli()
}
