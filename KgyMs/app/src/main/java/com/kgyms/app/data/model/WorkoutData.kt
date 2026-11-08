package com.kgyms.app.data.model

internal data class WorkoutText(val vi: String, val en: String) {
    fun inLanguage(language: String) = if (language == "vi") vi else en
}

internal enum class PlanSource { Self, Trainer }

internal enum class SessionStatus { Planned, Booked, Confirmed }

internal data class Exercise(
    val id: String, val name: WorkoutText, val muscle: WorkoutText,
    val level: WorkoutText, val equipment: WorkoutText, val sets: Int,
    val reps: String, val restSeconds: Int, val instructions: List<WorkoutText>, val safety: WorkoutText,
)
