package com.neverdrop.ui.screens.capture

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

enum class VoiceTargetField { TITLE, DESCRIPTION }

data class CaptureUiState(
    val title: String = "",
    val description: String = "",
    val relatedPerson: String = "",
    val commitmentType: CommitmentType = CommitmentType.SELF_GOAL,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val hasDeadline: Boolean = false,
    val deadlineDate: LocalDate? = null,
    val deadlineTime: LocalTime = LocalTime.of(17, 0),
    val isSaved: Boolean = false,
    val isListening: Boolean = false,
    val activeVoiceField: VoiceTargetField? = null,
    val voiceError: String? = null
)

class CaptureViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateRelatedPerson(person: String) {
        _uiState.value = _uiState.value.copy(relatedPerson = person)
    }

    fun updateCommitmentType(type: CommitmentType) {
        _uiState.value = _uiState.value.copy(commitmentType = type)
    }

    fun updatePriority(priority: TaskPriority) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun toggleDeadline(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasDeadline = enabled,
            deadlineDate = if (enabled) LocalDate.now().plusDays(1) else null
        )
    }

    fun updateDeadlineDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(deadlineDate = date)
    }

    fun startVoiceInput(field: VoiceTargetField) {
        _uiState.value = _uiState.value.copy(
            activeVoiceField = field,
            voiceError = null
        )
    }

    fun onVoiceResult(text: String) {
        val state = _uiState.value
        when (state.activeVoiceField) {
            VoiceTargetField.TITLE -> _uiState.value = state.copy(title = text)
            VoiceTargetField.DESCRIPTION -> {
                val existing = state.description
                val newText = if (existing.isBlank()) text else "$existing $text"
                _uiState.value = state.copy(description = newText)
            }
            null -> {}
        }
    }

    fun onVoicePartialResult(text: String) {
        val state = _uiState.value
        when (state.activeVoiceField) {
            VoiceTargetField.TITLE -> _uiState.value = state.copy(title = text)
            VoiceTargetField.DESCRIPTION -> {} // Don't update partial for description to avoid flicker
            null -> {}
        }
    }

    fun onListeningStateChanged(isListening: Boolean) {
        _uiState.value = _uiState.value.copy(
            isListening = isListening,
            activeVoiceField = if (!isListening) null else _uiState.value.activeVoiceField
        )
    }

    fun onVoiceError(message: String) {
        _uiState.value = _uiState.value.copy(
            voiceError = message,
            isListening = false,
            activeVoiceField = null
        )
    }

    fun clearVoiceError() {
        _uiState.value = _uiState.value.copy(voiceError = null)
    }

    fun saveTask() {
        val state = _uiState.value
        if (state.title.isBlank()) return

        val deadline = if (state.hasDeadline && state.deadlineDate != null) {
            state.deadlineDate.atTime(state.deadlineTime)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        } else null

        val task = Task(
            title = state.title.trim(),
            description = state.description.trim(),
            commitmentType = state.commitmentType,
            priority = state.priority,
            relatedPerson = state.relatedPerson.trim().ifEmpty { null },
            deadline = deadline,
            createdAt = Instant.now()
        )

        viewModelScope.launch {
            repository.addTask(task)
            _uiState.value = CaptureUiState(isSaved = true)
        }
    }
}
