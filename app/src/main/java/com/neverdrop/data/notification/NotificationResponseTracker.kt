package com.neverdrop.data.notification

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tracks how the user responds to notifications to learn their preferences.
 * Stores aggregated stats per hour-of-day and per channel to adapt future notifications.
 */
class NotificationResponseTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notification_response_stats", Context.MODE_PRIVATE)

    companion object {
        // Keys for hourly response tracking (0-23)
        private const val PREFIX_HOUR_SENT = "hour_sent_"
        private const val PREFIX_HOUR_ACTED = "hour_acted_"
        private const val PREFIX_HOUR_SNOOZED = "hour_snoozed_"
        private const val PREFIX_HOUR_IGNORED = "hour_ignored_"

        // Keys for channel response tracking
        private const val PREFIX_CHANNEL_SENT = "channel_sent_"
        private const val PREFIX_CHANNEL_ACTED = "channel_acted_"
        private const val PREFIX_CHANNEL_SNOOZED = "channel_snoozed_"

        // Overall stats
        private const val KEY_TOTAL_SENT = "total_sent"
        private const val KEY_TOTAL_ACTED = "total_acted"
        private const val KEY_TOTAL_SNOOZED = "total_snoozed"

        // Minimum data points before adapting
        const val MIN_DATA_POINTS = 10
    }

    fun recordNotificationSent(channel: String) {
        val hour = currentHour()
        prefs.edit()
            .putInt(PREFIX_HOUR_SENT + hour, getHourSent(hour) + 1)
            .putInt(PREFIX_CHANNEL_SENT + channel, getChannelSent(channel) + 1)
            .putInt(KEY_TOTAL_SENT, totalSent + 1)
            .apply()
    }

    fun recordAction(channel: String) {
        val hour = currentHour()
        prefs.edit()
            .putInt(PREFIX_HOUR_ACTED + hour, getHourActed(hour) + 1)
            .putInt(PREFIX_CHANNEL_ACTED + channel, getChannelActed(channel) + 1)
            .putInt(KEY_TOTAL_ACTED, totalActed + 1)
            .apply()
    }

    fun recordSnooze(channel: String) {
        val hour = currentHour()
        prefs.edit()
            .putInt(PREFIX_HOUR_SNOOZED + hour, getHourSnoozed(hour) + 1)
            .putInt(PREFIX_CHANNEL_SNOOZED + channel, getChannelSnoozed(channel) + 1)
            .putInt(KEY_TOTAL_SNOOZED, totalSnoozed + 1)
            .apply()
    }

    fun recordIgnored(channel: String) {
        val hour = currentHour()
        prefs.edit()
            .putInt(PREFIX_HOUR_IGNORED + hour, getHourIgnored(hour) + 1)
            .apply()
    }

    /**
     * Returns the action rate (0.0 to 1.0) for a given hour of day.
     * Higher = user is more responsive at this hour.
     */
    fun getHourlyActionRate(hour: Int): Double {
        val sent = getHourSent(hour)
        if (sent == 0) return 0.5 // No data, neutral
        val acted = getHourActed(hour)
        return acted.toDouble() / sent
    }

    /**
     * Returns the best hours to send notifications, sorted by action rate descending.
     */
    fun getBestHours(topN: Int = 3): List<Int> {
        if (totalSent < MIN_DATA_POINTS) return emptyList()

        return (0..23)
            .filter { getHourSent(it) >= 2 } // Need at least 2 data points per hour
            .sortedByDescending { getHourlyActionRate(it) }
            .take(topN)
    }

    /**
     * Returns the worst hours (high ignore/snooze rate) to suppress notifications.
     */
    fun getWorstHours(topN: Int = 3): List<Int> {
        if (totalSent < MIN_DATA_POINTS) return emptyList()

        return (0..23)
            .filter { getHourSent(it) >= 2 }
            .sortedBy { getHourlyActionRate(it) }
            .take(topN)
    }

    /**
     * Should we suppress a notification right now based on learned patterns?
     * Returns true if current hour has very low action rate.
     */
    fun shouldSuppressNow(urgencyScore: Double): Boolean {
        if (totalSent < MIN_DATA_POINTS) return false

        // Never suppress critical notifications
        if (urgencyScore >= 80.0) return false

        val hour = currentHour()
        val actionRate = getHourlyActionRate(hour)

        // Suppress if action rate is below 15% at this hour (user rarely acts)
        return actionRate < 0.15 && getHourSent(hour) >= 3
    }

    /**
     * Suggests the best channel for a given urgency score based on learned patterns.
     * Returns null if not enough data to adapt.
     */
    fun suggestChannel(defaultChannel: String): String {
        if (totalSent < MIN_DATA_POINTS) return defaultChannel

        // Calculate action rate per channel
        val channels = listOf(
            NotificationChannelManager.CHANNEL_CRITICAL,
            NotificationChannelManager.CHANNEL_HIGH,
            NotificationChannelManager.CHANNEL_MEDIUM,
            NotificationChannelManager.CHANNEL_LOW
        )

        val channelRates = channels.mapNotNull { channel ->
            val sent = getChannelSent(channel)
            if (sent < 3) return@mapNotNull null
            val acted = getChannelActed(channel)
            channel to (acted.toDouble() / sent)
        }

        if (channelRates.isEmpty()) return defaultChannel

        // If user ignores low-priority notifications, escalate to medium
        val lowRate = channelRates.find { it.first == NotificationChannelManager.CHANNEL_LOW }
        if (defaultChannel == NotificationChannelManager.CHANNEL_LOW && lowRate != null && lowRate.second < 0.1) {
            return NotificationChannelManager.CHANNEL_MEDIUM
        }

        return defaultChannel
    }

    val totalSent: Int get() = prefs.getInt(KEY_TOTAL_SENT, 0)
    val totalActed: Int get() = prefs.getInt(KEY_TOTAL_ACTED, 0)
    val totalSnoozed: Int get() = prefs.getInt(KEY_TOTAL_SNOOZED, 0)
    val hasEnoughData: Boolean get() = totalSent >= MIN_DATA_POINTS

    val overallActionRate: Double
        get() = if (totalSent == 0) 0.0 else totalActed.toDouble() / totalSent

    private fun getHourSent(hour: Int) = prefs.getInt(PREFIX_HOUR_SENT + hour, 0)
    private fun getHourActed(hour: Int) = prefs.getInt(PREFIX_HOUR_ACTED + hour, 0)
    private fun getHourSnoozed(hour: Int) = prefs.getInt(PREFIX_HOUR_SNOOZED + hour, 0)
    private fun getHourIgnored(hour: Int) = prefs.getInt(PREFIX_HOUR_IGNORED + hour, 0)
    private fun getChannelSent(channel: String) = prefs.getInt(PREFIX_CHANNEL_SENT + channel, 0)
    private fun getChannelActed(channel: String) = prefs.getInt(PREFIX_CHANNEL_ACTED + channel, 0)
    private fun getChannelSnoozed(channel: String) = prefs.getInt(PREFIX_CHANNEL_SNOOZED + channel, 0)

    private fun currentHour(): Int =
        LocalTime.now(ZoneId.systemDefault()).hour
}
