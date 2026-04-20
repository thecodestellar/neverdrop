package com.neverdrop.data.capture

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractedCommitment(
    val title: String,
    val sender: String? = null,
    val deadline: Instant? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val commitmentType: CommitmentType = CommitmentType.PROMISE_TO_SOMEONE,
    val sourceLine: String
)

class ScreenshotAnalyzer {

    companion object {
        private val ACTION_PATTERNS = listOf(
            Regex("(?i)(please|pls|kindly)\\s+(send|share|submit|review|check|update|call|email|complete|finish|prepare|confirm|respond|reply)", RegexOption.IGNORE_CASE),
            Regex("(?i)(can|could|would)\\s+you\\s+(please\\s+)?(send|share|submit|review|check|update|call|email|complete|do)", RegexOption.IGNORE_CASE),
            Regex("(?i)(don'?t\\s+forget|make\\s+sure|remember)\\s+to", RegexOption.IGNORE_CASE),
            Regex("(?i)(action\\s+required|action\\s+needed|follow\\s*up|follow\\s*-\\s*up)", RegexOption.IGNORE_CASE),
            Regex("(?i)(deadline|due\\s+by|due\\s+date|by\\s+eod|by\\s+end\\s+of\\s+day|by\\s+tomorrow|by\\s+friday|by\\s+monday)", RegexOption.IGNORE_CASE),
            Regex("(?i)(urgent|asap|immediately|critical|high\\s+priority)", RegexOption.IGNORE_CASE),
            Regex("(?i)(i('ll|\\s+will)\\s+(send|share|do|call|check|get|finish|complete))", RegexOption.IGNORE_CASE),
            Regex("(?i)(need\\s+(you|your|to)|waiting\\s+for)", RegexOption.IGNORE_CASE),
            Regex("(?i)(meeting|appointment|call)\\s+(at|on|scheduled)", RegexOption.IGNORE_CASE),
            Regex("(?i)(rsvp|register|sign\\s*up|enroll)", RegexOption.IGNORE_CASE)
        )

        private val URGENCY_KEYWORDS = listOf("urgent", "asap", "immediately", "critical", "high priority", "eod", "end of day")

        private val DEADLINE_PATTERNS = listOf(
            Regex("(?i)by\\s+(tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday)", RegexOption.IGNORE_CASE),
            Regex("(?i)by\\s+(eod|end\\s+of\\s+day|end\\s+of\\s+week|eow)", RegexOption.IGNORE_CASE),
            Regex("(?i)(due|deadline)\\s*:?\\s*(\\w+\\s+\\d+|tomorrow|today)", RegexOption.IGNORE_CASE),
            Regex("(?i)\\b(\\d{1,2}[/\\-]\\d{1,2}([/\\-]\\d{2,4})?)\\b")
        )

        private val SENDER_PATTERNS = listOf(
            Regex("^([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)\\s*$"),           // "John Doe" on its own line
            Regex("^([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)\\s*[:\\-]"),       // "John Doe:" or "John Doe -"
            Regex("(?i)from\\s*:?\\s*([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)") // "From: John Doe"
        )
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    suspend fun analyzeImage(context: Context, imageUri: Uri): AnalysisResult {
        val image = InputImage.fromFilePath(context, imageUri)
        val fullText = recognizeText(image)

        if (fullText.isBlank()) {
            return AnalysisResult(fullText = "", commitments = emptyList())
        }

        val commitments = extractCommitments(fullText)
        return AnalysisResult(fullText = fullText, commitments = commitments)
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    private fun extractCommitments(fullText: String): List<ExtractedCommitment> {
        val lines = fullText.lines().filter { it.isNotBlank() }
        val commitments = mutableListOf<ExtractedCommitment>()
        val sender = extractSender(lines)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length < 5) continue

            val isActionable = ACTION_PATTERNS.any { it.containsMatchIn(trimmed) }
            if (!isActionable) continue

            val priority = if (URGENCY_KEYWORDS.any { trimmed.lowercase().contains(it) }) {
                TaskPriority.HIGH
            } else {
                TaskPriority.MEDIUM
            }

            val deadline = extractDeadline(trimmed)

            val commitmentType = if (sender != null) {
                CommitmentType.PROMISE_TO_SOMEONE
            } else {
                CommitmentType.SELF_GOAL
            }

            commitments.add(
                ExtractedCommitment(
                    title = cleanTitle(trimmed),
                    sender = sender,
                    deadline = deadline,
                    priority = priority,
                    commitmentType = commitmentType,
                    sourceLine = trimmed
                )
            )
        }

        return commitments
    }

    private fun extractSender(lines: List<String>): String? {
        for (line in lines.take(5)) {
            for (pattern in SENDER_PATTERNS) {
                val match = pattern.find(line.trim())
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
        }
        return null
    }

    private fun extractDeadline(text: String): Instant? {
        val lower = text.lowercase()
        val today = LocalDate.now()

        return when {
            lower.contains("today") || lower.contains("eod") || lower.contains("end of day") -> {
                today.atTime(LocalTime.of(17, 0)).atZone(ZoneId.systemDefault()).toInstant()
            }
            lower.contains("tomorrow") -> {
                today.plusDays(1).atTime(LocalTime.of(17, 0)).atZone(ZoneId.systemDefault()).toInstant()
            }
            lower.contains("end of week") || lower.contains("eow") -> {
                val daysUntilFriday = (5 - today.dayOfWeek.value + 7) % 7
                val friday = if (daysUntilFriday == 0) today else today.plusDays(daysUntilFriday.toLong())
                friday.atTime(LocalTime.of(17, 0)).atZone(ZoneId.systemDefault()).toInstant()
            }
            else -> {
                val dayPattern = Regex("(?i)by\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)")
                val dayMatch = dayPattern.find(text)
                if (dayMatch != null) {
                    val targetDay = java.time.DayOfWeek.valueOf(dayMatch.groupValues[1].uppercase())
                    val daysAhead = (targetDay.value - today.dayOfWeek.value + 7) % 7
                    val targetDate = if (daysAhead == 0) today.plusDays(7) else today.plusDays(daysAhead.toLong())
                    targetDate.atTime(LocalTime.of(17, 0)).atZone(ZoneId.systemDefault()).toInstant()
                } else {
                    null
                }
            }
        }
    }

    private fun cleanTitle(line: String): String {
        return line
            .replace(Regex("^[-•*>]+\\s*"), "")
            .replace(Regex("^\\d+[.)\\-]\\s*"), "")
            .trim()
            .take(100)
    }

    fun commitmentToTask(commitment: ExtractedCommitment): Task {
        return Task(
            title = commitment.title,
            description = "Extracted from screenshot\nSource: ${commitment.sourceLine}",
            commitmentType = commitment.commitmentType,
            priority = commitment.priority,
            relatedPerson = commitment.sender,
            deadline = commitment.deadline,
            createdAt = Instant.now()
        )
    }

    data class AnalysisResult(
        val fullText: String,
        val commitments: List<ExtractedCommitment>
    )
}
