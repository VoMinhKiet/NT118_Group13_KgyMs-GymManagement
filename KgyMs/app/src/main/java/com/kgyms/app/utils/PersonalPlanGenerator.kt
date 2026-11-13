package com.kgyms.app.utils

import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.workout.safeCatalog
import java.security.MessageDigest

internal object PersonalPlanGenerator {
    fun seal(plan: PersonalPlan): PersonalPlan {
        val signature = "${plan.uid}|${plan.config}|" + plan.sessions.joinToString(";") { "${it.date}:${it.templateId}:${it.exerciseIds}" }
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
        val signature = listOf(uid, normalized.toString(), options.map { it.id to it.exerciseIds }.toString(), "v1").joinToString("\n")
        val id = "auto-" + MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(24)
        val dates = (0..27).map { PlanDates.add(config.startDate, it, config.zoneId) }
            .filter { PlanDates.weekday(it, config.zoneId) in days }
        val sessions = dates.mapIndexed { index, date ->
            val selected = options[index % options.size]
            PersonalPlanSession("$id-$date", date, selected.id, selected.exerciseIds)
        }
        return PersonalPlan(id, uid, normalized, sessions, sessions.size > options.size)
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
        return plan.copy(sessions = plan.sessions.map { if (it.id == sessionId) it.copy(templateId = workout.id, exerciseIds = workout.exerciseIds) else it })
            .also { validate(it, catalog, now) }
    }
}
