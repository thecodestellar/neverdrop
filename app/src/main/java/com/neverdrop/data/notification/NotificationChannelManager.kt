package com.neverdrop.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannelManager {

    const val CHANNEL_CRITICAL = "neverdrop_critical"
    const val CHANNEL_HIGH = "neverdrop_high"
    const val CHANNEL_MEDIUM = "neverdrop_medium"
    const val CHANNEL_LOW = "neverdrop_low"
    const val CHANNEL_BRIEFING = "neverdrop_briefing"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val channels = listOf(
            NotificationChannel(
                CHANNEL_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent tasks needing immediate attention (urgency 80+)"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_HIGH,
                "High Priority",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority tasks (urgency 60-79)"
            },
            NotificationChannel(
                CHANNEL_MEDIUM,
                "Medium Priority",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Medium priority tasks (urgency 40-59)"
            },
            NotificationChannel(
                CHANNEL_LOW,
                "Low Priority",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Low priority reminders (urgency below 40)"
            },
            NotificationChannel(
                CHANNEL_BRIEFING,
                "Morning Briefing",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily morning summary of your commitments"
            }
        )

        manager.createNotificationChannels(channels)
    }

    fun channelForUrgency(urgencyScore: Double): String = when {
        urgencyScore >= 80.0 -> CHANNEL_CRITICAL
        urgencyScore >= 60.0 -> CHANNEL_HIGH
        urgencyScore >= 40.0 -> CHANNEL_MEDIUM
        else -> CHANNEL_LOW
    }
}
