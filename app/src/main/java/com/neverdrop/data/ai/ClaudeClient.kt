package com.neverdrop.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.neverdrop.data.capture.ExtractedCommitment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The cloud AI engine: a thin wrapper over the official Anthropic (Claude) SDK that powers
 * natural-language capture, screenshot commitment extraction, and the morning briefing.
 *
 * Every public method is best-effort and returns null (or an empty list) on any failure —
 * missing key, no network, malformed model output — so callers can transparently fall back.
 * Nothing here ever throws to the caller. Prompt text and JSON parsing are shared with the
 * on-device engine via [CommitmentParsing].
 *
 * The OkHttp-backed client is cached and only rebuilt when the API key changes.
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

    suspend fun parseCommitment(apiKey: String, input: String): ExtractedCommitment? {
        val user = "${CommitmentParsing.nowLine()}\nMessage: \"$input\""
        val raw = complete(apiKey, CommitmentParsing.SINGLE_SYSTEM, user, maxTokens = 512) ?: return null
        return CommitmentParsing.parseObject(raw, sourceLine = input)
    }

    suspend fun extractFromScreenshot(apiKey: String, fullText: String): List<ExtractedCommitment>? {
        val user = "${CommitmentParsing.nowLine()}\n\nScreenshot text:\n$fullText"
        val raw = complete(apiKey, CommitmentParsing.SCREENSHOT_SYSTEM, user, maxTokens = 1024) ?: return null
        return CommitmentParsing.parseArray(raw)
    }

    suspend fun generateBriefing(apiKey: String, stats: String): String? =
        complete(apiKey, CommitmentParsing.BRIEFING_SYSTEM, stats, maxTokens = 400)

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
}
