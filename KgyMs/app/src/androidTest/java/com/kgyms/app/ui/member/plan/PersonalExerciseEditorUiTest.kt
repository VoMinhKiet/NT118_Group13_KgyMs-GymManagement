package com.kgyms.app.ui.member.plan

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.local.PersonalPlanCodec
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.utils.ExerciseEditScope
import com.kgyms.app.utils.PersonalPlanEditor as Editor
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

/** Only test UIDs and app-private local storage; no Firebase reads or writes. */
class PersonalExerciseEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private val zone = TimeZone.getDefault().id
    private val today = PlanDates.date(System.currentTimeMillis(), zone)
    private fun plan(uid: String = "exercise-ui", count: Int = 1) = Generator.seal(Generator.generate(uid,
        PersonalPlanConfig(WorkoutLevel.Beginner, count, today, zone,
            (0 until count).map { (PlanDates.weekday(today, zone) + it * 2) % 7 }), catalog, System.currentTimeMillis()))
    private fun editorItem(tag: String) {
        compose.onNodeWithTag("personal_editor_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun setupItem(tag: String) {
        compose.onNodeWithTag("personal_setup").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun workoutItem(tag: String) {
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag(tag))
    }

    @Test fun createRemoveAddReplaceSaveThenHomeAndPlanOpenActualEditedWorkout() {
        val state = PersonalPlanState(MemoryPersonalPlanRepository("exercise-ui-flow"))
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { MemberApp() }
        }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_create"))
        compose.onNodeWithTag("personal_today_create").performClick()
        setupItem("personal_days_count")
        val defaultDays = setOf(0, 2, 4)
        val day = PlanDates.weekday(today, zone)
        (0..6).filter { (it in defaultDays) != (it == day) }.forEach { compose.onNodeWithTag("personal_day_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("personal_preview").performClick()
        setupItem("personal_edit_exercises"); compose.onNodeWithTag("personal_edit_exercises").performClick()
        editorItem("personal_remove_squat"); compose.onNodeWithTag("personal_remove_squat").performClick()
        editorItem("personal_add_exercise"); compose.onNodeWithTag("personal_add_exercise").performClick()
        editorItem("personal_choose_bridge"); compose.onNodeWithTag("personal_choose_bridge").performClick()
        editorItem("personal_swap_press"); compose.onNodeWithTag("personal_swap_press").performClick()
        compose.onNodeWithTag("personal_choose_row").assertDoesNotExist()
        compose.onNodeWithTag("personal_choose_plank").assertDoesNotExist()
        editorItem("personal_choose_curl"); compose.onNodeWithTag("personal_choose_curl").performClick()
        compose.onNodeWithTag("personal_editor_save").performClick()
        compose.runOnIdle { assertNull(state.active) } // Preview edits have not touched the repository.
        compose.onNodeWithTag("personal_save").performClick()
        workoutItem("personal_saved_notice")
        val saved = state.active!!
        val session = saved.sessions.first()
        assertEquals(listOf("curl", "row", "bridge"), session.exerciseIds)
        assertNotEquals(session.exerciseIds, saved.sessions[1].exerciseIds)
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"))
        compose.onNodeWithTag("personal_today_start").performClick()
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        session.exerciseIds.forEach { id -> workoutItem("mark_$id"); compose.onNodeWithTag("mark_$id").assertExists() }
        compose.onNodeWithTag("mark_squat").assertDoesNotExist()
        compose.onNodeWithTag("mark_press").assertDoesNotExist()
        compose.onNodeWithTag("workout_back").performClick()
        workoutItem("personal_session_${session.id}"); compose.onNodeWithTag("personal_session_${session.id}").performClick()
        workoutItem("hero_personal-workout-${session.id}"); compose.onNodeWithTag("hero_personal-workout-${session.id}").assertExists()
    }

    @Test fun savedEditorCancelEmptyDraftRestoreAndRotationKeepRepositoryUnchangedUntilSave() = runBlocking<Unit> {
        val original = plan()
        val repo = MemoryPersonalPlanRepository(original.uid); repo.save(original, null)
        val state = PersonalPlanState(repo)
        val restore = StateRestorationTester(compose)
        restore.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") {
                Column(Modifier.verticalScroll(rememberScrollState())) { PersonalPlanOverview({}, {}) }
            } }
        }
        compose.onNodeWithTag("personal_edit_exercises").performScrollTo().performClick()
        compose.onNodeWithTag("personal_scope_session").performScrollTo().assertIsSelected()
        original.sessions.first().exerciseIds.forEach { id -> editorItem("personal_remove_$id"); compose.onNodeWithTag("personal_remove_$id").performClick() }
        compose.onNodeWithTag("personal_editor_save").assertIsNotEnabled()
        editorItem("personal_editor_empty"); compose.onNodeWithTag("personal_editor_empty").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("personal_editor_save").assertIsNotEnabled()
        editorItem("personal_restore_exercises"); compose.onNodeWithTag("personal_restore_exercises").performClick()
        compose.onNodeWithTag("personal_editor_save").assertIsEnabled()
        editorItem("personal_remove_squat"); compose.onNodeWithTag("personal_remove_squat").performClick()
        compose.onNodeWithTag("personal_editor_cancel").performClick()
        compose.runOnIdle { assertEquals(original, state.active); assertEquals(1, state.archive.plans.size) }
        compose.onNodeWithTag("personal_edit_exercises").performScrollTo().performClick()
        editorItem("personal_editor_exercise_squat"); compose.onNodeWithTag("personal_editor_exercise_squat").assertExists()
    }

    @Test fun sameTemplateScopeCreatesVersionAndBothHomeAndPlanResolveRevisedContent() = runBlocking<Unit> {
        val original = plan(count = 3)
        val repo = MemoryPersonalPlanRepository(original.uid); repo.save(original, null)
        val state = PersonalPlanState(repo)
        var opened by mutableStateOf<String?>(null)
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(true, "vi") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PersonalPlanToday(System.currentTimeMillis(), {}, { opened = it })
                    PersonalPlanOverview({}, { opened = it })
                }
            } }
        }
        compose.onNodeWithTag("personal_edit_exercises").performScrollTo().performClick()
        editorItem("personal_remove_squat"); compose.onNodeWithTag("personal_remove_squat").performClick()
        editorItem("personal_scope_template"); compose.onNodeWithTag("personal_scope_template").performClick()
        compose.onNodeWithTag("personal_editor_save").performClick()
        compose.waitUntil { state.archive.plans.size == 2 }
        val revision = state.active!!
        assertEquals(original, state.archive.plans.first())
        original.sessions.zip(revision.sessions).forEach { (before, after) ->
            if (before.templateId == original.sessions.first().templateId) assertEquals(before.exerciseIds - "squat", after.exerciseIds)
            else assertEquals(before, after)
        }
        compose.onNodeWithTag("personal_today_start").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(revision.runtimeSessionId(revision.sessions.first()), opened) }
        compose.onNodeWithTag("personal_session_${revision.sessions.first().id}").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(revision.runtimeSessionId(revision.sessions.first()), opened)
            val projected = state.catalog(catalog)
            assertEquals(revision.sessions.first().exerciseIds, projected.workouts.first { it.id == "personal-workout-$opened" }.exerciseIds)
            assertTrue(projected.sessions.all { it.confirmedAt == null && it.completedAt == null })
        }
        compose.onNodeWithTag("personal_edit_exercises").performScrollTo().performClick()
        editorItem("personal_restore_exercises"); compose.onNodeWithTag("personal_restore_exercises").performClick()
        editorItem("personal_editor_exercise_squat"); compose.onNodeWithTag("personal_editor_exercise_squat").assertExists()
        compose.onNodeWithTag("personal_editor_cancel").performClick()
        assertEquals(revision, state.active)
    }

    @Test fun editedLocalArchiveReopensWithSnapshotsAndOldVersionsAndUidIsolation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "exercise-persistence-${UUID.randomUUID()}"
        val original = plan(uid)
        val repo = LocalPersonalPlanRepository(context, uid); repo.save(original, null)
        val revision = Editor.revision(original, Editor.apply(original, original.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog), catalog)
        val saved = repo.save(revision, original.id)
        assertEquals(saved, LocalPersonalPlanRepository(context, uid).load())
        assertEquals(saved, PersonalPlanCodec.decode(PersonalPlanCodec.encode(saved, uid), uid))
        assertEquals(original.sessions.map { it.id }, revision.sessions.map { it.id })
        assertEquals(original.sessions.first().exerciseIds, revision.sessions.first().suggestedExerciseIds)
        assertTrue(LocalPersonalPlanRepository(context, "$uid-other").load().plans.isEmpty())
        assertTrue(runCatching { PersonalPlanCodec.decode(PersonalPlanCodec.encode(saved, uid), "$uid-other") }.isFailure)
    }

    private fun assertCenteredWeekday(day: Int) {
        val button = compose.onNodeWithTag("personal_day_$day").getUnclippedBoundsInRoot()
        val circle = compose.onNodeWithTag("personal_day_circle_$day", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithTag("personal_day_label_$day", useUnmergedTree = true).getUnclippedBoundsInRoot()
        fun near(a: Float, b: Float) = assertEquals(a, b, 1f)
        near((button.right - button.left).value, (button.bottom - button.top).value)
        near((circle.right - circle.left).value, (circle.bottom - circle.top).value)
        near((circle.left + circle.right).value / 2, (label.left + label.right).value / 2)
        near((circle.top + circle.bottom).value / 2, (label.top + label.bottom).value / 2)
        near((button.left + button.right).value / 2, (circle.left + circle.right).value / 2)
        near((button.top + button.bottom).value / 2, (circle.top + circle.bottom).value / 2)
        assertTrue(label.left >= circle.left && label.right <= circle.right && label.top >= circle.top && label.bottom <= circle.bottom)
        // Use measured pixels: subtracting absolute Dp coordinates can turn exactly 48dp into 47.99998dp.
        val layout = compose.onNodeWithTag("personal_day_$day").fetchSemanticsNode().layoutInfo
        val minimumPixels = with(compose.density) { 48.dp.toPx() }
        assertTrue("Day $day touch size: ${layout.width} × ${layout.height}px", layout.width >= minimumPixels && layout.height >= minimumPixels)
    }

    @Test fun weekdaysStayOnOneScrollableRowAt320DpWithLargeFontsInBothThemesAndLanguages() {
        var language by mutableStateOf("vi")
        var dark by mutableStateOf(false)
        var days by mutableStateOf(emptyList<Int>())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ProfileAppearance(dark, language) {
                    Box(Modifier.width(320.dp).testTag("weekday_container").padding(20.dp)) { PersonalWeekdayChips(days) { days = it } }
                }
            }
        }
        listOf(false to "vi", true to "vi", false to "en", true to "en").forEach { (theme, locale) ->
            compose.runOnIdle { dark = theme; language = locale; days = emptyList() }
            val viewport = compose.onNodeWithTag("personal_weekdays").getUnclippedBoundsInRoot()
            val first = compose.onNodeWithTag("personal_day_0").getUnclippedBoundsInRoot()
            (0..6).forEach { day ->
                val node = compose.onNodeWithTag("personal_day_$day").performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= viewport.left - 1.dp && bounds.right <= viewport.right + 1.dp)
                assertEquals(first.top.value, bounds.top.value, 1f)
                assertCenteredWeekday(day)
                node.performClick().assertIsSelected()
            }
            assertEquals(7, days.size)
            compose.onNodeWithTag("personal_day_6").performClick().assertIsNotSelected()
            compose.onNodeWithTag("personal_day_0").performScrollTo().performClick().assertIsNotSelected()
            assertEquals(5, days.size)
        }
    }

    @Test fun sevenEqualCirclesAndCenteredLabelsFitOneRowWithoutScrollingOnPhone() {
        var language by mutableStateOf("vi")
        var dark by mutableStateOf(false)
        var days by mutableStateOf(emptyList<Int>())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                ProfileAppearance(dark, language) {
                    Box(Modifier.requiredWidth(390.dp).padding(20.dp)) { PersonalWeekdayChips(days) { days = it } }
                }
            }
        }
        listOf(false to "vi", true to "vi", false to "en", true to "en").forEach { (theme, locale) ->
            compose.runOnIdle { dark = theme; language = locale; days = emptyList() }
            val viewport = compose.onNodeWithTag("personal_weekdays").getUnclippedBoundsInRoot()
            val first = compose.onNodeWithTag("personal_day_0").getUnclippedBoundsInRoot()
            val scroll = compose.onNodeWithTag("personal_weekdays_scroll").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
            assertEquals(0f, scroll.maxValue(), 0.5f)
            var previousRight: Float? = null
            (0..6).forEach { day ->
                val node = compose.onNodeWithTag("personal_day_$day").assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= viewport.left - 1.dp && bounds.right <= viewport.right + 1.dp)
                assertEquals(first.top.value, bounds.top.value, 1f)
                assertEquals((first.right - first.left).value, (bounds.right - bounds.left).value, 1f)
                previousRight?.let { assertEquals(2f, bounds.left.value - it, 1f) }
                previousRight = bounds.right.value
                assertCenteredWeekday(day)
                node.performClick().assertIsSelected()
            }
            assertEquals(7, days.size)
            compose.onNodeWithTag("personal_day_6").performClick().assertIsNotSelected()
        }
    }

    @Test fun savingLocksButtonsAndFailureRetainsDraftForRetry() = runBlocking<Unit> {
        val original = plan()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val state = PersonalPlanState(object : PersonalPlanRepository {
            override val uid = original.uid
            override suspend fun load() = PersonalPlanArchive(original.id, listOf(original))
            override suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive {
                calls++; gate.await(); error("Test local write failure")
            }
        })
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, "en") {
                Column(Modifier.verticalScroll(rememberScrollState())) { PersonalPlanOverview({}, {}) }
            } }
        }
        compose.onNodeWithTag("personal_edit_exercises").performScrollTo().performClick()
        editorItem("personal_remove_squat"); compose.onNodeWithTag("personal_remove_squat").performClick()
        compose.onNodeWithTag("personal_editor_save").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("personal_editor_cancel").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); gate.complete(Unit) }
        compose.waitUntil { !state.saving }
        compose.onNodeWithTag("personal_editor_save").assertIsEnabled()
        editorItem("personal_editor_error"); compose.onNodeWithTag("personal_editor_error").assertExists()
        compose.onNodeWithTag("personal_editor_exercise_squat").assertDoesNotExist()
        assertEquals(original, state.active)
        compose.onNodeWithTag("personal_editor_cancel").performClick()
    }

    @Test fun revisedWorkoutCanFinishAndRestartWithoutSharingMarksWithAnotherSession() = runBlocking<Unit> {
        val original = plan("exercise-ui-workout", count = 3)
        val revision = Editor.revision(original, Editor.apply(original, original.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog), catalog)
        val repo = MemoryPersonalPlanRepository(original.uid)
        repo.save(original, null); repo.save(revision, original.id)
        val state = PersonalPlanState(repo)
        compose.setContent {
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { MemberApp() }
        }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"))
        compose.onNodeWithTag("personal_today_start").performClick()
        val first = revision.sessions.first()
        workoutItem("hero_personal-workout-${revision.runtimeSessionId(first)}")
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        workoutItem("mark_bridge"); compose.onNodeWithTag("mark_bridge").performClick().assertIsOn()
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        androidx.test.espresso.Espresso.pressBack()
        workoutItem("workout_finished"); compose.onNodeWithTag("workout_finished").assertExists()
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        workoutItem("mark_bridge"); compose.onNodeWithTag("mark_bridge").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("workout_back").performClick()
        val other = revision.sessions.first { it.id != first.id && "bridge" in it.exerciseIds }
        val week = (0..3).first { other.date in PlanDates.weekDates(revision, it) }
        workoutItem("personal_week_$week"); compose.onNodeWithTag("personal_week_$week").performClick()
        workoutItem("personal_calendar_${other.date}"); compose.onNodeWithTag("personal_calendar_${other.date}").performScrollTo().performClick()
        workoutItem("personal_session_${other.id}"); compose.onNodeWithTag("personal_session_${other.id}").performClick()
        workoutItem("workout_start"); compose.onNodeWithTag("workout_start").performClick()
        workoutItem("mark_bridge"); compose.onNodeWithTag("mark_bridge").assertIsOff()
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        assertEquals(revision, state.active)
    }

    @Test fun exhaustedCatalogAndMissingSuggestionsHaveHonestEmptyStates() {
        val original = plan()
        val session = original.sessions.first().copy(templateId = "missing-template", exerciseIds = Editor.eligible(catalog, WorkoutLevel.Beginner).map { it.id })
        compose.setContent { ProfileAppearance(true, "en") {
            PersonalExerciseEditor(original, session, catalog, busy = false, onDismiss = {}, onSave = { _, _ -> false })
        } }
        editorItem("personal_restore_exercises"); compose.onNodeWithTag("personal_restore_exercises").assertIsNotEnabled()
        editorItem("personal_scope_template"); compose.onNodeWithTag("personal_scope_template").assertIsNotEnabled()
        compose.onNodeWithTag("personal_scope_session").assertIsSelected()
        editorItem("personal_add_exercise"); compose.onNodeWithTag("personal_add_exercise").performClick()
        editorItem("personal_editor_no_choices"); compose.onNodeWithTag("personal_editor_no_choices").assertIsDisplayed()
        compose.onNodeWithTag("personal_picker_back").performClick()
        compose.onNodeWithTag("personal_editor_save").assertIsEnabled()
    }
}
