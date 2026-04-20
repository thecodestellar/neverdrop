package com.neverdrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun UrgencyIndicator(score: Double, modifier: Modifier = Modifier) {
    val color = when {
        score >= 75 -> Color(0xFFE53935) // Red — critical
        score >= 50 -> Color(0xFFFF9800) // Orange — high
        score >= 25 -> Color(0xFFFFC107) // Amber — medium
        else -> Color(0xFF4CAF50)         // Green — low
    }

    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
