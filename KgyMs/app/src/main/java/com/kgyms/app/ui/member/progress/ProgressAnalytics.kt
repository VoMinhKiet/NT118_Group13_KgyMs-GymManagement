package com.kgyms.app.ui.member.progress

import com.kgyms.app.data.model.*
import com.kgyms.app.utils.calculateWeeklyGoal
import java.util.Calendar
import java.util.TimeZone

internal enum class ProgressLoad { Loading, Unavailable, Failed, Ready }

internal data class ProgressHistory(val load: ProgressLoad, val archive: WorkoutHistoryArchive? = null)

/** An empty local cache alone does not establish that the account has no workout history. */
internal fun progressHistory(
    uid: String?,
    owner: String?,
    archive: WorkoutHistoryArchive?,
    now: Long,
    loading: Boolean = false,
    failed: Boolean = false,
): ProgressHistory {
    if (uid.isNullOrBlank() || owner != uid || archive == null) return ProgressHistory(ProgressLoad.Unavailable)
    if (loading) return ProgressHistory(ProgressLoad.Loading)
    if (failed) return ProgressHistory(ProgressLoad.Failed)

    val attempts = archive.attempts.filter { it.uid == uid && it.finishedAt <= now }
        .sortedWith(compareByDescending<WorkoutAttempt> { it.finishedAt }.thenBy { it.id })
        .distinctBy { it.id }
    val verified = archive.remoteKnown && archive.attempts.all { it.uid == uid }
    if (!verified && attempts.isEmpty()) return ProgressHistory(ProgressLoad.Unavailable)

    val foreignIds = archive.attempts.filter { it.uid != uid }.map { it.id }.toSet()
    val ownedIds = attempts.map { it.id }.toSet()
    val ownedArchive = WorkoutHistoryArchive(
        attempts = attempts,
        acknowledged = archive.acknowledged.intersect(ownedIds) - foreignIds,
        confirmations = archive.confirmations.filter { it.uid == uid && it.attemptId in ownedIds },
        remoteKnown = verified,
    )
    return ProgressHistory(ProgressLoad.Ready, ownedArchive)
}

internal data class ProgressWeek(val start: Long, val endExclusive: Long, val completed: Int?)

internal data class ProgressAnalytics(
    val weeks: List<ProgressWeek>,
    val target: Int,
    val totalSessions: Int?,
    val performedExercises: Int?,
    val weeksAtCurrentTarget: Int?,
) {
    val currentWeek get() = weeks.last()
    val remaining get() = currentWeek.completed?.let { (target - it).coerceAtLeast(0) }.takeIf { target > 0 }
}

internal fun progressAnalytics(
    history: ProgressHistory,
    target: Int,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
    weekCount: Int = 4,
    legacyRecords: List<WorkoutRecord>? = null,
): ProgressAnalytics {
    require(weekCount > 0)
    val archive = history.archive
    val known = archive?.remoteKnown == true || legacyRecords != null
    val completions = archive?.attempts?.firstCompletionTimes()
        ?: legacyRecords.orEmpty().groupBy { it.sessionId }.values.map { records -> records.minOf { it.occurredAtMillis } }
    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = calculateWeeklyGoal(target, emptyList(), now, zone).weekStartMillis
        add(Calendar.DAY_OF_YEAR, -7 * (weekCount - 1))
    }
    val weeks = List(weekCount) {
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val end = calendar.timeInMillis
        val count = completions.count { it >= start && it < end }
        ProgressWeek(start, end, count.takeIf { known || it > 0 })
    }
    return ProgressAnalytics(
        weeks = weeks,
        target = target,
        totalSessions = completions.size.takeIf { known || it > 0 },
        performedExercises = archive?.attempts?.let { attempts ->
            if (known || attempts.any { it.exercises.isNotEmpty() }) attempts.sumOf { attempt -> attempt.exercises.count { it.performed } }
            else null
        },
        weeksAtCurrentTarget = if (known && target > 0) weeks.count { (it.completed ?: 0) >= target } else null,
    )
}

internal data class PlanProgress(
    val plan: PersonalPlan,
    val scheduled: Int,
    val completed: Int?,
    val upcoming: Int,
    val pastIncomplete: Int?,
)

internal fun activePlanProgress(
    uid: String?,
    plans: PersonalPlanArchive,
    history: ProgressHistory,
    now: Long,
): PlanProgress? {
    val active = plans.active?.takeIf { uid != null && it.uid == uid } ?: return null
    val versions = plans.plans.filter { it.uid == uid }.associateBy { it.id }
    val children = versions.values.groupBy { it.revisionOf }
    val family = mutableSetOf<String>()
    val pendingVersions = ArrayDeque(listOf(active.id))
    val logicalIds = active.sessions.map { it.id }.toSet()
    val runtimeIds = mutableMapOf<String, String>()
    while (pendingVersions.isNotEmpty()) {
        val versionId = pendingVersions.removeFirst()
        if (!family.add(versionId)) continue
        pendingVersions.addAll(children[versionId].orEmpty().map { it.id })
        val version = versions[versionId] ?: continue
        version.revisionOf?.let(pendingVersions::addLast)
        for (session in version.sessions) {
            if (session.id in logicalIds) runtimeIds[version.runtimeSessionId(session)] = session.id
        }
    }

    val archive = history.archive
    val completedIds = archive?.attempts.orEmpty().asSequence()
        .filter { it.uid == uid && it.finishedAt <= now && (it.planVersionId.isBlank() || it.planVersionId in family) }
        .mapNotNull { attempt ->
            attempt.logicalSessionId.takeIf { it in logicalIds } ?: runtimeIds[attempt.logicalSessionId]
        }.toSet()
    val today = PlanDates.date(now, active.config.zoneId)
    val distinctSessions = active.sessions.distinctBy { it.id }
    val historyKnown = archive?.remoteKnown == true
    return PlanProgress(
        plan = active,
        scheduled = distinctSessions.size,
        completed = completedIds.size.takeIf { historyKnown || it > 0 },
        upcoming = distinctSessions.count { it.date >= today && it.id !in completedIds },
        pastIncomplete = if (historyKnown) distinctSessions.count { it.date < today && it.id !in completedIds } else null,
    )
}
