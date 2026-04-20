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
        taskDao.insertTask(TaskEntity.fromDomain(task))

    suspend fun updateTask(task: Task) =
        taskDao.updateTask(TaskEntity.fromDomain(task))

    suspend fun completeTask(id: Long) =
        taskDao.updateTaskStatus(id, TaskStatus.COMPLETED.name, Instant.now().toEpochMilli())

    suspend fun dropTask(id: Long) =
        taskDao.updateTaskStatus(id, TaskStatus.DROPPED.name, Instant.now().toEpochMilli())

    suspend fun snoozeTask(id: Long) =
        taskDao.incrementSnoozeCount(id, Instant.now().toEpochMilli())

    suspend fun deleteTask(task: Task) =
        taskDao.deleteTask(TaskEntity.fromDomain(task))

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
}
