package com.neverdrop.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.neverdrop.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY createdAtEpochMillis DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' OR status = 'SNOOZED' ORDER BY createdAtEpochMillis DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, completedAtEpochMillis = :completedAt WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: String, completedAt: Long? = null)

    @Query("UPDATE tasks SET snoozeCount = snoozeCount + 1, lastSnoozedAtEpochMillis = :snoozedAt WHERE id = :id")
    suspend fun incrementSnoozeCount(id: Long, snoozedAt: Long)

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' AND deadlineEpochMillis IS NOT NULL AND deadlineEpochMillis <= :beforeMillis")
    suspend fun getTasksDueBefore(beforeMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'SNOOZED'")
    suspend fun getSnoozedTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' AND deadlineEpochMillis IS NOT NULL AND deadlineEpochMillis < :nowMillis")
    suspend fun getOverdueTasks(nowMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' AND deadlineEpochMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getTasksDueBetween(startMillis: Long, endMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' OR status = 'SNOOZED'")
    suspend fun getActiveTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksList(): List<TaskEntity>
}
