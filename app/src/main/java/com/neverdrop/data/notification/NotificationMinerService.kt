package com.neverdrop.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.neverdrop.NeverDropApp
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Scans incoming notifications for time-sensitive items:
 * delivery ETAs, appointment confirmations, bill due dates, flight updates, etc.
 */
class NotificationMinerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationMiner"

        // Packages to ignore (our own app, system, etc.)
        private val IGNORED_PACKAGES = setOf(
            "com.neverdrop",
            "com.android.systemui",
            "android",
            "com.android.providers.downloads"
        )

        // Patterns that indicate actionable/time-sensitive content
        private val DELIVERY_PATTERNS = listOf(
            Regex("(?i)(deliver|arriving|shipped|out for delivery|package|tracking)"),
            Regex("(?i)(eta|estimated|expected).{0,20}(delivery|arrival)"),
            Regex("(?i)(order|shipment).{0,30}(on the way|dispatched|shipped)")
        )

        private val APPOINTMENT_PATTERNS = listOf(
            Regex("(?i)(appointment|booking|reservation).{0,20}(confirmed|reminder|upcoming|scheduled)"),
            Regex("(?i)(doctor|dentist|meeting|interview|consultation).{0,20}(at|on|scheduled)"),
            Regex("(?i)(check-?in|check in).{0,20}(available|open|now)")
        )

        private val BILL_PATTERNS = listOf(
            Regex("(?i)(bill|payment|invoice|due).{0,20}(due|pending|overdue|pay)"),
            Regex("(?i)(subscription|renewal|auto-?pay).{0,20}(due|renew|charge)"),
            Regex("(?i)(credit card|statement|balance).{0,20}(due|pay|minimum)")
        )

        private val TRAVEL_PATTERNS = listOf(
            Regex("(?i)(flight|gate|boarding|departure|terminal).{0,20}(changed|updated|reminder|at)"),
            Regex("(?i)(train|bus|ride|uber|lyft).{0,20}(arriving|eta|minutes away)")
        )

        private val EVENT_PATTERNS = listOf(
            Regex("(?i)(event|concert|show|game|match).{0,20}(starts|begins|tonight|today|tomorrow)"),
            Regex("(?i)(starts? in|begins? in|happening in)\\s+\\d+\\s*(min|hour|hr)")
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return

        val app = applicationContext as? NeverDropApp ?: return
        if (!app.userPreferences.notificationMiningEnabled) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val combined = "$title $text $bigText"
        if (combined.length < 10) return

        val match = categorize(combined) ?: return

        Log.d(TAG, "Mined from ${sbn.packageName}: ${match.category} — ${match.title}")

        scope.launch {
            // Check for duplicates
            val existing = app.taskRepository.getActiveTasksList()
            val isDuplicate = existing.any { it.title.equals(match.title, ignoreCase = true) }
            if (isDuplicate) return@launch

            app.taskRepository.addTask(match.toTask())
            Log.d(TAG, "Created task: ${match.title}")
        }
    }

    private data class MinedItem(
        val title: String,
        val category: String,
        val commitmentType: CommitmentType,
        val priority: TaskPriority,
        val description: String,
        val deadline: Instant? = null
    ) {
        fun toTask() = Task(
            title = title,
            description = description,
            commitmentType = commitmentType,
            priority = priority,
            deadline = deadline,
            createdAt = Instant.now()
        )
    }

    private fun categorize(text: String): MinedItem? {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        // Check delivery
        if (DELIVERY_PATTERNS.any { it.containsMatchIn(text) }) {
            return MinedItem(
                title = extractTitle(text, "Delivery"),
                category = "Delivery",
                commitmentType = CommitmentType.TIME_SENSITIVE_EVENT,
                priority = TaskPriority.MEDIUM,
                description = "Auto-captured from notification\n${text.take(200)}"
            )
        }

        // Check appointments
        if (APPOINTMENT_PATTERNS.any { it.containsMatchIn(text) }) {
            return MinedItem(
                title = extractTitle(text, "Appointment"),
                category = "Appointment",
                commitmentType = CommitmentType.TIME_SENSITIVE_EVENT,
                priority = TaskPriority.HIGH,
                description = "Auto-captured from notification\n${text.take(200)}",
                deadline = today.plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
            )
        }

        // Check bills
        if (BILL_PATTERNS.any { it.containsMatchIn(text) }) {
            return MinedItem(
                title = extractTitle(text, "Payment due"),
                category = "Bill/Payment",
                commitmentType = CommitmentType.RECURRING_DUTY,
                priority = TaskPriority.HIGH,
                description = "Auto-captured from notification\n${text.take(200)}",
                deadline = today.plusDays(3).atTime(LocalTime.of(17, 0)).atZone(zone).toInstant()
            )
        }

        // Check travel
        if (TRAVEL_PATTERNS.any { it.containsMatchIn(text) }) {
            return MinedItem(
                title = extractTitle(text, "Travel update"),
                category = "Travel",
                commitmentType = CommitmentType.TIME_SENSITIVE_EVENT,
                priority = TaskPriority.HIGH,
                description = "Auto-captured from notification\n${text.take(200)}"
            )
        }

        // Check events
        if (EVENT_PATTERNS.any { it.containsMatchIn(text) }) {
            return MinedItem(
                title = extractTitle(text, "Event"),
                category = "Event",
                commitmentType = CommitmentType.TIME_SENSITIVE_EVENT,
                priority = TaskPriority.MEDIUM,
                description = "Auto-captured from notification\n${text.take(200)}"
            )
        }

        return null
    }

    private fun extractTitle(text: String, fallbackCategory: String): String {
        // Use the first meaningful sentence as title
        val firstLine = text.split("\n").firstOrNull { it.length > 5 }?.trim() ?: text.trim()
        return firstLine.take(80).ifBlank { "$fallbackCategory notification" }
    }
}
