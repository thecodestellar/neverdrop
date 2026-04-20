package com.neverdrop.data.google

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

data class GmailSyncResult(
    val tasks: List<Task>,
    val syncedCount: Int,
    val error: String? = null
)

class GmailSyncService {

    companion object {
        private val ACTION_KEYWORDS = listOf(
            "action required", "action needed", "please respond",
            "follow up", "follow-up", "reminder", "deadline",
            "urgent", "asap", "by end of day", "by eod",
            "please review", "waiting for", "need your",
            "can you", "could you", "would you", "please send",
            "don't forget", "do not forget", "make sure"
        )

        private val PRIORITY_HIGH_KEYWORDS = listOf(
            "urgent", "asap", "immediately", "critical", "high priority"
        )
    }

    suspend fun syncRecentActionableEmails(
        credential: GoogleAccountCredential,
        maxResults: Long = 20
    ): GmailSyncResult = withContext(Dispatchers.IO) {
        try {
            val gmail = Gmail.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("NeverDrop").build()

            val query = "is:unread newer_than:3d"
            val messagesResponse = gmail.users().messages()
                .list("me")
                .setQ(query)
                .setMaxResults(maxResults)
                .execute()

            val messageIds = messagesResponse.messages ?: emptyList()
            val tasks = mutableListOf<Task>()

            for (msgRef in messageIds) {
                val message = gmail.users().messages()
                    .get("me", msgRef.id)
                    .setFormat("metadata")
                    .setMetadataHeaders(listOf("Subject", "From", "Date"))
                    .execute()

                val task = extractTaskFromMessage(message)
                if (task != null) {
                    tasks.add(task)
                }
            }

            GmailSyncResult(tasks = tasks, syncedCount = tasks.size)
        } catch (e: Exception) {
            GmailSyncResult(tasks = emptyList(), syncedCount = 0, error = e.message)
        }
    }

    private fun extractTaskFromMessage(message: Message): Task? {
        val headers = message.payload?.headers ?: return null
        val subject = headers.find { it.name.equals("Subject", true) }?.value ?: return null
        val from = headers.find { it.name.equals("From", true) }?.value ?: ""
        val snippet = message.snippet ?: ""

        val combinedText = "$subject $snippet".lowercase()
        val isActionable = ACTION_KEYWORDS.any { combinedText.contains(it) }

        if (!isActionable) return null

        val senderName = extractSenderName(from)
        val priority = if (PRIORITY_HIGH_KEYWORDS.any { combinedText.contains(it) }) {
            TaskPriority.HIGH
        } else {
            TaskPriority.MEDIUM
        }

        return Task(
            title = subject.take(100),
            description = "From: $from\n${snippet.take(200)}",
            commitmentType = CommitmentType.PROMISE_TO_SOMEONE,
            priority = priority,
            relatedPerson = senderName,
            createdAt = Instant.now()
        )
    }

    private fun extractSenderName(from: String): String {
        val nameMatch = Regex("^(.+?)\\s*<").find(from)
        return nameMatch?.groupValues?.get(1)?.trim()?.trim('"') ?: from.substringBefore("@")
    }
}
