package com.neverdrop.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neverdrop.data.preferences.UserPreferences
import com.neverdrop.data.repository.TaskRepository
import com.neverdrop.data.google.GoogleAuthManager
import com.neverdrop.ui.screens.capture.CaptureScreen
import com.neverdrop.ui.screens.capture.CaptureViewModel
import com.neverdrop.ui.screens.home.HomeScreen
import com.neverdrop.ui.screens.home.HomeViewModel
import com.neverdrop.ui.screens.screenshot.ScreenshotCaptureScreen
import com.neverdrop.ui.screens.screenshot.ScreenshotCaptureViewModel
import com.neverdrop.ui.screens.chat.ChatCaptureScreen
import com.neverdrop.ui.screens.chat.ChatCaptureViewModel
import com.neverdrop.ui.screens.relationships.RelationshipScreen
import com.neverdrop.ui.screens.relationships.RelationshipViewModel
import com.neverdrop.ui.screens.settings.SettingsScreen
import com.neverdrop.ui.screens.settings.SettingsViewModel

@Composable
fun NeverDropNavGraph(
    navController: NavHostController,
    repository: TaskRepository,
    userPreferences: UserPreferences,
    googleAuthManager: GoogleAuthManager
) {
    NavHost(navController = navController, startDestination = NavRoutes.HOME) {
        composable(NavRoutes.HOME) {
            val viewModel = remember { HomeViewModel(repository) }
            HomeScreen(
                viewModel = viewModel,
                onNavigateToCapture = { navController.navigate(NavRoutes.CAPTURE) },
                onNavigateToScreenshotCapture = { navController.navigate(NavRoutes.SCREENSHOT_CAPTURE) },
                onNavigateToChatCapture = { navController.navigate(NavRoutes.CHAT_CAPTURE) },
                onNavigateToRelationships = { navController.navigate(NavRoutes.RELATIONSHIPS) },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }
            )
        }
        composable(NavRoutes.CAPTURE) {
            val viewModel = remember { CaptureViewModel(repository) }
            CaptureScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.SCREENSHOT_CAPTURE) {
            val viewModel = remember { ScreenshotCaptureViewModel(repository, userPreferences) }
            ScreenshotCaptureScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.RELATIONSHIPS) {
            val viewModel = remember { RelationshipViewModel(repository) }
            RelationshipScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.CHAT_CAPTURE) {
            val viewModel = remember { ChatCaptureViewModel(repository, userPreferences) }
            ChatCaptureScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.SETTINGS) {
            val context = LocalContext.current
            val viewModel = remember {
                SettingsViewModel(userPreferences, googleAuthManager, context)
            }
            SettingsScreen(
                viewModel = viewModel,
                googleAuthManager = googleAuthManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
