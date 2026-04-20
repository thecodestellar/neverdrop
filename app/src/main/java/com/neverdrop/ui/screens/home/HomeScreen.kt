package com.neverdrop.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neverdrop.ui.components.DeadlineCountdownCard
import com.neverdrop.ui.components.ReflectionPromptDialog
import com.neverdrop.ui.components.TaskCard
import com.neverdrop.domain.usecase.ForgettingProfileAnalyzer
import com.neverdrop.ui.screens.score.FollowThroughScoreCard
import com.neverdrop.ui.screens.score.ForgettingInsightsCard
import com.neverdrop.ui.screens.score.StreakCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCapture: () -> Unit,
    onNavigateToScreenshotCapture: () -> Unit = {},
    onNavigateToChatCapture: () -> Unit = {},
    onNavigateToRelationships: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reflectionTask by viewModel.reflectionTask.collectAsStateWithLifecycle()

    // Reflection prompt dialog
    reflectionTask?.let { task ->
        ReflectionPromptDialog(
            task = task,
            onDismiss = viewModel::dismissReflection,
            onSubmit = { feeling, note -> viewModel.submitReflection(feeling, note) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("NeverDrop", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onNavigateToChatCapture) {
                        Icon(Icons.Default.Chat, contentDescription = "Chat capture")
                    }
                    IconButton(onClick = onNavigateToScreenshotCapture) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Screenshot capture")
                    }
                    IconButton(onClick = onNavigateToRelationships) {
                        Icon(Icons.Default.People, contentDescription = "Relationships")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCapture) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FollowThroughScoreCard(score = uiState.followThroughScore)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    StreakCard(
                        tasks = uiState.allTasks,
                        streakDays = uiState.followThroughScore.streakDays
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.allTasks.size >= 5) {
                    item {
                        val profile = ForgettingProfileAnalyzer.analyze(uiState.allTasks)
                        if (profile.insights.isNotEmpty()) {
                            ForgettingInsightsCard(profile = profile)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Deadline countdown for critical tasks
                item {
                    DeadlineCountdownCard(
                        tasks = uiState.tasks.map { it.task }
                    )
                }

                if (uiState.tasks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No active tasks",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to capture your first commitment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    items(uiState.tasks, key = { it.task.id }) { taskWithUrgency ->
                        TaskCard(
                            taskWithUrgency = taskWithUrgency,
                            onComplete = { viewModel.completeTask(taskWithUrgency.task.id) },
                            onSnooze = { viewModel.snoozeTask(taskWithUrgency.task.id) },
                            onDrop = { viewModel.dropTask(taskWithUrgency.task.id) }
                        )
                    }
                }
            }
        }
    }
}
