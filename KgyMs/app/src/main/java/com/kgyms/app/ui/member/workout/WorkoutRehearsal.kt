package com.kgyms.app.ui.member.workout

import com.kgyms.app.data.model.WorkoutCatalog
import com.kgyms.app.data.model.WorkoutSessionUiModel
import com.kgyms.app.data.model.WorkoutSummaryUiModel
import com.kgyms.app.utils.calculateWeeklyGoal
import com.kgyms.app.utils.toggleWorkoutMark
import java.util.TimeZone

/** Device saved-state only. Never converted to confirmed history or written to Firebase. */
internal data class WorkoutRehearsal(
    val active: List<String> = emptyList(),
    val finished: List<String> = emptyList(),
    val marks: List<String> = emptyList(),
) {
    private fun mark(key: String, exercise: String) = "$key|${exercise.length}:$exercise"
    fun isDone(key: String, exercise: String) = mark(key, exercise) in marks
    fun start(key: String) = if (key in active) this else copy(
        active = active + key, finished = finished - key,
        marks = marks.filterNot { it.startsWith("$key|") },
    )
    fun toggle(key: String, exercise: String) = if (key !in active) this else
        copy(marks = toggleWorkoutMark(marks, mark(key, exercise)))
    fun finish(key: String) = if (key !in active) this else
        copy(active = active - key, finished = (finished + key).distinct())
}

internal data class WorkoutDestination(
    val workout: WorkoutSummaryUiModel?, val session: WorkoutSessionUiModel?,
)

internal fun WorkoutCatalog.destination(route: String): WorkoutDestination {
    val session = if (route.startsWith("session:")) sessions.firstOrNull { it.id == route.removePrefix("session:") } else null
    val workoutId = when {
        route.startsWith("workout:") -> route.removePrefix("workout:")
        else -> session?.workoutId
    }
    return WorkoutDestination(workouts.firstOrNull { it.id == workoutId }, session)
}

internal fun workoutRunKey(route: String, week: Long): String =
    if (route.startsWith("session:")) "${route.length}:$route" else "$week:${route.length}:$route"

internal fun WorkoutCatalog.weekSessions(now: Long, zone: TimeZone = TimeZone.getDefault()): List<WorkoutSessionUiModel> {
    val week = calculateWeeklyGoal(1, emptyList(), now, zone)
    return sessions.filter { it.scheduledAt >= week.weekStartMillis && it.scheduledAt < week.weekEndExclusiveMillis }
        .sortedBy { it.scheduledAt }
}

/** Keep missing references visible, but remove duplicate keys before passing data to LazyColumn. */
internal fun WorkoutCatalog.safeCatalog() = copy(
    exercises = exercises.filter { it.id.isNotBlank() }.distinctBy { it.id },
    workouts = workouts.filter { it.id.isNotBlank() }.distinctBy { it.id }
        .map { it.copy(exerciseIds = it.exerciseIds.filter(String::isNotBlank).distinct()) },
    sessions = sessions.filter { it.id.isNotBlank() }.distinctBy { it.id },
    messages = messages.filter { it.id.isNotBlank() }.distinctBy { it.id },
)
