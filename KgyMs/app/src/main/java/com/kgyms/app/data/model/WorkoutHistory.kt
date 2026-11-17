package com.kgyms.app.data.model

import com.kgyms.app.utils.calculateWeeklyGoal
import java.util.TimeZone

internal enum class AttemptState { LocalPending, SelfReported, Confirmed }
internal enum class HistorySyncState { LocalOnly, Pending, Syncing, Synced, AccessDenied, Error, Conflict }
internal data class AttemptExercise(val id: String, val performed: Boolean)
internal data class WorkoutAttempt(
    val id: String, val uid: String, val logicalSessionId: String, val planVersionId: String,
    val workoutId: String, val titleVi: String, val titleEn: String,
    val startedAt: Long, val finishedAt: Long, val exercises: List<AttemptExercise>,
)
internal data class WorkoutConfirmation(val uid: String, val attemptId: String, val confirmedBy: String, val confirmedAt: Long)
internal data class WorkoutRunContext(val key: String, val logicalSessionId: String, val planVersionId: String,
    val workoutId: String, val titleVi: String, val titleEn: String, val exerciseIds: List<String>)
internal data class WorkoutDraft(val context: WorkoutRunContext, val attemptId: String, val startedAt: Long,
    val performed: Set<String> = emptySet())
internal data class WorkoutHistoryArchive(
    val attempts: List<WorkoutAttempt> = emptyList(), val acknowledged: Set<String> = emptySet(),
    val confirmations: List<WorkoutConfirmation> = emptyList(), val remoteKnown: Boolean = false,
    val drafts: Map<String, WorkoutDraft> = emptyMap(), val finishedRuns: Map<String, String> = emptyMap(),
) {
    fun state(attempt: WorkoutAttempt): AttemptState = when {
        attempt.id !in acknowledged -> AttemptState.LocalPending
        confirmations.any { it.attemptId == attempt.id && it.uid == attempt.uid } -> AttemptState.Confirmed
        else -> AttemptState.SelfReported
    }
    val pending get() = attempts.filter { it.id !in acknowledged }
    fun finished(key: String) = attempts.firstOrNull { it.id == finishedRuns[key] }

    /** Latest real attempt for display; weekly counting still uses the first completion separately. */
    fun completed(logicalSessionId: String, now: Long): WorkoutAttempt? = attempts
        .asSequence()
        .filter { it.logicalSessionId == logicalSessionId && it.finishedAt <= now }
        .maxWithOrNull(compareBy<WorkoutAttempt> { it.finishedAt }.thenBy { it.id })

    fun activeDraft(logicalSessionId: String): WorkoutDraft? = drafts.values
        .asSequence()
        .filter { it.context.logicalSessionId == logicalSessionId }
        .maxWithOrNull(compareBy<WorkoutDraft> { it.startedAt }.thenBy { it.attemptId })

    /**
     * Exact key wins. A plan revision may then resume by stable logical session ID. Reference
     * workouts use their workout ID because their legacy logical ID contains the start date.
     */
    fun activeDraft(proposed: WorkoutRunContext): WorkoutDraft? = drafts[proposed.key]
        ?: activeDraft(proposed.logicalSessionId)
        ?: if (proposed.planVersionId.isBlank()) drafts.values
            .asSequence()
            .filter { it.context.planVersionId.isBlank() && it.context.workoutId == proposed.workoutId }
            .maxWithOrNull(compareBy<WorkoutDraft> { it.startedAt }.thenBy { it.attemptId })
        else null

    /** Supports a reference workout that started before midnight and finished on the new day. */
    fun displayedCompletion(proposed: WorkoutRunContext, now: Long, zoneId: String): WorkoutAttempt? =
        completed(proposed.logicalSessionId, now) ?: if (proposed.planVersionId.isBlank()) {
            val today = PlanDates.date(now, zoneId)
            attempts.asSequence()
                .filter {
                    it.planVersionId.isBlank() && it.workoutId == proposed.workoutId &&
                        it.finishedAt <= now && PlanDates.date(it.finishedAt, zoneId) == today
                }
                .maxWithOrNull(compareBy<WorkoutAttempt> { it.finishedAt }.thenBy { it.id })
        } else null
}
internal data class RemoteWorkoutHistory(val attempts: List<WorkoutAttempt>, val confirmations: List<WorkoutConfirmation>)
internal class WorkoutHistoryDenied : IllegalStateException("Workout history permission denied")
internal class WorkoutHistoryOffline : IllegalStateException("Workout history offline")
internal class WorkoutHistoryConflict : IllegalStateException("Immutable workout history differs")
internal fun resolveWorkoutTarget(override: Int?, planTarget: Int?): Int = override?.coerceIn(1,7) ?: planTarget?.coerceIn(1,7) ?: 0

internal data class WorkoutHistorySummary(val attempts: Int, val unique: Int?, val confirmed: Int?, val pending: Int, val progress: Float)
/** Attempts are events; unique sessions use their first completion, so retakes never move a session to a new week. */
internal fun workoutHistorySummary(archive: WorkoutHistoryArchive, target: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): WorkoutHistorySummary {
    val week = calculateWeeklyGoal(target, emptyList(), now, zone)
    val eligible = archive.attempts.filter { it.finishedAt <= now }
    fun inWeek(time: Long) = time >= week.weekStartMillis && time < week.weekEndExclusiveMillis
    val unique = eligible.groupBy { it.logicalSessionId }.values.count { inWeek(it.minOf { a -> a.finishedAt }) }
    val confirmed = eligible.filter { archive.state(it) == AttemptState.Confirmed }.groupBy { it.logicalSessionId }
        .values.count { inWeek(it.minOf { a -> a.finishedAt }) }
    val known = archive.remoteKnown || eligible.isNotEmpty()
    return WorkoutHistorySummary(eligible.count { inWeek(it.finishedAt) }, if (known) unique else null,
        if (archive.remoteKnown || archive.confirmations.isNotEmpty()) confirmed else null, archive.pending.size,
        if (target > 0 && known) (unique.toFloat() / target).coerceIn(0f, 1f) else 0f)
}

internal fun confirmedHistoryRecords(archive: WorkoutHistoryArchive): List<WorkoutRecord>? {
    if (!archive.remoteKnown && archive.confirmations.isEmpty()) return null
    return archive.attempts.filter { archive.state(it) == AttemptState.Confirmed }.groupBy { it.logicalSessionId }.values.map { group ->
        val first = group.minBy { it.finishedAt }
        WorkoutRecord(first.logicalSessionId, first.finishedAt, WorkoutRecordStatus.Completed, true)
    }
}
