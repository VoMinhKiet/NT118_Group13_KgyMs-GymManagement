package com.kgyms.app.ui.member.progress

import com.kgyms.app.data.model.HomeWeeklyGoal
import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.model.WorkoutRecordStatus
import com.kgyms.app.utils.calculateWeeklyGoal
import java.util.TimeZone

internal data class MemberProgressData(
    val week: HomeWeeklyGoal,
    val history: List<WorkoutRecord>?,
) {
    val completed: Int? get() = history?.let { week.completed }
}

internal fun memberProgressData(
    target: Int,
    records: List<WorkoutRecord>?,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
): MemberProgressData {
    val history = records?.asSequence()
        ?.filter {
            it.sessionId.isNotBlank() && it.confirmed &&
                it.status == WorkoutRecordStatus.Completed && it.occurredAtMillis <= now
        }
        ?.sortedByDescending { it.occurredAtMillis }
        ?.distinctBy { it.sessionId }
        ?.toList()
    return MemberProgressData(calculateWeeklyGoal(target, history.orEmpty(), now, zone), history)
}
