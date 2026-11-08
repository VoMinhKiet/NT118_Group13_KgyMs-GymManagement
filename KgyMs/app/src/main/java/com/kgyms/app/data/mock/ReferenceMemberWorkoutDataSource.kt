package com.kgyms.app.data.mock

import com.kgyms.app.data.model.MemberWorkoutDataSource

/** Reference exercise library only: demo personal history must never be attributed to a real UID. */
internal object ReferenceMemberWorkoutDataSource : MemberWorkoutDataSource {
    override fun snapshot(now: Long) = MockMemberWorkoutDataSource.snapshot(now).copy(
        sessions = emptyList(), trainer = null, trainerPlan = null, messages = emptyList(),
    )
}
