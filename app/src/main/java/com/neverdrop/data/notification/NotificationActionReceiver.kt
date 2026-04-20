package com.neverdrop.data.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neverdrop.NeverDropApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE = "com.neverdrop.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "com.neverdrop.ACTION_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_CHANNEL = "notification_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1L) return

        val app = context.applicationContext as NeverDropApp
        val repository = app.taskRepository
        val tracker = app.notificationResponseTracker
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: ""

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        repository.completeTask(taskId)
                        tracker.recordAction(channel)
                    }
                    ACTION_SNOOZE -> {
                        repository.snoozeTask(taskId)
                        tracker.recordSnooze(channel)
                    }
                }
                // Dismiss the notification
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(taskId.toInt())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
