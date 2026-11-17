package com.kgyms.app.ui.member.plan

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.utils.ExerciseEditScope
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.TimeZone
import java.util.UUID

/** Presentation and app-private test UID storage only. No Firebase access. */
class PersonalPlanPresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private val zone = TimeZone.getDefault().id
    private val now = System.currentTimeMillis()
    private val today = PlanDates.date(now, zone)
    private fun plan(uid: String = "presentation", count: Int = 1) = Generator.seal(Generator.generate(uid,
        PersonalPlanConfig(WorkoutLevel.Beginner, count, today, zone,
            (0 until count).map { (PlanDates.weekday(today, zone) + it * 2) % 7 }), catalog, now))
    private fun item(tag: String) {
        compose.onNodeWithTag("personal_editor_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun capture(tag: String, name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun calendarCirclesFit390AndScrollAt320WithCenteredTextAndDistinctToday() {
        val dates = (0..6).map { PlanDates.add("2026-12-29", it, zone) }
        val scheduled = setOf(dates[0], dates[2], dates[6])
        var selected by mutableStateOf(dates.first())
        var compact by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var language by mutableStateOf("vi")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (compact) 1.5f else 1f)) {
                ProfileAppearance(dark, language) { MemberDashboardAppearance {
                    Box(Modifier.requiredWidth(if (compact) 320.dp else 390.dp).padding(20.dp)) {
                        PersonalPlanDays(dates, zone, selected, dates[1], scheduled, onSelect = { selected = it })
                    }
                } }
            }
        }
        listOf(false, true).forEach { small ->
            listOf(false to "vi", true to "vi", false to "en", true to "en").forEach { (theme, locale) ->
                compose.runOnIdle { compact = small; dark = theme; language = locale; selected = dates.first() }
                val viewport = compose.onNodeWithTag("personal_calendar_strip").getUnclippedBoundsInRoot()
                val scroll = compose.onNodeWithTag("personal_calendar_scroll").fetchSemanticsNode().config[
                    androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
                if (small) assertTrue(scroll.maxValue() > 0) else assertEquals(0f, scroll.maxValue(), 0.5f)
                var firstTop: Float? = null
                var width: Float? = null
                dates.forEach { date ->
                    val node = compose.onNodeWithTag("personal_calendar_$date")
                    if (small) node.performScrollTo()
                    node.assertIsDisplayed()
                    val bounds = node.getUnclippedBoundsInRoot()
                    assertTrue(bounds.left >= viewport.left - 1.dp && bounds.right <= viewport.right + 1.dp)
                    firstTop?.let { assertEquals(it, bounds.top.value, 1f) }; firstTop = bounds.top.value
                    val circle = compose.onNodeWithTag("personal_calendar_circle_$date", true).getUnclippedBoundsInRoot()
                    val label = compose.onNodeWithTag("personal_calendar_number_$date", true).getUnclippedBoundsInRoot()
                    val weekday = compose.onNodeWithTag("personal_calendar_day_$date", true).getUnclippedBoundsInRoot()
                    assertEquals((circle.right - circle.left).value, (circle.bottom - circle.top).value, 1f)
                    width?.let { assertEquals(it, (circle.right - circle.left).value, 1f) }; width = (circle.right - circle.left).value
                    assertEquals((circle.left + circle.right).value / 2, (label.left + label.right).value / 2, 1f)
                    assertEquals((circle.top + circle.bottom).value / 2, (label.top + label.bottom).value / 2, 1f)
                    assertEquals((circle.left + circle.right).value / 2, (weekday.left + weekday.right).value / 2, 1f)
                    assertTrue(label.left >= circle.left && label.right <= circle.right && label.top >= circle.top && label.bottom <= circle.bottom)
                    val layout = node.fetchSemanticsNode().layoutInfo
                    val minimum = with(compose.density) { 48.dp.toPx() }
                    assertTrue(layout.width >= minimum && layout.height >= minimum)
                    if (date in scheduled) compose.onNodeWithTag("personal_calendar_dot_$date", true).assertExists()
                    else compose.onNodeWithTag("personal_calendar_dot_$date", true).assertDoesNotExist()
                    node.performClick().assertIsSelected()
                    assertEquals(date, selected)
                }
                compose.onNodeWithTag("personal_calendar_today_${dates[1]}", true).assertExists()
                compose.onNodeWithTag("personal_calendar_${dates[1]}").assertIsNotSelected()
                if (!small) capture("personal_calendar_strip", "plan-circles-${if (theme) "dark" else "light"}-$locale.png")
            }
        }
    }

    @Test fun editorActionsAndScopeSpacingSupportLongNamesAndLargeFontsInBothThemes() {
        val original = plan()
        val first = original.sessions.first().exerciseIds.first()
        val longCatalog = catalog.copy(exercises = catalog.exercises.map { if (it.id != first) it else it.copy(title = WorkoutText(
            "Squat ôm tạ — hướng dẫn kiểm soát tư thế và chuyển động trong suốt bài tập",
            "Goblet squat — maintain a controlled posture throughout every repetition")) })
        var compact by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var language by mutableStateOf("vi")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (compact) 1.5f else 1f)) {
                ProfileAppearance(dark, language) { MemberDashboardAppearance {
                    Box(Modifier.requiredWidth(if (compact) 320.dp else 390.dp)) {
                        PersonalExerciseEditor(original, original.sessions.first(), longCatalog, false, {},
                            modifier = Modifier.requiredWidth(if (compact) 320.dp else 390.dp), onSave = { _, _ -> false })
                    }
                } }
            }
        }
        listOf(false, true).forEach { small ->
            listOf(false to "vi", true to "vi", false to "en", true to "en").forEach { (theme, locale) ->
                compose.runOnIdle { compact = small; dark = theme; language = locale }
                item("personal_swap_$first")
                val swap = compose.onNodeWithTag("personal_swap_$first").assertIsDisplayed().getUnclippedBoundsInRoot()
                val remove = compose.onNodeWithTag("personal_remove_$first").assertIsDisplayed().getUnclippedBoundsInRoot()
                assertEquals(swap.top.value, remove.top.value, 1f)
                assertEquals((swap.right - swap.left).value, (remove.right - remove.left).value, 1f)
                assertEquals((swap.bottom - swap.top).value, (remove.bottom - remove.top).value, 1f)
                assertTrue(swap.right <= remove.left)
                val card = compose.onNodeWithTag("personal_editor_exercise_$first").getUnclippedBoundsInRoot()
                val title = compose.onNodeWithTag("personal_editor_title_$first").getUnclippedBoundsInRoot()
                assertTrue(title.left >= card.left && title.right <= card.right && title.top >= card.top && title.bottom <= card.bottom)
                compose.onNodeWithTag("personal_editor_save").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithTag("personal_editor_cancel").assertIsDisplayed()
                if (!small && locale == "vi") capture("personal_exercise_editor", "plan-editor-${if (theme) "dark" else "light"}.png")
                item("personal_scope_section")
                val one = compose.onNodeWithTag("personal_scope_session").assertIsDisplayed().getUnclippedBoundsInRoot()
                val same = compose.onNodeWithTag("personal_scope_template").assertIsDisplayed().getUnclippedBoundsInRoot()
                val description = compose.onNodeWithTag("personal_scope_description").assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue(same.top >= one.bottom + 8.dp && description.top >= same.bottom + 8.dp)
                compose.onNodeWithTag("personal_scope_session").assertIsSelected()
                compose.onNodeWithTag("personal_scope_template").performClick().assertIsSelected()
                compose.onNodeWithTag("personal_scope_session").performClick().assertIsSelected()
                compose.onAllNodes(hasTestTag("personal_editor_save")).assertCountEquals(1)
                if (!small && locale == "vi") capture("personal_exercise_editor", "plan-editor-scope-${if (theme) "dark" else "light"}.png")
            }
        }
    }

    @Test fun namesFollowStoredOrderAcrossEditsReopenActiveChangesAndOwners() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "presentation-${UUID.randomUUID()}"
        val repo = LocalPersonalPlanRepository(context, uid)
        val first = plan(uid); repo.save(first, null)
        var current by mutableStateOf(PersonalPlanState(repo))
        var language by mutableStateOf("vi")
        compose.setContent {
            val state = current
            LaunchedEffect(state) { state.load() }
            CompositionLocalProvider(LocalPersonalPlans provides state) { ProfileAppearance(false, language) { MemberDashboardAppearance {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { PersonalPlanOverview({}, {}) }
            } } }
        }
        compose.waitUntil(5_000) { !current.loading }
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Lịch tập 1")
        val second = plan(uid, 3); repo.save(second, first.id); current.load()
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Lịch tập 2")
        assertTrue(current.saveExercises(second, second.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog, second.id))
        val third = current.active!!
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Lịch tập 3")
        compose.onNodeWithTag("personal_versions").performScrollTo().performClick()
        (0..2).forEach { i -> compose.onNodeWithTag("personal_version_name_$i", true).performScrollTo().assertTextEquals("Lịch tập ${i + 1}") }
        compose.onNodeWithText("${PlanDates.display(today)} • Đang dùng", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("personal_version_0").performScrollTo().performClick()
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Lịch tập 1")
        assertEquals(third.id, current.active!!.id)
        val reopened = PersonalPlanState(LocalPersonalPlanRepository(context, uid))
        compose.runOnIdle { current = reopened }
        compose.waitUntil(5_000) { !reopened.loading }
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Lịch tập 3")
        repo.save(first, third.id); current.load()
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Lịch tập 1")
        assertEquals(listOf(first, second, third), current.archive.plans)
        compose.runOnIdle { language = "en" }
        compose.onNodeWithTag("personal_plan_name").assertTextEquals("Workout plan 1")
        val otherUid = "$uid-B"; val otherRepo = LocalPersonalPlanRepository(context, otherUid)
        otherRepo.save(plan(otherUid), null)
        compose.runOnIdle { current = PersonalPlanState(otherRepo) }
        compose.waitUntil(5_000) { !current.loading }
        compose.onNodeWithTag("personal_plan_name").performScrollTo().assertTextEquals("Workout plan 1")
        assertEquals(1, current.archive.plans.size)
        assertEquals(listOf(first, second, third), LocalPersonalPlanRepository(context, uid).load().plans)
    }
}
