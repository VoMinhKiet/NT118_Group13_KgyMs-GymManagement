package com.kgyms.app.data.model

internal enum class WorkoutLevel(val label: WorkoutText) {
    Beginner(WorkoutText("Cơ bản", "Beginner")), Intermediate(WorkoutText("Trung cấp", "Intermediate")), Advanced(WorkoutText("Nâng cao", "Advanced"))
}
internal enum class WorkoutCategory(val label: WorkoutText) {
    Strength(WorkoutText("Sức mạnh", "Strength")), Mobility(WorkoutText("Linh hoạt", "Mobility")), Core(WorkoutText("Cơ trung tâm", "Core"))
}
internal enum class VideoProvider { LocalPreview, YouTube, External }
internal data class VideoSourceUiModel(val id: String, val title: WorkoutText, val provider: VideoProvider, val sourceUrl: String? = null)
internal data class ExerciseUiModel(val id: String, val title: WorkoutText, val muscleGroup: WorkoutText,
    val level: WorkoutLevel, val category: WorkoutCategory, val thumbnailRes: Int, val equipment: WorkoutText,
    val sets: Int, val reps: String, val restSeconds: Int, val instructions: List<WorkoutText>, val safety: WorkoutText,
    val video: VideoSourceUiModel, val thumbnailUrl: String? = null)
internal data class WorkoutSummaryUiModel(val id: String, val title: WorkoutText, val durationMinutes: Int,
    val calories: Int, val level: WorkoutLevel, val category: WorkoutCategory, val thumbnailRes: Int,
    val exerciseIds: List<String>, val tags: List<WorkoutText>, val thumbnailUrl: String? = null) {
    val exerciseCount get() = exerciseIds.size
}
internal data class WorkoutSessionUiModel(val id: String, val workoutId: String, val scheduledAt: Long,
    val status: SessionStatus, val source: PlanSource, val trainerId: String? = null,
    val completedAt: Long? = null, val confirmedAt: Long? = null)
internal data class TrainerUiModel(val id: String, val name: String, val initials: String, val specialty: WorkoutText)
internal data class TrainerPlanUiModel(val id: String, val trainerId: String, val title: WorkoutText,
    val assignedAt: Long, val sessionIds: List<String>)
internal data class TrainerMessageUiModel(val id: String, val fromTrainer: Boolean, val text: WorkoutText)
internal data class WorkoutCatalog(val exercises: List<ExerciseUiModel>, val workouts: List<WorkoutSummaryUiModel>,
    val sessions: List<WorkoutSessionUiModel>, val trainer: TrainerUiModel?, val trainerPlan: TrainerPlanUiModel?,
    val messages: List<TrainerMessageUiModel>, val historyAvailable: Boolean = false)

/** UI reads an immutable snapshot. A future host can map a repository flow to this model. */
internal fun interface MemberWorkoutDataSource {
    fun snapshot(now: Long): WorkoutCatalog
}
