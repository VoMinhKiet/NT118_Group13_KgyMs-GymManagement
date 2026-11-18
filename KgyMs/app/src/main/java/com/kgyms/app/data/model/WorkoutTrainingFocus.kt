package com.kgyms.app.data.model

internal enum class WorkoutTrainingFocus { Push, Pull, Legs, Core }

internal fun ExerciseUiModel.trainingFocus(): Set<WorkoutTrainingFocus> = when (muscleGroup.en) {
    "Chest", "Shoulders", "Triceps" -> setOf(WorkoutTrainingFocus.Push)
    "Back", "Arms", "Biceps" -> setOf(WorkoutTrainingFocus.Pull)
    "Legs", "Quadriceps", "Hamstrings", "Glutes", "Calves" -> setOf(WorkoutTrainingFocus.Legs)
    "Core" -> setOf(WorkoutTrainingFocus.Core)
    "Full body" -> setOf(WorkoutTrainingFocus.Push, WorkoutTrainingFocus.Pull, WorkoutTrainingFocus.Legs, WorkoutTrainingFocus.Core)
    else -> emptySet()
}
