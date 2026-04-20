package com.neverdrop.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neverdrop.data.repository.TaskRepository
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.usecase.FollowThroughScoreCalculator
import com.neverdrop.domain.usecase.UrgencyCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskWithUrgency(
    val task: Task,
    val urgencyScore: Double
)

data class HomeUiState(
    val tasks: List<TaskWithUrgency> = emptyList(),
    val allTasks: List<Task> = emptyList(),
    val followThroughScore: FollowThroughScoreCalculator.Score =
        FollowThroughScoreCalculator.Score(100.0, 0, 0, 0, 0, 0, 0),
    val isLoading: Boolean = true
)

class HomeViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _allTasks = repository.getAllTasks()
    private val _activeTasks = repository.getActiveTasks()
    private val _isLoading = MutableStateFlow(true)

    // Reflection prompt state
    private val _reflectionTask = MutableStateFlow<Task?>(null)
    val reflectionTask: StateFlow<Task?> = _reflectionTask.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        _activeTasks, _allTasks, _isLoading
    ) { active, all, loading ->
        val tasksWithUrgency = active
            .map { task -> TaskWithUrgency(task, UrgencyCalculator.calculate(task)) }
            .sortedByDescending { it.urgencyScore }

        HomeUiState(
            tasks = tasksWithUrgency,
            allTasks = all,
            followThroughScore = FollowThroughScoreCalculator.calculate(all),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun completeTask(taskId: Long) {
        // Find the task before completing to check if reflection is needed
        val taskWithUrgency = uiState.value.tasks.find { it.task.id == taskId }
        val task = taskWithUrgency?.task

        viewModelScope.launch {
            repository.completeTask(taskId)

            // Trigger reflection for high-urgency or heavily snoozed tasks
            if (task != null && shouldPromptReflection(task, taskWithUrgency.urgencyScore)) {
                _reflectionTask.value = task
            }
        }
    }

    fun snoozeTask(taskId: Long) {
        viewModelScope.launch { repository.snoozeTask(taskId) }
    }

    fun dropTask(taskId: Long) {
        viewModelScope.launch { repository.dropTask(taskId) }
    }

    fun dismissReflection() {
        _reflectionTask.value = null
    }

    fun submitReflection(feeling: String, note: String) {
        // Could persist reflections for deeper analysis in the future
        _reflectionTask.value = null
    }

    private fun shouldPromptReflection(task: Task, urgencyScore: Double): Boolean {
        return task.snoozeCount >= 2 || urgencyScore >= 70.0 || task.isOverdue
    }
}
