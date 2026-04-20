package com.neverdrop.domain.usecase

import com.neverdrop.domain.model.CommitmentType
import com.neverdrop.domain.model.Task
import com.neverdrop.domain.model.TaskStatus
import java.time.ZoneId

/**
 * Analyzes task patterns to build a personal forgetting profile.
 * Identifies when, what, and how the user tends to forget or drop tasks.
 */
object ForgettingProfileAnalyzer {

    data class ForgettingProfile(
        val insights: List<Insight>,
        val dropRateByHour: Map<Int, Double>,  // hour (0-23) -> drop rate
        val dropRateByType: Map<CommitmentType, Double>,
        val avgSnoozeBeforeDrop: Double,
        val totalAnalyzed: Int
    )

    data class Insight(
        val text: String,
        val severity: Severity // How actionable/important
    )

    enum class Severity { INFO, WARNING, CRITICAL }

    fun analyze(tasks: List<Task>): ForgettingProfile {
        if (tasks.size < 5) {
            return ForgettingProfile(
                insights = listOf(Insight("Not enough data yet. Keep using NeverDrop!", Severity.INFO)),
                dropRateByHour = emptyMap(),
                dropRateByType = emptyMap(),
                avgSnoozeBeforeDrop = 0.0,
                totalAnalyzed = tasks.size
            )
        }

        val insights = mutableListOf<Insight>()
        val zone = ZoneId.systemDefault()

        // 1. Analyze drop rate by capture hour
        val tasksByHour = tasks.groupBy { task ->
            task.createdAt.atZone(zone).hour
        }
        val dropRateByHour = tasksByHour.mapValues { (_, hourTasks) ->
            val dropped = hourTasks.count { it.status == TaskStatus.DROPPED }
            if (hourTasks.isEmpty()) 0.0 else dropped.toDouble() / hourTasks.size
        }

        // Find worst capture hours
        val worstHours = dropRateByHour.entries
            .filter { it.value > 0.3 && tasksByHour[it.key]!!.size >= 3 }
            .sortedByDescending { it.value }
            .take(2)

        for ((hour, rate) in worstHours) {
            val timeLabel = if (hour < 12) "$hour AM" else if (hour == 12) "12 PM" else "${hour - 12} PM"
            val pct = (rate * 100).toInt()
            insights.add(
                Insight(
                    "You drop $pct% of tasks captured around $timeLabel. Consider capturing these earlier in the day.",
                    if (rate > 0.5) Severity.CRITICAL else Severity.WARNING
                )
            )
        }

        // Late-night capture pattern
        val lateNightTasks = tasks.filter {
            val h = it.createdAt.atZone(zone).hour
            h >= 21 || h < 5
        }
        if (lateNightTasks.size >= 3) {
            val lateDropRate = lateNightTasks.count { it.status == TaskStatus.DROPPED }.toDouble() / lateNightTasks.size
            if (lateDropRate > 0.4) {
                val pct = (lateDropRate * 100).toInt()
                insights.add(
                    Insight(
                        "You forget $pct% of items captured after 9 PM. Try reviewing them first thing in the morning.",
                        Severity.WARNING
                    )
                )
            }
        }

        // 2. Analyze drop rate by commitment type
        val tasksByType = tasks.groupBy { it.commitmentType }
        val dropRateByType = tasksByType.mapValues { (_, typeTasks) ->
            val dropped = typeTasks.count { it.status == TaskStatus.DROPPED }
            if (typeTasks.isEmpty()) 0.0 else dropped.toDouble() / typeTasks.size
        }

        val worstType = dropRateByType.entries
            .filter { tasksByType[it.key]!!.size >= 3 && it.value > 0.3 }
            .maxByOrNull { it.value }

        worstType?.let { (type, rate) ->
            val typeName = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            val pct = (rate * 100).toInt()
            insights.add(
                Insight(
                    "You drop $pct% of \"$typeName\" tasks — more than other types.",
                    Severity.WARNING
                )
            )
        }

        // 3. Snooze analysis
        val droppedTasks = tasks.filter { it.status == TaskStatus.DROPPED }
        val avgSnoozeBeforeDrop = if (droppedTasks.isNotEmpty()) {
            droppedTasks.map { it.snoozeCount }.average()
        } else 0.0

        if (avgSnoozeBeforeDrop >= 3.0 && droppedTasks.size >= 3) {
            insights.add(
                Insight(
                    "You snooze tasks ${avgSnoozeBeforeDrop.toInt()} times on average before dropping them. Consider breaking big tasks into smaller steps.",
                    Severity.WARNING
                )
            )
        }

        // 4. People-related patterns
        val personTasks = tasks.filter { it.relatedPerson != null }
        if (personTasks.size >= 3) {
            val personDropRate = personTasks.count { it.status == TaskStatus.DROPPED }.toDouble() / personTasks.size
            val selfTasks = tasks.filter { it.relatedPerson == null }
            val selfDropRate = if (selfTasks.isNotEmpty()) {
                selfTasks.count { it.status == TaskStatus.DROPPED }.toDouble() / selfTasks.size
            } else 0.0

            if (personDropRate > selfDropRate + 0.15 && personDropRate > 0.3) {
                val pct = (personDropRate * 100).toInt()
                insights.add(
                    Insight(
                        "You drop $pct% of tasks involving other people. These are promises — they impact relationships.",
                        Severity.CRITICAL
                    )
                )
            } else if (selfDropRate > personDropRate + 0.15 && selfDropRate > 0.3) {
                insights.add(
                    Insight(
                        "You follow through well on promises to others but drop personal goals. Give self-goals the same weight!",
                        Severity.INFO
                    )
                )
            }
        }

        // 5. Positive insights
        val completedTasks = tasks.filter { it.status == TaskStatus.COMPLETED }
        if (completedTasks.size > droppedTasks.size * 3 && completedTasks.size >= 10) {
            insights.add(
                Insight(
                    "Strong follow-through! You complete ${completedTasks.size} tasks for every ${droppedTasks.size} dropped.",
                    Severity.INFO
                )
            )
        }

        val bestHours = dropRateByHour.entries
            .filter { it.value < 0.1 && tasksByHour[it.key]!!.size >= 3 }
            .sortedBy { it.value }
            .take(2)

        for ((hour, _) in bestHours) {
            val timeLabel = if (hour < 12) "$hour AM" else if (hour == 12) "12 PM" else "${hour - 12} PM"
            insights.add(
                Insight("Tasks captured around $timeLabel have the best completion rate.", Severity.INFO)
            )
        }

        return ForgettingProfile(
            insights = insights,
            dropRateByHour = dropRateByHour,
            dropRateByType = dropRateByType,
            avgSnoozeBeforeDrop = avgSnoozeBeforeDrop,
            totalAnalyzed = tasks.size
        )
    }
}
