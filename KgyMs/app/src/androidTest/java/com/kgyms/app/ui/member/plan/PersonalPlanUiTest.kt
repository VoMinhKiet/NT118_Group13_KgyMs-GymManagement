package com.kgyms.app.ui.member.plan

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.local.PersonalPlanCodec
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.TimeZone
import java.util.UUID
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Local or in-memory sources only. Never initializes Firebase or changes real account data. */
class PersonalPlanUiTest {
    @get:Rule val compose = createComposeRule()
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private fun setupItem(tag: String) {
        if (tag in listOf("personal_preview", "personal_save")) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
            return
        }
        compose.onNodeWithTag("personal_setup").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }
    private fun selectDate(date: String) {
        compose.onNodeWithTag("personal_setup").performScrollToNode(hasTestTag("personal_calendar_$date"))
        compose.onNodeWithTag("personal_calendar_$date").performScrollTo().performClick()
    }
    private fun workoutItem(tag: String) = compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag(tag))
    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun createPreviewEditRestoreSaveAndOpenTodaysExactSession() {
        val repo = MemoryPersonalPlanRepository("ui-personal-A")
        val state = PersonalPlanState(repo)
        val restore = StateRestorationTester(compose)
        restore.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { MemberApp() }
        }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_create"))
        compose.onNodeWithTag("personal_today_create").performClick()
        setupItem("personal_days_count")
        val zone = TimeZone.getDefault().id
        val today = PlanDates.date(System.currentTimeMillis(), zone)
        val day = PlanDates.weekday(today, zone)
        val wanted = setOf(day, (day + 3) % 7)
        (0..6).filter { (it in wanted) != (it in setOf(0, 2, 4)) }.forEach {
            compose.onNodeWithTag("personal_day_$it").performScrollTo().performClick()
        }
        setupItem("personal_preview"); compose.onNodeWithTag("personal_preview").performClick()
        setupItem("personal_preview_count"); compose.onNodeWithTag("personal_preview_count")
            .assert(hasText("8 buổi • 4 tuần") or hasText("8 sessions • 4 weeks"))
        capture("plan-preview")
        selectDate(PlanDates.add(today, 3, zone))
        setupItem("personal_change_day"); compose.onNodeWithTag("personal_change_day").performClick()
        val moved = PlanDates.add(today, 4, zone)
        compose.onNodeWithTag("personal_move_$moved").performScrollTo().performClick()
        setupItem("personal_selected_date"); compose.onNodeWithTag("personal_selected_date").assertTextEquals(PlanDates.display(moved))
        selectDate(today)
        setupItem("personal_replace_workout"); compose.onNodeWithTag("personal_replace_workout").performClick()
        val replacement = Generator.candidates(catalog, WorkoutLevel.Beginner).last()
        compose.onNodeWithTag("personal_replace_${replacement.id}").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        selectDate(moved)
        setupItem("personal_selected_date"); compose.onNodeWithTag("personal_selected_date").assertTextEquals(PlanDates.display(moved))
        setupItem("personal_save"); compose.onNodeWithTag("personal_save").performClick()
        workoutItem("personal_local_status"); compose.onNodeWithTag("personal_local_status").assertExists()
        compose.runOnIdle {
            assertEquals(8, state.active!!.sessions.size)
            assertEquals(moved, state.active!!.sessions[1].date)
            assertEquals(replacement.exerciseIds, state.active!!.sessions[0].exerciseIds)
        }
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"))
        capture("plan-home")
        compose.onNodeWithTag("personal_today_start").performClick()
        val session = state.active!!.sessions.first { it.date == today }
        workoutItem("hero_personal-workout-${session.id}")
        compose.onNodeWithTag("hero_personal-workout-${session.id}").assertExists()
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        val exercise = session.exerciseIds.first()
        workoutItem("mark_$exercise"); compose.onNodeWithTag("mark_$exercise").performClick().assertIsOn()
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        androidx.test.espresso.Espresso.pressBack()
        workoutItem("workout_finished"); compose.onNodeWithTag("workout_finished").assertExists()
        compose.onNodeWithTag("workout_back").performClick()
        workoutItem("workout_plan")
        compose.onNodeWithTag("workout_plan").assertIsSelected()
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
    }

    @Test fun invalidDaysAndPastDateStayInSetupWithoutSaving() {
        val state = PersonalPlanState(MemoryPersonalPlanRepository("ui-invalid"))
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") { PersonalPlanSetupScreen({}, {}) } }
        }
        setupItem("personal_days_count")
        listOf(0, 2, 4).forEach { compose.onNodeWithTag("personal_day_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("personal_preview").assertIsNotEnabled()
        setupItem("personal_start_date"); compose.onNodeWithTag("personal_start_date").performClick()
        compose.onNodeWithTag("personal_date_picker").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val today = PlanDates.date(System.currentTimeMillis(), TimeZone.getDefault().id)
        val past = if (today.takeLast(2).toInt() == 1) {
            compose.onNodeWithContentDescription("Change to previous month").performClick()
            PlanDates.add(today, -1, TimeZone.getDefault().id).take(8) + "01"
        } else today.take(8) + "01"
        val pastDescription = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(PlanDates.pickerMillis(past)))
        compose.onNode(hasText(pastDescription, substring = true) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithTag("personal_date_confirm").performClick()
        setupItem("personal_start_date"); compose.onNodeWithTag("personal_start_date").assert(hasText(PlanDates.display(today), substring = true))
        compose.runOnIdle { assertNull(state.active) }
    }

    @Test fun homeDistinguishesTodayRestNotStartedAndExpired() {
        val zone = "Asia/Ho_Chi_Minh"
        val monday = PlanDates.millis("2027-01-04", zone)
        val plan = Generator.seal(Generator.generate("ui-today", PersonalPlanConfig(WorkoutLevel.Beginner, 2, "2027-01-04", zone, listOf(0, 3)), catalog, monday))
        val repo = MemoryPersonalPlanRepository("ui-today")
        runBlocking { repo.save(plan, null) }
        val state = PersonalPlanState(repo)
        var now by mutableLongStateOf(monday)
        var opened: String? = null
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") { PersonalPlanToday(now, {}, { opened = it }) } }
        }
        compose.onNodeWithTag("personal_today_start").performClick()
        compose.onNodeWithText("Session only • Not synced to Firebase").assertExists()
        compose.runOnIdle { assertEquals(plan.sessions.first().id, opened); now = PlanDates.millis("2027-01-05", zone) }
        compose.onNodeWithTag("personal_rest").assertExists()
        compose.onNodeWithText("Next session: 07/01/2027").assertExists()
        compose.runOnIdle { now = PlanDates.millis("2027-01-03", zone) }
        compose.onNodeWithTag("personal_not_started").assertExists()
        compose.runOnIdle { now = PlanDates.millis("2027-02-01", zone) }
        compose.onNodeWithTag("personal_expired").assertExists()
    }

    @Test fun failedLoadIsNeverShownAsNoPlan() {
        val repo = object : PersonalPlanRepository {
            override val uid = "ui-error"
            override suspend fun load(): PersonalPlanArchive = error("Denied test read")
            override suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive = error("Unexpected save")
        }
        val state = PersonalPlanState(repo)
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") { PersonalPlanToday(System.currentTimeMillis(), {}, {}) } }
        }
        compose.onNodeWithTag("personal_error").assertExists()
        compose.onNodeWithTag("personal_today_create").assertDoesNotExist()
    }

    @Test fun switchingPlanOwnersClearsTodaysCardAndRestoresOnlyTheirOwnPlan() {
        val zone = TimeZone.getDefault().id
        val now = System.currentTimeMillis()
        val today = PlanDates.date(now, zone)
        val weekday = PlanDates.weekday(today, zone)
        val repoA = MemoryPersonalPlanRepository("ui-switch-A")
        val plan = Generator.seal(Generator.generate(repoA.uid, PersonalPlanConfig(WorkoutLevel.Beginner, 2, today, zone, listOf(weekday, (weekday + 3) % 7)), catalog, now))
        runBlocking { repoA.save(plan, null) }
        var state by mutableStateOf(PersonalPlanState(repoA))
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") { PersonalPlanToday(now, {}, {}) } }
        }
        compose.onNodeWithTag("personal_today_start").assertExists()
        compose.runOnIdle { state = PersonalPlanState(MemoryPersonalPlanRepository("ui-switch-B")) }
        compose.onNodeWithTag("personal_today_create").assertExists()
        compose.onNodeWithTag("personal_today_start").assertDoesNotExist()
        compose.runOnIdle { state = PersonalPlanState(repoA) }
        compose.onNodeWithTag("personal_today_start").assertExists()
    }

    @Test fun localPersistenceSurvivesNewRepositoryAndKeepsOwnersAndVersionsSeparate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "plan-test-" + UUID.randomUUID()
        val repo = LocalPersonalPlanRepository(context, uid)
        val zone = TimeZone.getDefault().id
        val date = PlanDates.date(System.currentTimeMillis(), zone)
        val first = Generator.seal(Generator.generate(uid, PersonalPlanConfig(WorkoutLevel.Beginner, 2, date, zone), catalog, System.currentTimeMillis()))
        val initial = repo.save(first, null)
        val second = Generator.seal(Generator.generate(uid, first.config.copy(sessionsPerWeek = 3, weekdays = emptyList()), catalog, System.currentTimeMillis()))
        repo.save(second, initial.activeId)
        val reloaded = LocalPersonalPlanRepository(context, uid).load()
        assertEquals(listOf(first, second), reloaded.plans)
        assertEquals(second.id, reloaded.activeId)
        assertTrue(LocalPersonalPlanRepository(context, "$uid-B").load().plans.isEmpty())
        assertTrue(runCatching { LocalPersonalPlanRepository(context, "$uid-B").save(first, null) }.isFailure)
        val json = PersonalPlanCodec.encode(reloaded, uid)
        assertEquals(reloaded, PersonalPlanCodec.decode(json, uid))
        assertTrue(runCatching { PersonalPlanCodec.decode(json, "$uid-B") }.isFailure)
        assertTrue(runCatching { PersonalPlanCodec.decode("broken", uid) }.isFailure)
    }

    @Test fun chipsToggleFromZeroThroughSevenAndSavingSevenDaysIsAllowedOnce() {
        val state = PersonalPlanState(MemoryPersonalPlanRepository("ui-seven"))
        var saves = 0
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") { PersonalPlanSetupScreen({}, { saves++ }) } }
        }
        setupItem("personal_days_count")
        listOf(0, 2, 4).forEach { compose.onNodeWithTag("personal_day_$it").performScrollTo().performClick().assertIsNotSelected() }
        compose.onNodeWithTag("personal_preview").assertIsNotEnabled()
        (0..6).forEach { day ->
            compose.onNodeWithTag("personal_day_$day").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("personal_days_count").assertTextEquals("${day + 1} sessions/week")
        }
        setupItem("personal_recovery_hint"); compose.onNodeWithTag("personal_recovery_hint").assertExists()
        setupItem("personal_suggest"); compose.onNodeWithTag("personal_suggest").performClick()
        (0..6).forEach { compose.onNodeWithTag("personal_day_$it").assertIsSelected() }
        compose.onNodeWithTag("personal_preview").assertIsEnabled().performClick()
        setupItem("personal_preview_count"); compose.onNodeWithTag("personal_preview_count").assertTextEquals("28 sessions • 4 weeks")
        setupItem("personal_change_day"); compose.onNodeWithTag("personal_change_day").assertIsNotEnabled()
        compose.onNodeWithTag("personal_save").assertIsEnabled().performClick()
        compose.onNodeWithTag("personal_save").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(28, state.active!!.sessions.size); assertEquals(1, state.archive.plans.size) }
    }

    @Test fun oneDayPlanNavigatesWeeksAndRestDaysWithoutShowingAllSessions() {
        val state = PersonalPlanState(MemoryPersonalPlanRepository("ui-one"))
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(true, "en") { PersonalPlanSetupScreen({}, {}) } }
        }
        setupItem("personal_days_count")
        listOf(0, 2, 4).forEach { compose.onNodeWithTag("personal_day_$it").performScrollTo().performClick() }
        val zone = TimeZone.getDefault().id
        val today = PlanDates.date(System.currentTimeMillis(), zone)
        val day = PlanDates.weekday(today, zone)
        compose.onNodeWithTag("personal_day_$day").performScrollTo().performClick()
        compose.onNodeWithTag("personal_preview").performClick()
        setupItem("personal_preview_count"); compose.onNodeWithTag("personal_preview_count").assertTextEquals("4 sessions • 4 weeks")
        setupItem("personal_week_next"); compose.onNodeWithTag("personal_week_next").performClick()
        compose.onNodeWithTag("personal_week_1").assertIsSelected()
        selectDate(PlanDates.add(today, 8, zone))
        setupItem("personal_selected_rest"); compose.onNodeWithTag("personal_selected_rest").assertExists()
        compose.onNodeWithTag("personal_selected_session").assertDoesNotExist()
        setupItem("personal_week_3"); compose.onNodeWithTag("personal_week_3").performClick()
        compose.onNodeWithTag("personal_week_3").assertIsSelected()
        selectDate(PlanDates.add(today, 21, zone))
        setupItem("personal_selected_session"); compose.onAllNodesWithTag("personal_selected_session").assertCountEquals(1)
        compose.onNodeWithTag("personal_save").performClick()
        compose.runOnIdle { assertEquals(4, state.active!!.sessions.size); assertEquals(listOf(day), state.active!!.config.weekdays) }
    }

    @Test fun previousVersionCanBeViewedWithoutReplacingTheActivePlan() = runBlocking {
        val zone = TimeZone.getDefault().id
        val now = System.currentTimeMillis()
        val today = PlanDates.date(now, zone)
        val day = PlanDates.weekday(today, zone)
        val repo = MemoryPersonalPlanRepository("ui-versions")
        val first = Generator.seal(Generator.generate(repo.uid, PersonalPlanConfig(WorkoutLevel.Beginner, 2, today, zone, listOf(day, (day + 3) % 7)), catalog, now))
        repo.save(first, null)
        val second = Generator.seal(Generator.generate(repo.uid, PersonalPlanConfig(WorkoutLevel.Beginner, 3, today, zone), catalog, now))
        repo.save(second, first.id)
        val state = PersonalPlanState(repo)
        var opened: String? = null
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) {
                ProfileAppearance(false, "en") { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { PersonalPlanOverview({}, { opened = it }) } }
            }
        }
        compose.onNodeWithTag("personal_versions").performScrollTo().performClick()
        compose.onNodeWithTag("personal_version_0").performScrollTo().performClick()
        compose.onNodeWithTag("personal_calendar_$today").performScrollTo().performClick()
        compose.onNodeWithTag("personal_session_${first.sessions.first().id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(first.sessions.first().id, opened); assertEquals(second.id, state.active!!.id); assertEquals(2, state.archive.plans.size) }
    }

    @Test fun compactLargeTextThemesAndLanguagesKeepPrimaryActionsAndPickerReachable() {
        val state = PersonalPlanState(MemoryPersonalPlanRepository("ui-compact"))
        var dark by mutableStateOf(true)
        var language by mutableStateOf("vi")
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalPersonalPlans provides state, LocalDensity provides Density(density, 1.5f)) {
                ProfileAppearance(dark, language) {
                    Scaffold(bottomBar = { Box(Modifier.fillMaxWidth().height(80.dp).testTag("test_bottom_bar")) }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).requiredWidth(320.dp)) { PersonalPlanSetupScreen({}, {}) }
                    }
                }
            }
        }
        listOf(true to "vi", false to "en", false to "vi", true to "en").forEach { (d, l) ->
            compose.runOnIdle { dark = d; language = l }
            setupItem("personal_level_Advanced"); compose.onNodeWithTag("personal_level_Advanced").performClick().assertIsSelected()
            setupItem("personal_start_date"); compose.onNodeWithTag("personal_start_date").performClick()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithTag("personal_date_confirm").assertIsDisplayed().performClick()
            capture("plan-setup-${if (d) "dark" else "light"}-$l")
            compose.onNodeWithTag("personal_preview").assertIsDisplayed().assertIsEnabled()
            val button = compose.onNodeWithTag("personal_preview").fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("test_bottom_bar").fetchSemanticsNode().boundsInRoot
            assertTrue(button.bottom <= bar.top + 1)
            setupItem("personal_suggest"); compose.onNodeWithTag("personal_suggest").performClick()
        }
        compose.onNodeWithTag("personal_preview").performClick()
        setupItem("personal_week_2"); compose.onNodeWithTag("personal_week_2").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("personal_save").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun codecReadsLegacyTwoToFiveDayArchivesAndNewOneToSevenDayPlans() {
        val zone = "Asia/Ho_Chi_Minh"
        val now = PlanDates.millis("2026-11-16", zone)
        (1..7).forEach { count ->
            val plan = Generator.seal(Generator.generate("legacy", PersonalPlanConfig(WorkoutLevel.Beginner, count, "2026-11-16", zone), catalog, now))
            // The exact phase-1 field names, without using the current encoder.
            val sessions = plan.sessions.joinToString(",") { s -> """{"id":"${s.id}","date":"${s.date}","templateId":"${s.templateId}","exerciseIds":[${s.exerciseIds.joinToString(",") { "\"$it\"" }}]}""" }
            val json = """{"schemaVersion":1,"uid":"legacy","activeId":"${plan.id}","plans":[{"id":"${plan.id}","uid":"legacy","schemaVersion":1,"generatorVersion":1,"level":"Beginner","sessionsPerWeek":$count,"startDate":"2026-11-16","zoneId":"$zone","weekdays":[${plan.config.weekdays.joinToString(",")}],"repeated":${plan.repeated},"sessions":[$sessions]}]}"""
            val archive = PersonalPlanCodec.decode(json, "legacy")
            assertEquals(plan, archive.active)
            assertEquals(archive, PersonalPlanCodec.decode(PersonalPlanCodec.encode(archive, "legacy"), "legacy"))
        }
    }

    @Test fun datePickerSelectsAnotherDayWithoutChangingChosenWeekdays() {
        val state = PersonalPlanState(MemoryPersonalPlanRepository("ui-picker"))
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") { PersonalPlanSetupScreen({}, {}) } }
        }
        val zone = TimeZone.getDefault().id
        val today = PlanDates.date(System.currentTimeMillis(), zone)
        val tomorrow = PlanDates.add(today, 1, zone)
        setupItem("personal_start_date"); compose.onNodeWithTag("personal_start_date").performClick()
        if (tomorrow.take(7) != today.take(7)) compose.onNodeWithContentDescription("Change to next month").performClick()
        val description = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(PlanDates.pickerMillis(tomorrow)))
        compose.onNode(hasText(description, substring = true) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("personal_date_confirm").performClick()
        setupItem("personal_start_date"); compose.onNodeWithTag("personal_start_date").assert(hasText(PlanDates.display(tomorrow), substring = true))
        setupItem("personal_days_count")
        (0..6).forEach { day ->
            if (day in listOf(0, 2, 4)) compose.onNodeWithTag("personal_day_$day").assertIsSelected()
            else compose.onNodeWithTag("personal_day_$day").assertIsNotSelected()
        }
    }
}
