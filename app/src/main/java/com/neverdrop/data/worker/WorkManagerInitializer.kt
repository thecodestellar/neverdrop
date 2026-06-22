package com.neverdrop.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object WorkManagerInitializer {

    fun initialize(context: Context) {
        scheduleDeadlineAlerts(context)
        scheduleEscalation(context)
    }

    private fun scheduleDeadlineAlerts(context: Context) {
        val request = PeriodicWorkRequestBuilder<DeadlineAlertWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DeadlineAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleEscalation(context: Context) {
        val request = PeriodicWorkRequestBuilder<EscalationWorker>(
            1, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EscalationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleMorningBriefing(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }

        val delayMillis = Duration.between(
            now.atZone(ZoneId.systemDefault()).toInstant(),
            target.atZone(ZoneId.systemDefault()).toInstant()
        ).toMillis()

        val request = PeriodicWorkRequestBuilder<MorningBriefingWorker>(
            24, TimeUnit.HOURS
        ).setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MorningBriefingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleSyncWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleEveningReview(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }

        val delayMillis = Duration.between(
            now.atZone(ZoneId.systemDefault()).toInstant(),
            target.atZone(ZoneId.systemDefault()).toInstant()
        ).toMillis()

        val request = PeriodicWorkRequestBuilder<EveningReviewWorker>(
            24, TimeUnit.HOURS
        ).setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EveningReviewWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleWeeklyReport(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(
            7, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeeklyReportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelWeeklyReport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeeklyReportWorker.WORK_NAME)
    }

    fun cancelSyncWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
    }

    fun scheduleCloudSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(
            1, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CloudSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelCloudSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CloudSyncWorker.WORK_NAME)
    }
}
