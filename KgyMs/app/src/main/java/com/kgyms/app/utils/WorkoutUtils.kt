package com.kgyms.app.utils

import java.util.Calendar
import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.model.WorkoutRecordStatus
import com.kgyms.app.data.model.ExerciseUiModel
import com.kgyms.app.data.model.SessionStatus
import com.kgyms.app.data.model.WorkoutCatalog

internal fun workoutMonday(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = now
    add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Local rehearsal only. Never exports a confirmed attendance record. */
internal fun trackingKey(week: Long, sessionId: String, exerciseId: String) = "$week:$sessionId:$exerciseId"

internal fun toggleWorkoutMark(marks: List<String>, key: String): List<String> =
    if (key in marks) marks.filterNot { it == key } else marks + key

/** Only the repository/confirmation boundary supplies these records; checkbox state is never an input. */
internal fun WorkoutCatalog.confirmedRecords(): List<WorkoutRecord> = sessions.map { s ->
    WorkoutRecord(s.id, s.completedAt ?: s.scheduledAt,
        if (s.status == SessionStatus.Confirmed && s.completedAt != null) WorkoutRecordStatus.Completed else WorkoutRecordStatus.Scheduled,
        s.confirmedAt != null && s.completedAt != null)
}

internal fun filterCatalogExercises(items: List<ExerciseUiModel>, query: String, level: String, muscle: String, category: String): List<ExerciseUiModel> {
    fun String.normalized() = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ','d').replace('Đ','D').lowercase(java.util.Locale.ROOT)
    val words = query.normalized().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return items.filter { e ->
        val text = "${e.title.vi} ${e.title.en} ${e.muscleGroup.vi} ${e.muscleGroup.en}".normalized()
        words.all { it in text } && (level.isEmpty() || e.level.name == level) &&
            (muscle.isEmpty() || e.muscleGroup.en == muscle) && (category.isEmpty() || e.category.name == category)
    }
}
