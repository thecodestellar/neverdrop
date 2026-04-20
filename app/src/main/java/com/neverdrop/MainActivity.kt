package com.neverdrop

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.compose.rememberNavController
import com.neverdrop.ui.navigation.NeverDropNavGraph
import com.neverdrop.ui.theme.NeverDropTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* User made their choice; no action needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as NeverDropApp

        setContent {
            NeverDropTheme {
                val navController = rememberNavController()
                NeverDropNavGraph(
                    navController = navController,
                    repository = app.taskRepository,
                    userPreferences = app.userPreferences,
                    googleAuthManager = app.googleAuthManager
                )
            }
        }
    }
}
