package com.neverdrop.data.google

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

data class CalendarSyncResult(
    val tasks: List<Task>,
    val syncedCount: Int,
    val error: String? = null
)

class CalendarSyncService {

    suspend fun syncUpcomingEvents(
        credential: GoogleAccountCredential,
        daysAhead: Int = 7
    ): CalendarSyncResult = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("NeverDrop").build()

            val now = Instant.now()
            val until = now.plus(daysAhead.toLong(), ChronoUnit.DAYS)

            val events = calendar.events().list("primary")
                .setTimeMin(DateTime(now.toEpochMilli()))
                .setTimeMax(DateTime(until.toEpochMilli()))
                .setMaxResults(50)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()

            val tasks = events.items
                ?.mapNotNull { event -> eventToTask(event) }
                ?: emptyList()

            CalendarSyncResult(tasks = tasks, syncedCount = tasks.size)
        } catch (e: Exception) {
            CalendarSyncResult(tasks = emptyList(), syncedCount = 0, error = e.message)
        }
    }

    private fun eventToTask(event: Event): Task? {
        val summary = event.summary ?: return null
        if (summary.isBlank()) return null

        val startMillis = event.start?.dateTime?.value
            ?: event.start?.date?.value
            ?: return null

        val deadline = Instant.ofEpochMilli(startMillis)

        val attendees = event.attendees?.mapNotNull { it.displayName ?: it.email } ?: emptyList()
        val relatedPerson = attendees.firstOrNull()

        val commitmentType = if (attendees.isNotEmpty()) {
            CommitmentType.PROMISE_TO_SOMEONE
        } else {
            CommitmentType.TIME_SENSITIVE_EVENT
        }

        return Task(
            title = summary.take(100),
            description = buildDescription(event),
            commitmentType = commitmentType,
            priority = TaskPriority.HIGH,
            relatedPerson = relatedPerson,
            deadline = deadline,
            createdAt = Instant.now()
        )
    }

    private fun buildDescription(event: Event): String {
        val parts = mutableListOf<String>()

        event.location?.let { parts.add("Location: $it") }

        val attendeeNames = event.attendees
            ?.mapNotNull { it.displayName ?: it.email }
            ?.take(5)
        if (!attendeeNames.isNullOrEmpty()) {
            parts.add("With: ${attendeeNames.joinToString(", ")}")
        }

        event.description?.let {
            parts.add(it.take(200))
        }

        return parts.joinToString("\n")
    }
}
