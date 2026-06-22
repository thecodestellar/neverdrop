package com.neverdrop.data.ai

import com.neverdrop.data.capture.ExtractedCommitment
import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.TaskPriority
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared prompt text and tolerant JSON parsing used by both the cloud (Claude) and the
 * on-device (Gemma) engines, so the two stay behaviourally aligned and the fragile bits
 * (date resolution, enum mapping, fence/prose stripping) live in one place.
 */
object CommitmentParsing {

    val SINGLE_SYSTEM = """
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

    val SCREENSHOT_SYSTEM = """
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

    val BRIEFING_SYSTEM = """
        You are NeverDrop, a supportive follow-through coach. Write the user's morning briefing
        from the stats below. Tone: warm, encouraging, never nagging or judgmental. 2-3 sentences.
        Acknowledge wins (streaks, high follow-through), gently flag what needs attention today,
        and end with a small nudge. Output only the briefing text — no headers, no lists.
    """.trimIndent()

    fun nowLine(): String =
        "Current time: ${DateTimeFormatter.ISO_INSTANT.format(Instant.now())} (timezone ${ZoneId.systemDefault()})."

    fun parseObject(raw: String, sourceLine: String): ExtractedCommitment? =
        readObject(raw)?.toCommitment(sourceLine)

    fun parseArray(raw: String): List<ExtractedCommitment>? {
        val cleaned = stripFences(raw)
        val arr = runCatching { JSONArray(cleaned) }.getOrNull()
            ?: sliceBetween(cleaned, '[', ']')?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (arr == null) {
            // Some models return a single object when there's one item — accept that too.
            return readObject(cleaned)?.toCommitment("Extracted from screenshot")?.let { listOf(it) }
        }
        val out = ArrayList<ExtractedCommitment>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            obj.toCommitment("Extracted from screenshot")?.let { out.add(it) }
        }
        return out
    }

    // --- internals ---

    private fun readObject(raw: String): JSONObject? {
        val cleaned = stripFences(raw)
        return runCatching { JSONObject(cleaned) }.getOrNull()
            ?: sliceBetween(cleaned, '{', '}')?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    /** Pull the first {...} or [...] block out of any surrounding prose a small model may add. */
    private fun sliceBetween(text: String, open: Char, close: Char): String? {
        val start = text.indexOf(open)
        val end = text.lastIndexOf(close)
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    private fun stripFences(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun JSONObject.toCommitment(sourceLine: String): ExtractedCommitment? {
        val title = optString("title").trim().ifBlank { return null }.take(100)
        val person = optString("person").trim()
            .takeIf { it.isNotBlank() && !it.equals("null", true) }
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

    private fun parseDeadline(value: String?): Instant? {
        val v = value?.trim()
        if (v.isNullOrBlank() || v.equals("null", true)) return null
        val zone = ZoneId.systemDefault()
        runCatching { return Instant.parse(v) }
        runCatching { return LocalDateTime.parse(v).atZone(zone).toInstant() }
        runCatching { return LocalDate.parse(v).atTime(LocalTime.of(17, 0)).atZone(zone).toInstant() }
        return null
    }
}
