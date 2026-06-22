package com.neverdrop

import android.app.Application
import com.neverdrop.data.google.CalendarSyncService
import com.neverdrop.data.google.GmailSyncService
import com.neverdrop.data.google.GoogleAuthManager
import com.neverdrop.data.local.NeverDropDatabase
import com.neverdrop.data.notification.NotificationChannelManager
import com.neverdrop.data.notification.NotificationResponseTracker
import com.neverdrop.data.preferences.UserPreferences
import com.neverdrop.data.repository.TaskRepository
import com.neverdrop.data.sync.SyncRepository
import com.neverdrop.data.worker.WorkManagerInitializer

class NeverDropApp : Application() {

    val database: NeverDropDatabase by lazy { NeverDropDatabase.getInstance(this) }
    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val userPreferences: UserPreferences by lazy { UserPreferences(this) }
    val googleAuthManager: GoogleAuthManager by lazy { GoogleAuthManager(this) }
    val notificationResponseTracker: NotificationResponseTracker by lazy { NotificationResponseTracker(this) }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            taskRepository = taskRepository,
            gmailSyncService = GmailSyncService(),
            calendarSyncService = CalendarSyncService(),
            googleAuthManager = googleAuthManager,
            preferences = userPreferences
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Wire up the on-device LLM engine (Gemma via MediaPipe)
        com.neverdrop.data.ai.OnDeviceLlm.init(this)

        // Create notification channels
        NotificationChannelManager.createChannels(this)

        // Initialize workers (deadline alerts, escalation)
        WorkManagerInitializer.initialize(this)

        // Schedule morning briefing if enabled
        if (userPreferences.briefingEnabled) {
            WorkManagerInitializer.scheduleMorningBriefing(
                this,
                userPreferences.briefingHour,
                userPreferences.briefingMinute
            )
        }

        // Schedule evening review if enabled
        if (userPreferences.eveningReviewEnabled) {
            WorkManagerInitializer.scheduleEveningReview(
                this,
                userPreferences.eveningReviewHour,
                userPreferences.eveningReviewMinute
            )
        }

        // Schedule weekly report if enabled
        if (userPreferences.weeklyReportEnabled) {
            WorkManagerInitializer.scheduleWeeklyReport(this)
        }

        // Schedule sync worker if enabled
        if (userPreferences.autoSyncEnabled && googleAuthManager.isSignedIn) {
            WorkManagerInitializer.scheduleSyncWorker(this)
        }
    }
}
