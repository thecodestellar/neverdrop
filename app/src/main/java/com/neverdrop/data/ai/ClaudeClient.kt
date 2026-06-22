package com.neverdrop.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.neverdrop.data.capture.ExtractedCommitment
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The AI Intelligence Engine: a thin wrapper over the Anthropic (Claude) SDK that powers
 * natural-language capture, screenshot commitment extraction, and the morning briefing.
 *
 * Every public method is best-effort and returns null (or an empty list) on any failure —
 * missing key, no network, malformed model output — so callers can transparently fall back
 * to the on-device heuristic parsers. Nothing here ever throws to the caller.
 *
 * The Claude model is reached on a background dispatcher; the OkHttp-backed client is cached
 * and only rebuilt when the API key changes.
 */
object ClaudeClient {

    private const val MODEL = "claude-opus-4-8"

    @Volatile private var cachedKey: String? = null
    @Volatile private var cachedClient: AnthropicClient? = null

    private fun clientFor(apiKey: String): AnthropicClient {
        val existing = cachedClient
        if (existing != null && cachedKey == apiKey) return existing
        val built = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        synchronized(this) {
            cachedClient = built
            cachedKey = apiKey
        }
        return built
    }

    /** Parse a single free-text capture ("I promised Sarah the report by Friday") into a commitment. */
    suspend fun parseCommitment(apiKey: String, input: String): ExtractedCommitment? {
        val system = """
            You are NeverDrop's capture engine. The user speaks or types a thought; extract the
            single commitment behind it. A commitment is something the user must do or follow up on.

            Respond with ONLY a JSON object — no prose, no markdown fences — with these fields:
            - "title": a short imperative task title (strip filler like "remind me to"). Required.
            - "person": the other person involved, or null.
            - "deadline": an ISO-8601 instant in UTC (e.g. "2026-06-23T17:00:00Z"), or null if none implied.
            - "priority": one of "LOW", "MEDIUM", "HIGH".
            - "type": one of "PROMISE_TO_SOMEONE", "SELF_GOAL", "RECURRING_DUTY",
              "TIME_SENSITIVE_EVENT", "RELATIONSHIP_MAINTENANCE".
            Resolve relative dates ("tomorrow", "Friday", "EOD") against the provided current time.
        """.trimIndent()

        val user = "Current time: ${nowIso()} (timezone ${ZoneId.systemDefault()}).\nMessage: \"$input\""

        val raw = complete(apiKey, system, user, maxTokens = 512) ?: return null
        val obj = runCatching { JSONObject(stripFences(raw)) }.getOrNull() ?: return null
        return obj.toCommitment(sourceLine = input)
    }

    /** Extract every actionable commitment from OCR'd screenshot text (chat/email). */
    suspend fun extractFromScreenshot(apiKey: String, fullText: String): List<ExtractedCommitment>? {
        val system = """
            You are NeverDrop's capture engine reading text extracted from a screenshot of a chat
            or email. Identify every commitment, follow-up, request, deadline, or appointment that
            warrants a tracked task. Ignore greetings, signatures, and idle chatter.

            Respond with ONLY a JSON array (possibly empty) of objects — no prose, no markdown
            fences. Each object has these fields:
            - "title": a short imperative task title. Required.
            - "person": the sender/requester involved, or null.
            - "deadline": an ISO-8601 instant in UTC, or null.
            - "priority": one of "LOW", "MEDIUM", "HIGH".
            - "type": one of "PROMISE_TO_SOMEONE", "SELF_GOAL", "RECURRING_DUTY",
              "TIME_SENSITIVE_EVENT", "RELATIONSHIP_MAINTENANCE".
            Resolve relative dates against the provided current time.
        """.trimIndent()

        val user = "Current time: ${nowIso()} (timezone ${ZoneId.systemDefault()}).\n\nScreenshot text:\n$fullText"

        val raw = complete(apiKey, system, user, maxTokens = 1024) ?: return null
        val arr = runCatching { JSONArray(stripFences(raw)) }.getOrNull() ?: return null
        val out = ArrayList<ExtractedCommitment>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            obj.toCommitment(sourceLine = "Extracted from screenshot")?.let { out.add(it) }
        }
        return out
    }

    /** Generate a warm, coach-not-cop morning briefing from the day's stats. Returns null on failure. */
    suspend fun generateBriefing(apiKey: String, stats: String): String? {
        val system = """
            You are NeverDrop, a supportive follow-through coach. Write the user's morning briefing
            from the stats below. Tone: warm, encouraging, never nagging or judgmental. 2-3 sentences.
            Acknowledge wins (streaks, high follow-through), gently flag what needs attention today,
            and end with a small nudge. Output only the briefing text — no headers, no lists.
        """.trimIndent()

        return complete(apiKey, system, stats, maxTokens = 400)
    }

    // --- internals ---

    private suspend fun complete(
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Long
    ): String? = withContext(Dispatchers.IO) {
        try {
            val params = MessageCreateParams.builder()
                .model(Model.of(MODEL))
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(user)
                .build()
            val response = clientFor(apiKey).messages().create(params)
            val sb = StringBuilder()
            response.content().forEach { block -> block.text().ifPresent { sb.append(it.text()) } }
            sb.toString().trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun nowIso(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    /** Strip ```json ... ``` fences a model occasionally adds despite instructions. */
    private fun stripFences(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun JSONObject.toCommitment(sourceLine: String): ExtractedCommitment? {
        val title = optString("title").trim().ifBlank { return null }.take(100)
        val person = optString("person").trim().takeIf { it.isNotBlank() && !it.equals("null", true) }
        return ExtractedCommitment(
            title = title,
            sender = person,
            deadline = parseDeadline(optString("deadline")),
            priority = parsePriority(optString("priority")),
            commitmentType = parseType(optString("type"), hasPerson = person != null),
            sourceLine = sourceLine
        )
    }

    private fun parsePriority(value: String?): TaskPriority = when (value?.trim()?.uppercase()) {
        "HIGH" -> TaskPriority.HIGH
        "LOW" -> TaskPriority.LOW
        else -> TaskPriority.MEDIUM
    }

    private fun parseType(value: String?, hasPerson: Boolean): CommitmentType =
        runCatching { CommitmentType.valueOf(value!!.trim().uppercase()) }.getOrElse {
            if (hasPerson) CommitmentType.PROMISE_TO_SOMEONE else CommitmentType.SELF_GOAL
        }

    /** Tolerant deadline parsing: ISO instant, then local date-time, then plain date at 5pm. */
    private fun parseDeadline(value: String?): Instant? {
        val v = value?.trim()
        if (v.isNullOrBlank() || v.equals("null", true)) return null
        val zone = ZoneId.systemDefault()
        runCatching { return Instant.parse(v) }
        runCatching { return LocalDateTime.parse(v).atZone(zone).toInstant() }
        runCatching {
            return LocalDate.parse(v).atTime(LocalTime.of(17, 0)).atZone(zone).toInstant()
        }
        return null
    }
}
