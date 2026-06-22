package com.neverdrop.data.ai

import com.neverdrop.data.capture.ExtractedCommitment
import com.neverdrop.data.preferences.UserPreferences

/**
 * Routes AI work across the available engines with a privacy-first policy:
 *
 *   1. AI off            -> null (caller uses on-device heuristics)
 *   2. On-device ready   -> Gemma (private, offline). Does NOT fall back to the cloud, so a
 *                           user who wants privacy gets it: a failed local run degrades to the
 *                           local heuristics, never to the network.
 *   3. Cloud key present -> Claude (most capable)
 *   4. otherwise         -> null
 *
 * To force fully-private operation, the user keeps the on-device model installed (and/or
 * removes the API key). Every method returns null on failure so callers degrade gracefully.
 */
object AiEngine {

    suspend fun parseCommitment(prefs: UserPreferences, text: String): ExtractedCommitment? {
        if (!prefs.aiEnabled) return null
        if (prefs.onDeviceAiEnabled && OnDeviceLlm.isReady()) {
            return OnDeviceLlm.parseCommitment(text)
        }
        val key = prefs.anthropicApiKey
        if (!key.isNullOrBlank()) return ClaudeClient.parseCommitment(key, text)
        return null
    }

    suspend fun extractFromScreenshot(prefs: UserPreferences, fullText: String): List<ExtractedCommitment>? {
        if (!prefs.aiEnabled || fullText.isBlank()) return null
        if (prefs.onDeviceAiEnabled && OnDeviceLlm.isReady()) {
            return OnDeviceLlm.extractFromScreenshot(fullText)
        }
        val key = prefs.anthropicApiKey
        if (!key.isNullOrBlank()) return ClaudeClient.extractFromScreenshot(key, fullText)
        return null
    }

    suspend fun generateBriefing(prefs: UserPreferences, stats: String): String? {
        if (!prefs.aiEnabled) return null
        if (prefs.onDeviceAiEnabled && OnDeviceLlm.isReady()) {
            return OnDeviceLlm.generateBriefing(stats)
        }
        val key = prefs.anthropicApiKey
        if (!key.isNullOrBlank()) return ClaudeClient.generateBriefing(key, stats)
        return null
    }
}
