package com.neverdrop.ui.screens.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neverdrop.domain.usecase.ForgettingProfileAnalyzer

@Composable
fun ForgettingInsightsCard(
    profile: ForgettingProfileAnalyzer.ForgettingProfile,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Your Forgetting Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Based on ${profile.totalAnalyzed} tasks analyzed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            profile.insights.forEach { insight ->
                InsightRow(insight)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (profile.dropRateByHour.isNotEmpty() && profile.totalAnalyzed >= 10) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Average snoozes before dropping: ${String.format("%.1f", profile.avgSnoozeBeforeDrop)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun InsightRow(insight: ForgettingProfileAnalyzer.Insight) {
    val (icon, color) = when (insight.severity) {
        ForgettingProfileAnalyzer.Severity.CRITICAL -> "!!" to Color(0xFFE53935)
        ForgettingProfileAnalyzer.Severity.WARNING -> "!" to Color(0xFFFFC107)
        ForgettingProfileAnalyzer.Severity.INFO -> "i" to Color(0xFF4CAF50)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Card(
            modifier = Modifier.size(24.dp),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f))
        ) {
            Text(
                icon,
                modifier = Modifier.padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            insight.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
