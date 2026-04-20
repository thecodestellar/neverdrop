package com.neverdrop.ui.screens.relationships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neverdrop.data.repository.TaskRepository
import com.neverdrop.domain.usecase.RelationshipTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class RelationshipUiState(
    val summary: RelationshipTracker.RelationshipSummary? = null
)

class RelationshipViewModel(repository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RelationshipUiState())
    val uiState: StateFlow<RelationshipUiState> = _uiState.asStateFlow()

    init {
        repository.getAllTasks()
            .onEach { tasks ->
                val personTasks = tasks.filter { !it.relatedPerson.isNullOrBlank() }
                _uiState.value = if (personTasks.isEmpty()) {
                    RelationshipUiState(summary = null)
                } else {
                    RelationshipUiState(summary = RelationshipTracker.analyze(tasks))
                }
            }
            .launchIn(viewModelScope)
    }
}
