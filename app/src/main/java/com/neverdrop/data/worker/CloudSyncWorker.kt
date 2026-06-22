package com.neverdrop.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neverdrop.NeverDropApp

/**
 * Periodic (and on-demand) push/pull of tasks to Supabase. No-op when cloud sync is disabled
 * or unconfigured; retries on transient failures.
 */
class CloudSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "cloud_sync_worker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as NeverDropApp
        val prefs = app.userPreferences
        if (!prefs.cloudSyncEnabled || !prefs.cloudSyncConfigured) {
            return Result.success()
        }
        val result = app.supabaseSyncRepository.syncAll()
        return result.fold(
            onSuccess = {
                Log.d("CloudSyncWorker", "Synced: pushed ${it.pushed}, pulled ${it.pulled}")
                Result.success()
            },
            onFailure = {
                Log.w("CloudSyncWorker", "Cloud sync failed", it)
                Result.retry()
            }
        )
    }
}
