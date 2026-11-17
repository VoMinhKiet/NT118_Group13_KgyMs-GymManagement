package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
import org.junit.Assert.*
import org.junit.Test

class PersonalPlanGeneratorTest {
    private val zone = "Asia/Ho_Chi_Minh"
    private val now = PlanDates.millis("2026-12-29", zone) + 12 * 3_600_000
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private fun config(level: WorkoutLevel = WorkoutLevel.Beginner, count: Int = 3, days: List<Int> = emptyList(), start: String = "2026-12-29") =
        PersonalPlanConfig(level, count, start, zone, days)
    private fun plan() = Generator.generate("A", config(), catalog, now)

    @Test fun allLevelsAndCountsGenerateExactlyFourWeeksWithRestDays() {
        WorkoutLevel.entries.forEach { level -> (1..7).forEach { count ->
            val plan = Generator.generate("A", config(level, count), catalog, now)
            assertEquals(count * 4, plan.sessions.size)
            assertEquals(plan.sessions.size, plan.sessions.map { it.id }.distinct().size)
            assertEquals(plan.sessions.size, plan.sessions.map { it.date }.distinct().size)
            assertTrue(plan.sessions.all { it.date >= "2026-12-29" && it.date < "2027-01-26" })
            val eligible = catalog.exercises.filter { it.level.ordinal <= level.ordinal }.map { it.id }
            assertTrue(plan.sessions.all { session -> session.exerciseIds.all { it in eligible } })
            Generator.validate(plan, catalog, now)
        } }
    }
    @Test fun sameInputAndReorderedWeekdaysAreDeterministic() {
        val a = Generator.generate("A", config(days = listOf(0, 2, 4)), catalog, now)
        val b = Generator.generate("A", config(days = listOf(4, 0, 2)), catalog, now)
        assertEquals(a, b)
        assertEquals(Generator.seal(a), Generator.seal(b))
        assertNotEquals(a.id, Generator.generate("B", config(days = listOf(0, 2, 4)), catalog, now).id)
    }
    @Test fun customDaysAreRespectedAcrossYearBoundary() {
        val plan = Generator.generate("A", config(count = 2, days = listOf(1, 6)), catalog, now)
        assertEquals("2026-12-29", plan.sessions.first().date)
        assertTrue(plan.sessions.any { it.date.startsWith("2027-") })
        assertTrue(plan.sessions.all { PlanDates.weekday(it.date, zone) in listOf(1, 6) })
    }
    @Test fun invalidCountsDaysDatesZonesAndPastStartsAreRejected() {
        val invalid = listOf(config(count = 0), config(count = 8), config(days = listOf(0, 0, 2)),
            config(days = listOf(0, 2)), config(days = listOf(0, 2, 7)), config(start = "2026-12-28"),
            config(start = "2027-02-30"), config(start = "2027-2-01"), config().copy(zoneId = "No/SuchZone"))
        invalid.forEach { assertTrue(it.toString(), runCatching { Generator.generate("A", it, catalog, now) }.isFailure) }
        assertTrue(runCatching { Generator.generate("", config(), catalog, now) }.isFailure)
    }
    @Test fun duplicatesAreSanitizedAndMissingReferencesNeverCrashGeneration() {
        val noisy = catalog.copy(workouts = catalog.workouts + catalog.workouts, exercises = catalog.exercises + catalog.exercises)
        assertEquals(plan(), Generator.generate("A", config(), noisy, now))
        val broken = catalog.copy(workouts = catalog.workouts.map { it.copy(exerciseIds = listOf("missing")) })
        assertTrue(runCatching { Generator.generate("A", config(), broken, now) }.isFailure)
        assertTrue(runCatching { Generator.generate("A", config(), catalog.copy(exercises = emptyList()), now) }.isFailure)
        assertTrue(runCatching { Generator.generate("A", config(), catalog.copy(workouts = emptyList()), now) }.isFailure)
    }
    @Test fun beginnerNeverReceivesPlankOrTempoSquatAndSmallCatalogWarns() {
        assertTrue(plan().sessions.all { "plank" !in it.exerciseIds && "tempo-squat" !in it.exerciseIds })
        assertTrue(plan().repeated)
        val one = catalog.copy(workouts = listOf(catalog.workouts.first()))
        val repeated = Generator.generate("A", config(), one, now)
        assertTrue(repeated.repeated)
        assertEquals(1, repeated.sessions.map { it.exerciseIds }.distinct().size)
    }
    @Test fun changingDateKeepsIdentityAndRejectsPastCollisionsAndOtherWeeks() {
        val plan = plan()
        val first = plan.sessions.first()
        val weekStart = plan.config.startDate
        val free = (0..6).map { PlanDates.add(weekStart, it, zone) }.first { date -> plan.sessions.none { it.date == date } }
        val moved = Generator.move(plan, first.id, free, catalog, now)
        assertEquals(first.id, moved.sessions.first().id)
        assertEquals(free, moved.sessions.first().date)
        assertTrue(runCatching { Generator.move(plan, first.id, plan.sessions[1].date, catalog, now) }.isFailure)
        assertTrue(runCatching { Generator.move(plan, first.id, "2026-12-28", catalog, now) }.isFailure)
        assertTrue(runCatching { Generator.move(plan, first.id, "2027-02-01", catalog, now) }.isFailure)
        val nextWeekFree = (7..13).map { PlanDates.add(weekStart, it, zone) }.first { date -> plan.sessions.none { it.date == date } }
        assertTrue(runCatching { Generator.move(plan, first.id, nextWeekFree, catalog, now) }.isFailure)
        assertNotEquals(Generator.seal(plan).id, Generator.seal(moved).id)
    }
    @Test fun replacementUsesEligibleExistingExercisesAndUnknownTemplateIsRejected() {
        val plan = plan()
        val choice = Generator.candidates(catalog, plan.config.level).last()
        val edited = Generator.replace(plan, plan.sessions.first().id, choice.id, catalog, now)
        assertEquals(choice.exerciseIds, edited.sessions.first().exerciseIds)
        assertTrue(runCatching { Generator.replace(plan, plan.sessions.first().id, "unknown", catalog, now) }.isFailure)
    }
    @Test fun validationRejectsDuplicateSessionIdsAndHigherLevelExercises() {
        val plan = plan()
        val duplicates = plan.copy(sessions = plan.sessions.map { it.copy(id = "same") })
        assertTrue(runCatching { Generator.validate(duplicates, catalog, now) }.isFailure)
        val advanced = plan.copy(sessions = plan.sessions.map { it.copy(exerciseIds = listOf("tempo-squat")) })
        assertTrue(runCatching { Generator.validate(advanced, catalog, now) }.isFailure)
    }
    @Test fun calendarDatesUseLocalZoneAndLeapDaysWithoutFixedDayDurations() {
        assertEquals("2028-02-29", PlanDates.add("2028-02-28", 1, zone))
        assertEquals("2028-03-01", PlanDates.add("2028-02-28", 2, zone))
        val ny = "America/New_York"
        assertEquals(23L * 3_600_000, PlanDates.millis("2027-03-15", ny) - PlanDates.millis("2027-03-14", ny))
    }

    @Test fun oneAndSevenDaysKeepChosenDaysAndNeverIncreasePrescription() {
        listOf(listOf(6), (0..6).toList()).forEach { days ->
            val plan = Generator.generate("A", config(count = days.size, days = days), catalog, now)
            assertEquals(days, plan.config.weekdays)
            assertEquals(days.size * 4, plan.sessions.size)
            assertTrue(plan.sessions.all { PlanDates.weekday(it.date, zone) in days })
            assertTrue(plan.sessions.all { session -> session.exerciseIds.all { id -> catalog.exercises.any { it.id == id && it.level == WorkoutLevel.Beginner } } })
            Generator.validate(plan, catalog, now)
            if (days.size == 7) assertTrue(PlanDates.freeDates(plan, plan.sessions.first(), "2026-12-29").isEmpty())
        }
    }

    @Test fun datePickerRoundTripDoesNotShiftLocalDatesAcrossUtcOffsets() {
        listOf("Asia/Ho_Chi_Minh", "Pacific/Kiritimati", "America/Los_Angeles", "Pacific/Pago_Pago").forEach { zoneId ->
            val localDate = PlanDates.date(PlanDates.millis("2027-01-01", zoneId) + 1, zoneId)
            assertEquals("2027-01-01", PlanDates.pickerDate(PlanDates.pickerMillis(localDate)))
            assertEquals("01/01/2027", PlanDates.display(localDate))
        }
    }

    @Test fun weeklyViewAndFreeDatesPreserveExistingRollingWeekBoundaries() {
        val plan = plan()
        val week = PlanDates.weekDates(plan, 0)
        assertEquals("2026-12-29", week.first())
        assertEquals("2027-01-04", week.last())
        assertEquals(28, (0..3).flatMap { PlanDates.weekDates(plan, it) }.distinct().size)
        val available = PlanDates.freeDates(plan, plan.sessions.first(), "2026-12-31")
        assertTrue(available.all { it in week && it >= "2026-12-31" && plan.sessions.none { s -> s.date == it } })
    }
}
