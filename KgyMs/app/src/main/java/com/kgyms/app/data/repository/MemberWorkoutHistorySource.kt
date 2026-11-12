package com.kgyms.app.data.repository

import com.kgyms.app.data.model.WorkoutRecord

/** Returns null when history is unavailable, and an empty list for a successful empty read. */
internal fun interface MemberWorkoutHistorySource {
    suspend fun load(uid: String): List<WorkoutRecord>?
}

internal val UnconnectedWorkoutHistory = MemberWorkoutHistorySource { null }
