package com.neverdrop.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neverdrop.data.repository.TaskRepository
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Instant = Instant.now()
)

data class ChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "Hey! Tell me what you need to remember. I'll turn it into a task.\n\nTry something like:\n\"Remind me to call the dentist tomorrow\"\n\"I promised Sarah I'd send the report by Friday\"",
            isUser = false
        )
    ),
    val inputText: String = ""
)

class ChatCaptureViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val userMessage = ChatMessage(text = text, isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = ""
        )

        viewModelScope.launch {
            val parsed = parseNaturalLanguage(text)
            repository.addTask(parsed.task)

            val response = buildResponse(parsed)
            val botMessage = ChatMessage(text = response, isUser = false)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botMessage
            )
        }
    }

    private data class ParsedInput(
        val task: Task,
        val detectedPerson: String?,
        val detectedDeadline: String?,
        val detectedPriority: String?
    )

    private fun parseNaturalLanguage(input: String): ParsedInput {
        val lower = input.lowercase()
        val today = LocalDate.now()

        // Extract person
        val person = extractPerson(input)

        // Extract deadline
        val (deadline, deadlineLabel) = extractDeadline(lower, today)

        // Extract priority
        val (priority, priorityLabel) = extractPriority(lower)

        // Extract commitment type
        val commitmentType = when {
            person != null -> CommitmentType.PROMISE_TO_SOMEONE
            lower.contains("meeting") || lower.contains("appointment") || lower.contains("event") ->
                CommitmentType.TIME_SENSITIVE_EVENT
            lower.contains("every") || lower.contains("daily") || lower.contains("weekly") ->
                CommitmentType.RECURRING_DUTY
            else -> CommitmentType.SELF_GOAL
        }

        // Clean the title
        val title = cleanTitle(input)

        val task = Task(
            title = title,
            commitmentType = commitmentType,
            priority = priority,
            relatedPerson = person,
            deadline = deadline,
            createdAt = Instant.now()
        )

        return ParsedInput(task, person, deadlineLabel, priorityLabel)
    }

    private fun extractPerson(input: String): String? {
        val patterns = listOf(
            Regex("(?i)(?:promised|told|owe)\\s+([A-Z][a-z]+)"),
            Regex("(?i)(?:email|call|text|message|tell|remind)\\s+([A-Z][a-z]+)"),
            Regex("(?i)(?:for|with|to)\\s+([A-Z][a-z]+)(?:\\s|$|,|\\.)"),
            Regex("(?i)([A-Z][a-z]+)(?:'s|\\s+asked|\\s+wants|\\s+needs)")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                val name = match.groupValues[1]
                // Filter out common words that aren't names
                val nonNames = setOf("Remind", "Send", "Call", "Email", "Tell", "Make", "Need",
                    "Get", "Check", "Review", "Update", "Follow", "Monday", "Tuesday",
                    "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Tomorrow", "Today")
                if (name !in nonNames) return name
            }
        }
        return null
    }

    private fun extractDeadline(lower: String, today: LocalDate): Pair<Instant?, String?> {
        val zone = ZoneId.systemDefault()
        val eod = LocalTime.of(17, 0)

        return when {
            lower.contains("today") || lower.contains("eod") || lower.contains("end of day") ->
                today.atTime(eod).atZone(zone).toInstant() to "today"

            lower.contains("tomorrow") ->
                today.plusDays(1).atTime(eod).atZone(zone).toInstant() to "tomorrow"

            lower.contains("end of week") || lower.contains("eow") -> {
                val daysToFri = (5 - today.dayOfWeek.value + 7) % 7
                val fri = if (daysToFri == 0) today else today.plusDays(daysToFri.toLong())
                fri.atTime(eod).atZone(zone).toInstant() to "end of week"
            }

            lower.contains("next week") ->
                today.plusDays(7).atTime(eod).atZone(zone).toInstant() to "next week"

            else -> {
                val dayPattern = Regex("(?i)(?:by|on|this)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)")
                val match = dayPattern.find(lower)
                if (match != null) {
                    val targetDay = java.time.DayOfWeek.valueOf(match.groupValues[1].uppercase())
                    val daysAhead = (targetDay.value - today.dayOfWeek.value + 7) % 7
                    val targetDate = if (daysAhead == 0) today.plusDays(7) else today.plusDays(daysAhead.toLong())
                    targetDate.atTime(eod).atZone(zone).toInstant() to match.groupValues[1].lowercase()
                } else {
                    null to null
                }
            }
        }
    }

    private fun extractPriority(lower: String): Pair<TaskPriority, String?> {
        return when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("critical") ||
                lower.contains("important") || lower.contains("high priority") ->
                TaskPriority.HIGH to "high"

            lower.contains("low priority") || lower.contains("whenever") ||
                lower.contains("no rush") || lower.contains("when you can") ->
                TaskPriority.LOW to "low"

            else -> TaskPriority.MEDIUM to null
        }
    }

    private fun cleanTitle(input: String): String {
        return input
            .replace(Regex("(?i)^(remind me to|i need to|don't forget to|remember to|i have to|i should|i must|i promised to)\\s*"), "")
            .replace(Regex("(?i)^(can you|could you|please)\\s*"), "")
            .trim()
            .replaceFirstChar { it.uppercase() }
            .take(100)
    }

    private fun buildResponse(parsed: ParsedInput): String {
        val parts = mutableListOf<String>()
        parts.add("Got it! I've captured:")
        parts.add("\"${parsed.task.title}\"")

        val details = mutableListOf<String>()
        parsed.detectedPerson?.let { details.add("Person: $it") }
        parsed.detectedDeadline?.let { details.add("Due: $it") }
        parsed.detectedPriority?.let { details.add("Priority: $it") }

        if (details.isNotEmpty()) {
            parts.add(details.joinToString(" | "))
        }

        parts.add("\nAnything else to capture?")
        return parts.joinToString("\n")
    }
}
