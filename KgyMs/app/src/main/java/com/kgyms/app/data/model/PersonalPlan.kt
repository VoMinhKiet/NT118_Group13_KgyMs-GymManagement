package com.kgyms.app.data.model

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dates are local calendar dates in zoneId, not appointment timestamps. */
internal data class PersonalPlanConfig(
    val level: WorkoutLevel, val sessionsPerWeek: Int, val startDate: String,
    val zoneId: String, val weekdays: List<Int> = emptyList(), // Monday = 0
)
internal data class PersonalPlanSession(
    val id: String, val date: String, val templateId: String, val exerciseIds: List<String>,
)
internal data class PersonalPlan(
    val id: String, val uid: String, val config: PersonalPlanConfig,
    val sessions: List<PersonalPlanSession>, val repeated: Boolean,
    val schemaVersion: Int = 1, val generatorVersion: Int = 1,
)
internal data class PersonalPlanArchive(val activeId: String? = null, val plans: List<PersonalPlan> = emptyList()) {
    val active get() = plans.firstOrNull { it.id == activeId }
}

internal object PlanDates {
    // Material DatePicker uses UTC midnight to represent a calendar date, not a local instant.
    fun pickerMillis(date: String): Long = millis(date, "UTC")
    fun pickerDate(utcMillis: Long): String = date(utcMillis, "UTC")
    fun display(date: String): String = date.split('-').let { "${it[2]}/${it[1]}/${it[0]}" }
    fun weekDates(plan: PersonalPlan, week: Int): List<String> {
        require(week in 0..3)
        return (0..6).map { add(plan.config.startDate, week * 7 + it, plan.config.zoneId) }
    }
    fun freeDates(plan: PersonalPlan, session: PersonalPlanSession, today: String): List<String> {
        val week = (0..3).first { session.date in weekDates(plan, it) }
        val occupied = plan.sessions.map { it.date }.toSet()
        return weekDates(plan, week).filter { it >= today && it !in occupied }
    }
    fun zone(id: String): TimeZone {
        require(id in TimeZone.getAvailableIDs()) { "Invalid time zone" }
        return TimeZone.getTimeZone(id)
    }
    private fun format(zone: TimeZone) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone; isLenient = false }
    fun date(now: Long, zoneId: String): String = format(zone(zoneId)).format(Date(now))
    fun millis(date: String, zoneId: String): Long {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid date" }
        val position = ParsePosition(0)
        return requireNotNull(format(zone(zoneId)).parse(date, position)) { "Invalid date" }
            .also { require(position.index == date.length) }.time
    }
    fun add(date: String, days: Int, zoneId: String): String = Calendar.getInstance(zone(zoneId)).run {
        timeInMillis = millis(date, zoneId); add(Calendar.DAY_OF_YEAR, days)
        date(timeInMillis, zoneId)
    }
    fun weekday(date: String, zoneId: String): Int = Calendar.getInstance(zone(zoneId)).run {
        timeInMillis = millis(date, zoneId); (get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
