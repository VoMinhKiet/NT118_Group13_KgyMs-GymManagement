package com.kgyms.app.utils

import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.workout.safeCatalog
import java.security.MessageDigest

internal object PersonalPlanGenerator {
    fun seal(plan: PersonalPlan): PersonalPlan {
        val signature = "${plan.uid}|${plan.config}|" + plan.sessions.joinToString(";") {
            "${it.date}:${it.templateId}:${it.exerciseIds}" + (it.suggestedExerciseIds?.let { ids -> ":suggested=$ids" } ?: "")
        }
        val id = "plan-" + MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(24)
        return plan.copy(id = id, sessions = plan.sessions.mapIndexed { index, s -> s.copy(id = "$id-$index") })
    }
    fun weekdays(count: Int): List<Int> = when (count) {
        1 -> listOf(0)
        2 -> listOf(0, 3)
        3 -> listOf(0, 2, 4)
        4 -> listOf(0, 1, 3, 5)
        5 -> listOf(0, 1, 3, 4, 6)
        6 -> listOf(0, 1, 2, 3, 4, 5)
        7 -> (0..6).toList()
        else -> error("Choose 1–7 sessions")
    }

    /** Keep the original default first, then offer similarly spaced choices across the full week. */
    fun weekdayOptions(count: Int): List<List<Int>> {
        val preferred = weekdays(count)
        if (count == 7) return listOf(preferred)
        if (count == 3) return listOf(preferred, listOf(1, 3, 5), listOf(0, 3, 6))
        val combinations = (1 until (1 shl 7)).map { mask -> (0..6).filter { mask and (1 shl it) != 0 } }
            .filter { it.size == count && it != preferred }
        fun spacingPenalty(days: List<Int>): Int = days.indices.sumOf { index ->
            val next = if (index == days.lastIndex) days.first() + 7 else days[index + 1]
            val deviation = (next - days[index]) * count - 7
            deviation * deviation
        }
        val alternatives = combinations.sortedWith(compareBy<List<Int>> { spacingPenalty(it) }
            .thenBy { (it intersect preferred.toSet()).size }.thenBy { it.first() })
        return listOf(preferred) + alternatives.take(if (count == 1) 6 else 3)
    }

    /** Reuse existing exercise definitions; strip exercises above the chosen level. */
    fun candidates(catalog: WorkoutCatalog, level: WorkoutLevel): List<WorkoutSummaryUiModel> {
        val data = catalog.safeCatalog()
        val byId = data.exercises.associateBy { it.id }
        return data.workouts.sortedBy { it.id }.mapNotNull { workout ->
            if (workout.exerciseIds.isEmpty() || workout.exerciseIds.any { it !in byId }) return@mapNotNull null
            val ids = workout.exerciseIds.filter { byId.getValue(it).level.ordinal <= level.ordinal }
            if (ids.isEmpty()) null else workout.copy(exerciseIds = ids, level = level)
        }.distinctBy { it.exerciseIds.sorted() }
            .sortedWith(compareByDescending<WorkoutSummaryUiModel> { it.exerciseIds.size }.thenBy { it.id })
    }

    fun generate(uid: String, config: PersonalPlanConfig, catalog: WorkoutCatalog, now: Long): PersonalPlan {
        require(uid.isNotBlank()) { "Missing owner" }
        require(config.sessionsPerWeek in 1..7) { "Choose 1–7 sessions" }
        val start = PlanDates.millis(config.startDate, config.zoneId)
        require(start >= PlanDates.millis(PlanDates.date(now, config.zoneId), config.zoneId)) { "Past start date" }
        val days = config.weekdays.ifEmpty { weekdays(config.sessionsPerWeek) }
        require(days.size == config.sessionsPerWeek && days.distinct().size == days.size && days.all { it in 0..6 }) { "Choose exactly the requested days" }
        val normalized = config.copy(weekdays = days.sorted())
        val options = candidates(catalog, config.level)
        require(options.isNotEmpty()) { "No eligible workouts" }
        val scheduledDays = (0..27).filter { offset ->
            PlanDates.weekday(PlanDates.add(config.startDate, offset, config.zoneId), config.zoneId) in days
        }
        val selected = balanceSessions(options, catalog, normalized, scheduledDays)
        val signature = listOf(uid, normalized, selected.map { it.id to it.exerciseIds }, "balanced-v2").joinToString("\n")
        val id = "auto-" + MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(24)
        val sessions = scheduledDays.zip(selected).map { (offset, workout) ->
            val date = PlanDates.add(config.startDate, offset, config.zoneId)
            PersonalPlanSession("$id-$date", date, workout.id, workout.exerciseIds)
        }
        return PersonalPlan(id, uid, normalized, sessions, selected.distinctBy { it.exerciseIds.sorted() }.size < sessions.size)
    }

    private data class Candidate(
        val workout: WorkoutSummaryUiModel,
        val focus: Set<WorkoutTrainingFocus>,
        val atChosenLevel: Int,
    )

    private fun balanceSessions(
        workouts: List<WorkoutSummaryUiModel>,
        catalog: WorkoutCatalog,
        config: PersonalPlanConfig,
        scheduledDays: List<Int>,
    ): List<WorkoutSummaryUiModel> {
        val exercises = catalog.exercises.associateBy { it.id }
        val candidates = workouts.map { workout ->
            val items = workout.exerciseIds.mapNotNull(exercises::get)
            Candidate(workout, items.flatMap { it.trainingFocus() }.toSet(), items.count { it.level == config.level })
        }
        val selected = mutableListOf<Pair<Int, Candidate>>()
        val uses = mutableMapOf<String, Int>()
        for (offset in scheduledDays) {
            val weekFocus = selected.filter { (day, _) -> day / 7 == offset / 7 }.flatMap { it.second.focus }.toSet()
            val recent = selected.takeLast(2)
            fun score(candidate: Candidate): Int {
                val coverage = (candidate.focus - weekFocus).size
                val fatigue = recent.sumOf { (day, previous) ->
                    val weight = when (offset - day) { 1 -> 12; 2 -> 3; else -> 0 }
                    (candidate.focus intersect previous.focus).count { it != WorkoutTrainingFocus.Core } * weight
                }
                val prior = selected.lastOrNull()?.second
                return coverage * (if (config.sessionsPerWeek <= 2) 10 else 5) -
                    fatigue - (uses[candidate.workout.id] ?: 0) * 4 -
                    (if (prior == candidate) 8 else 0) + candidate.atChosenLevel.coerceAtMost(3)
            }
            val best = candidates.maxWith(compareBy<Candidate> { score(it) }.thenByDescending { it.workout.id })
            selected.add(offset to best)
            uses[best.workout.id] = (uses[best.workout.id] ?: 0) + 1
        }
        return selected.map { it.second.workout }
    }

    fun validate(plan: PersonalPlan, catalog: WorkoutCatalog, now: Long) {
        require(plan.schemaVersion == 1 && plan.generatorVersion == 1 && plan.uid.isNotBlank())
        require(plan.config.sessionsPerWeek in 1..7)
        require(plan.config.weekdays.distinct().size == plan.config.sessionsPerWeek && plan.config.weekdays.all { it in 0..6 })
        PlanDates.millis(plan.config.startDate, plan.config.zoneId)
        require(plan.sessions.size == plan.config.sessionsPerWeek * 4)
        require(plan.sessions.map { it.id }.distinct().size == plan.sessions.size)
        require(plan.sessions.map { it.date }.distinct().size == plan.sessions.size) { "One session per day" }
        val exercises = catalog.safeCatalog().exercises.associateBy { it.id }
        val templates = catalog.workouts.map { it.id }.toSet()
        val today = PlanDates.date(now, plan.config.zoneId)
        val end = PlanDates.add(plan.config.startDate, 28, plan.config.zoneId)
        (0..3).forEach { week ->
            val from = PlanDates.add(plan.config.startDate, week * 7, plan.config.zoneId)
            val until = PlanDates.add(from, 7, plan.config.zoneId)
            require(plan.sessions.count { it.date >= from && it.date < until } == plan.config.sessionsPerWeek) { "Keep the same number of sessions in each week" }
        }
        plan.sessions.forEach { session ->
            PlanDates.millis(session.date, plan.config.zoneId)
            require(session.id.isNotBlank() && session.templateId in templates)
            require(session.date >= today && session.date >= plan.config.startDate && session.date < end) { "Invalid session date" }
            require(session.exerciseIds.isNotEmpty() && session.exerciseIds.distinct().size == session.exerciseIds.size)
            require(session.exerciseIds.all { id -> exercises[id]?.let { it.level.ordinal <= plan.config.level.ordinal } == true }) { "Exercise exceeds level or is missing" }
        }
    }

    fun move(plan: PersonalPlan, sessionId: String, date: String, catalog: WorkoutCatalog, now: Long): PersonalPlan =
        plan.copy(sessions = plan.sessions.map { if (it.id == sessionId) it.copy(date = date) else it })
            .also { require(plan.sessions.any { it.id == sessionId }); validate(it, catalog, now) }

    fun replace(plan: PersonalPlan, sessionId: String, templateId: String, catalog: WorkoutCatalog, now: Long): PersonalPlan {
        val workout = requireNotNull(candidates(catalog, plan.config.level).firstOrNull { it.id == templateId })
        require(plan.sessions.any { it.id == sessionId })
        return plan.copy(sessions = plan.sessions.map { if (it.id == sessionId) it.copy(templateId = workout.id, exerciseIds = workout.exerciseIds, suggestedExerciseIds = null) else it })
            .also { validate(it, catalog, now) }
    }
}
