package com.neverdrop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neverdrop.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY createdAtEpochMillis DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND (status = 'ACTIVE' OR status = 'SNOOZED') ORDER BY createdAtEpochMillis DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deleted = 0")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Query(
        "UPDATE tasks SET status = :status, completedAtEpochMillis = :completedAt, " +
            "dirty = 1, updatedAtEpochMillis = :now WHERE id = :id"
    )
    suspend fun updateTaskStatus(id: Long, status: String, completedAt: Long?, now: Long)

    @Query(
        "UPDATE tasks SET snoozeCount = snoozeCount + 1, lastSnoozedAtEpochMillis = :snoozedAt, " +
            "dirty = 1, updatedAtEpochMillis = :now WHERE id = :id"
    )
    suspend fun incrementSnoozeCount(id: Long, snoozedAt: Long, now: Long)

    /** Soft delete: tombstone the row so the deletion syncs to other devices. */
    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND status = 'ACTIVE' AND deadlineEpochMillis IS NOT NULL AND deadlineEpochMillis <= :beforeMillis")
    suspend fun getTasksDueBefore(beforeMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND status = 'SNOOZED'")
    suspend fun getSnoozedTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND status = 'ACTIVE' AND deadlineEpochMillis IS NOT NULL AND deadlineEpochMillis < :nowMillis")
    suspend fun getOverdueTasks(nowMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND status = 'ACTIVE' AND deadlineEpochMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getTasksDueBetween(startMillis: Long, endMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND (status = 'ACTIVE' OR status = 'SNOOZED')")
    suspend fun getActiveTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    suspend fun getAllTasksList(): List<TaskEntity>

    // --- sync ---

    /** All locally-changed rows (including deleted tombstones) awaiting push. */
    @Query("SELECT * FROM tasks WHERE dirty = 1")
    suspend fun getDirtyTasks(): List<TaskEntity>

    /** Lookup by stable id, including tombstones — used when applying remote rows. */
    @Query("SELECT * FROM tasks WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TaskEntity?

    @Query("UPDATE tasks SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markSynced(uuids: List<String>)
}
