package com.neverdrop.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neverdrop.NeverDropApp

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "sync_worker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as NeverDropApp

        return try {
            if (!app.googleAuthManager.isSignedIn) {
                return Result.success()
            }

            val result = app.syncRepository.syncAll()
            Log.d("SyncWorker", "Synced ${result.gmailItems} Gmail + ${result.calendarItems} Calendar items")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
