package com.neverdrop.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neverdrop.data.preferences.UserPreferences
import com.neverdrop.data.google.GoogleAuthManager
import com.neverdrop.data.worker.WorkManagerInitializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val briefingEnabled: Boolean = true,
    val briefingHour: Int = 8,
    val briefingMinute: Int = 0,
    val weeklyReportEnabled: Boolean = true,
    val eveningReviewEnabled: Boolean = true,
    val eveningReviewHour: Int = 21,
    val eveningReviewMinute: Int = 0,
    val notificationMiningEnabled: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val googleEmail: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val aiEnabled: Boolean = true,
    val hasApiKey: Boolean = false
)

class SettingsViewModel(
    private val preferences: UserPreferences,
    private val googleAuthManager: GoogleAuthManager,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState(): SettingsUiState = SettingsUiState(
        briefingEnabled = preferences.briefingEnabled,
        briefingHour = preferences.briefingHour,
        briefingMinute = preferences.briefingMinute,
        weeklyReportEnabled = preferences.weeklyReportEnabled,
        eveningReviewEnabled = preferences.eveningReviewEnabled,
        eveningReviewHour = preferences.eveningReviewHour,
        eveningReviewMinute = preferences.eveningReviewMinute,
        notificationMiningEnabled = preferences.notificationMiningEnabled,
        autoSyncEnabled = preferences.autoSyncEnabled,
        googleEmail = preferences.googleAccountEmail,
        lastSyncTime = preferences.lastSyncTimestamp,
        aiEnabled = preferences.aiEnabled,
        hasApiKey = !preferences.anthropicApiKey.isNullOrBlank()
    )

    fun toggleAi(enabled: Boolean) {
        preferences.aiEnabled = enabled
        _uiState.value = _uiState.value.copy(aiEnabled = enabled)
    }

    fun updateApiKey(key: String) {
        preferences.anthropicApiKey = key
        _uiState.value = _uiState.value.copy(hasApiKey = !key.isBlank())
    }

    fun clearApiKey() {
        preferences.anthropicApiKey = null
        _uiState.value = _uiState.value.copy(hasApiKey = false)
    }

    fun toggleBriefing(enabled: Boolean) {
        preferences.briefingEnabled = enabled
        _uiState.value = _uiState.value.copy(briefingEnabled = enabled)
        if (enabled) {
            WorkManagerInitializer.scheduleMorningBriefing(
                context, preferences.briefingHour, preferences.briefingMinute
            )
        }
    }

    fun updateBriefingTime(hour: Int, minute: Int) {
        preferences.briefingHour = hour
        preferences.briefingMinute = minute
        _uiState.value = _uiState.value.copy(briefingHour = hour, briefingMinute = minute)
        if (preferences.briefingEnabled) {
            WorkManagerInitializer.scheduleMorningBriefing(context, hour, minute)
        }
    }

    fun toggleWeeklyReport(enabled: Boolean) {
        preferences.weeklyReportEnabled = enabled
        _uiState.value = _uiState.value.copy(weeklyReportEnabled = enabled)
        if (enabled) {
            WorkManagerInitializer.scheduleWeeklyReport(context)
        } else {
            WorkManagerInitializer.cancelWeeklyReport(context)
        }
    }

    fun toggleEveningReview(enabled: Boolean) {
        preferences.eveningReviewEnabled = enabled
        _uiState.value = _uiState.value.copy(eveningReviewEnabled = enabled)
        if (enabled) {
            WorkManagerInitializer.scheduleEveningReview(
                context, preferences.eveningReviewHour, preferences.eveningReviewMinute
            )
        }
    }

    fun updateEveningReviewTime(hour: Int, minute: Int) {
        preferences.eveningReviewHour = hour
        preferences.eveningReviewMinute = minute
        _uiState.value = _uiState.value.copy(eveningReviewHour = hour, eveningReviewMinute = minute)
        if (preferences.eveningReviewEnabled) {
            WorkManagerInitializer.scheduleEveningReview(context, hour, minute)
        }
    }

    fun toggleNotificationMining(enabled: Boolean) {
        preferences.notificationMiningEnabled = enabled
        _uiState.value = _uiState.value.copy(notificationMiningEnabled = enabled)
    }

    fun toggleAutoSync(enabled: Boolean) {
        preferences.autoSyncEnabled = enabled
        _uiState.value = _uiState.value.copy(autoSyncEnabled = enabled)
        if (enabled) {
            WorkManagerInitializer.scheduleSyncWorker(context)
        } else {
            WorkManagerInitializer.cancelSyncWorker(context)
        }
    }

    fun onGoogleSignInResult(email: String?) {
        preferences.googleAccountEmail = email
        _uiState.value = _uiState.value.copy(googleEmail = email)
    }

    fun signOut() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            preferences.googleAccountEmail = null
            preferences.autoSyncEnabled = false
            WorkManagerInitializer.cancelSyncWorker(context)
            _uiState.value = _uiState.value.copy(
                googleEmail = null,
                autoSyncEnabled = false
            )
        }
    }

    fun triggerManualSync() {
        _uiState.value = _uiState.value.copy(isSyncing = true)
        viewModelScope.launch {
            // SyncWorker handles the actual sync; here we just trigger it
            val workManager = androidx.work.WorkManager.getInstance(context)
            val request = androidx.work.OneTimeWorkRequestBuilder<com.neverdrop.data.worker.SyncWorker>().build()
            workManager.enqueue(request)
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }
}
