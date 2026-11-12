package com.kgyms.app.ui.member.home

sealed interface MemberHomeAction {
    data object Profile : MemberHomeAction
    data object Workouts : MemberHomeAction
    data object WorkoutLibrary : MemberHomeAction
    data class RecommendedWorkout(val workoutId: String) : MemberHomeAction
    data object AssignedWorkout : MemberHomeAction
    data object Nutrition : MemberHomeAction
    data object Progress : MemberHomeAction
}
