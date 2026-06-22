package com.neverdrop.data.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.neverdrop.data.capture.ExtractedCommitment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * The on-device AI engine: runs a quantized Gemma model locally via MediaPipe LLM Inference,
 * so capture, extraction, and briefings can happen fully privately and offline — nothing
 * leaves the device.
 *
 * The model (a MediaPipe-compatible `.task` bundle, e.g. a Gemma instruction-tuned model) is
 * not bundled with the app — the user imports it once via Settings; it is copied into the
 * app's private storage. When no model is present, every method returns null so callers fall
 * back to the on-device heuristics (never silently to the cloud — see [AiEngine]).
 *
 * Inference is blocking and not reentrant, so calls are serialized on a mutex and run off the
 * main thread. Prompt text and JSON parsing are shared with the cloud engine via
 * [CommitmentParsing].
 */
object OnDeviceLlm {

    private const val MODEL_FILENAME = "gemma-it.task"
    private const val MAX_TOKENS = 1024

    @Volatile private var appContext: Context? = null
    @Volatile private var engine: LlmInference? = null
    private val mutex = Mutex()

    /** Wire up the application context once, at startup. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun modelFile(): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "models").apply { mkdirs() }
        return File(dir, MODEL_FILENAME)
    }

    /** True when a usable model is installed; gates whether on-device AI can run. */
    fun isModelPresent(): Boolean = modelFile()?.let { it.exists() && it.length() > 0 } == true

    fun isReady(): Boolean = appContext != null && isModelPresent()

    fun modelSizeBytes(): Long = modelFile()?.takeIf { it.exists() }?.length() ?: 0L

    /** Copy a user-selected model file into private storage. Replaces any existing model. */
    suspend fun importModel(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext Result.failure(IllegalStateException("Not initialized"))
        val dest = modelFile() ?: return@withContext Result.failure(IllegalStateException("No model path"))
        try {
            closeEngine()
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output, bufferSize = 1 shl 20) }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open model file"))
            if (dest.length() == 0L) {
                dest.delete()
                return@withContext Result.failure(IllegalStateException("Imported file was empty"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            dest.delete()
            Result.failure(e)
        }
    }

    fun deleteModel() {
        closeEngine()
        modelFile()?.delete()
    }

    suspend fun parseCommitment(input: String): ExtractedCommitment? {
        val prompt = gemmaPrompt(
            CommitmentParsing.SINGLE_SYSTEM + "\n\n" +
                CommitmentParsing.nowLine() + "\nMessage: \"" + input + "\""
        )
        val raw = generate(prompt) ?: return null
        return CommitmentParsing.parseObject(raw, sourceLine = input)
    }

    suspend fun extractFromScreenshot(fullText: String): List<ExtractedCommitment>? {
        val prompt = gemmaPrompt(
            CommitmentParsing.SCREENSHOT_SYSTEM + "\n\n" +
                CommitmentParsing.nowLine() + "\n\nScreenshot text:\n" + fullText
        )
        val raw = generate(prompt) ?: return null
        return CommitmentParsing.parseArray(raw)
    }

    suspend fun generateBriefing(stats: String): String? {
        val prompt = gemmaPrompt(CommitmentParsing.BRIEFING_SYSTEM + "\n\n" + stats)
        return generate(prompt)
    }

    // --- internals ---

    /** Gemma instruction-tuned chat template. */
    private fun gemmaPrompt(instruction: String): String =
        "<start_of_turn>user\n$instruction<end_of_turn>\n<start_of_turn>model\n"

    private suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isModelPresent()) return@withContext null
        mutex.withLock {
            try {
                val llm = ensureEngine() ?: return@withLock null
                llm.generateResponse(prompt)?.trim()?.ifBlank { null }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun ensureEngine(): LlmInference? {
        engine?.let { return it }
        val ctx = appContext ?: return null
        val file = modelFile() ?: return null
        if (!file.exists()) return null
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        return LlmInference.createFromOptions(ctx, options).also { engine = it }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
    }
}
