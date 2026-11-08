package com.kgyms.app.data.model

import kotlin.math.roundToInt

enum class WorkoutRecordStatus { Scheduled, Completed, Cancelled }

/** Workout and attendance for the same session must use the same canonical sessionId. */
data class WorkoutRecord(
    val sessionId: String,
    val occurredAtMillis: Long,
    val status: WorkoutRecordStatus,
    val confirmed: Boolean,
)

data class HomeWeeklyGoal(
    val target: Int,
    val weekStartMillis: Long,
    val weekEndExclusiveMillis: Long,
    val records: List<WorkoutRecord>,
    val asOfMillis: Long = Long.MAX_VALUE,
) {
    val completedSessions: List<WorkoutRecord> get() = records.filter {
        it.sessionId.isNotBlank() && it.status == WorkoutRecordStatus.Completed && it.confirmed &&
            it.occurredAtMillis >= weekStartMillis && it.occurredAtMillis < weekEndExclusiveMillis &&
            it.occurredAtMillis <= asOfMillis
    }.distinctBy { it.sessionId }
    val completed: Int get() = completedSessions.size
    val remaining: Int get() = (target - completed).coerceAtLeast(0)
    val progress: Float get() = if (target <= 0) 0f else (completed.toFloat() / target).coerceIn(0f, 1f)
    val percentage: Int get() = (progress * 100).roundToInt()
    val weekEndMillis: Long get() = weekEndExclusiveMillis - 1
}
