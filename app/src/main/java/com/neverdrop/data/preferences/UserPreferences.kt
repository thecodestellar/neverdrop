package com.neverdrop.data.preferences

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("neverdrop_user_prefs", Context.MODE_PRIVATE)

    var briefingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BRIEFING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BRIEFING_ENABLED, value).apply()

    var briefingHour: Int
        get() = prefs.getInt(KEY_BRIEFING_HOUR, 8)
        set(value) = prefs.edit().putInt(KEY_BRIEFING_HOUR, value).apply()

    var briefingMinute: Int
        get() = prefs.getInt(KEY_BRIEFING_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_BRIEFING_MINUTE, value).apply()

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var googleAccountEmail: String?
        get() = prefs.getString(KEY_GOOGLE_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_EMAIL, value).apply()

    var notificationMiningEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_MINING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_MINING_ENABLED, value).apply()

    var weeklyReportEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_REPORT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEEKLY_REPORT_ENABLED, value).apply()

    var eveningReviewEnabled: Boolean
        get() = prefs.getBoolean(KEY_EVENING_REVIEW_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_EVENING_REVIEW_ENABLED, value).apply()

    var eveningReviewHour: Int
        get() = prefs.getInt(KEY_EVENING_REVIEW_HOUR, 21)
        set(value) = prefs.edit().putInt(KEY_EVENING_REVIEW_HOUR, value).apply()

    var eveningReviewMinute: Int
        get() = prefs.getInt(KEY_EVENING_REVIEW_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_EVENING_REVIEW_MINUTE, value).apply()

    /** Anthropic API key powering the AI Intelligence Engine. Null when unconfigured. */
    var anthropicApiKey: String?
        get() = prefs.getString(KEY_ANTHROPIC_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_API_KEY, value?.takeIf { it.isNotBlank() }).apply()

    /** Master switch for Claude-powered capture, classification, and briefings. */
    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    /** True when AI features can actually run (enabled + a key is present). */
    val aiAvailable: Boolean
        get() = aiEnabled && !anthropicApiKey.isNullOrBlank()

    companion object {
        private const val KEY_BRIEFING_ENABLED = "briefing_enabled"
        private const val KEY_BRIEFING_HOUR = "briefing_hour"
        private const val KEY_BRIEFING_MINUTE = "briefing_minute"
        private const val KEY_NOTIFICATION_MINING_ENABLED = "notification_mining_enabled"
        private const val KEY_WEEKLY_REPORT_ENABLED = "weekly_report_enabled"
        private const val KEY_EVENING_REVIEW_ENABLED = "evening_review_enabled"
        private const val KEY_EVENING_REVIEW_HOUR = "evening_review_hour"
        private const val KEY_EVENING_REVIEW_MINUTE = "evening_review_minute"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_GOOGLE_EMAIL = "google_account_email"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_AI_ENABLED = "ai_enabled"
    }
}
