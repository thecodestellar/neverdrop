package com.neverdrop.data.sync

import com.neverdrop.data.google.CalendarSyncService
import com.neverdrop.data.google.GmailSyncService
import com.neverdrop.data.google.GoogleAuthManager
import com.neverdrop.data.preferences.UserPreferences
import com.neverdrop.data.repository.TaskRepository
import com.neverdrop.domain.model.Task
import java.time.Instant

class SyncRepository(
    private val taskRepository: TaskRepository,
    private val gmailSyncService: GmailSyncService,
    private val calendarSyncService: CalendarSyncService,
    private val googleAuthManager: GoogleAuthManager,
    private val preferences: UserPreferences
) {
    suspend fun syncAll(): SyncResult {
        val credential = googleAuthManager.getAccountCredential()
            ?: return SyncResult(0, 0)

        var gmailCount = 0
        var calendarCount = 0

        try {
            // Sync Gmail
            val gmailResult = gmailSyncService.syncRecentActionableEmails(credential)
            val existingTasks = taskRepository.getActiveTasksList()

            for (task in gmailResult.tasks) {
                if (!isDuplicate(task, existingTasks)) {
                    taskRepository.addTask(task)
                    gmailCount++
                }
            }

            // Sync Calendar
            val calendarResult = calendarSyncService.syncUpcomingEvents(credential)
            val updatedExisting = taskRepository.getActiveTasksList()

            for (task in calendarResult.tasks) {
                if (!isDuplicate(task, updatedExisting)) {
                    taskRepository.addTask(task)
                    calendarCount++
                }
            }
        } catch (e: Exception) {
            // Partial sync is ok; save timestamp of what we did get
        }

        preferences.lastSyncTimestamp = Instant.now().toEpochMilli()

        return SyncResult(gmailCount, calendarCount)
    }

    private fun isDuplicate(newTask: Task, existing: List<Task>): Boolean {
        return existing.any { existingTask ->
            existingTask.title.equals(newTask.title, ignoreCase = true) &&
                (existingTask.deadline == newTask.deadline ||
                    existingTask.relatedPerson == newTask.relatedPerson)
        }
    }

    data class SyncResult(val gmailItems: Int, val calendarItems: Int)
}
