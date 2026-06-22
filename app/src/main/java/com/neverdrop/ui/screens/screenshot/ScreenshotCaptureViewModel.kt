package com.neverdrop.ui.screens.screenshot

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neverdrop.data.ai.AiEngine
import com.neverdrop.data.capture.ExtractedCommitment
import com.neverdrop.data.capture.ScreenshotAnalyzer
import com.neverdrop.data.preferences.UserPreferences
import com.neverdrop.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScreenshotUiState(
    val imageUri: Uri? = null,
    val isAnalyzing: Boolean = false,
    val extractedText: String = "",
    val commitments: List<ExtractedCommitment> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val savedCount: Int = 0,
    val error: String? = null
)

class ScreenshotCaptureViewModel(
    private val repository: TaskRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val analyzer = ScreenshotAnalyzer()

    private val _uiState = MutableStateFlow(ScreenshotUiState())
    val uiState: StateFlow<ScreenshotUiState> = _uiState.asStateFlow()

    fun onImageSelected(uri: Uri, context: Context) {
        _uiState.value = _uiState.value.copy(
            imageUri = uri,
            isAnalyzing = true,
            error = null,
            commitments = emptyList(),
            selectedIndices = emptySet(),
            savedCount = 0
        )

        viewModelScope.launch {
            try {
                val result = analyzer.analyzeImage(context, uri)

                // Prefer the AI engine (on-device Gemma, else cloud Claude) over regex heuristics.
                val commitments = AiEngine.extractFromScreenshot(preferences, result.fullText)
                    ?.takeIf { it.isNotEmpty() }
                    ?: result.commitments

                val allSelected = commitments.indices.toSet()
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    extractedText = result.fullText,
                    commitments = commitments,
                    selectedIndices = allSelected
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = e.message ?: "Failed to analyze image"
                )
            }
        }
    }

    fun toggleCommitment(index: Int) {
        val current = _uiState.value.selectedIndices.toMutableSet()
        if (current.contains(index)) {
            current.remove(index)
        } else {
            current.add(index)
        }
        _uiState.value = _uiState.value.copy(selectedIndices = current)
    }

    fun saveSelected() {
        val state = _uiState.value
        val selected = state.commitments.filterIndexed { index, _ -> index in state.selectedIndices }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            var count = 0
            for (commitment in selected) {
                val task = analyzer.commitmentToTask(commitment)
                repository.addTask(task)
                count++
            }
            _uiState.value = _uiState.value.copy(savedCount = count)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun reset() {
        _uiState.value = ScreenshotUiState()
    }
}
