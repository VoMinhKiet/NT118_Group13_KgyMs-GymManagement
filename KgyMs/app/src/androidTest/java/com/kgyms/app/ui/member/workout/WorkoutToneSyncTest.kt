package com.kgyms.app.ui.member.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.member.home.MemberHomeScreen
import com.kgyms.app.data.mock.createMockMemberHome
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.member.MemberPreferences
import com.kgyms.app.data.mock.MockMemberWorkoutDataSource
import com.kgyms.app.ui.member.profile.ProfileAppearance
import org.junit.Rule
import org.junit.Test

class WorkoutToneSyncTest {
    @get:Rule val compose = createComposeRule()

    private fun scroll(tag: String) {
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag(tag))
    }

    @Test fun planSessionAndExerciseKeepNavigationAndVideoPlaceholder() {
        compose.setContent { ProfileAppearance(true, "en") { MemberWorkoutScreen("plan", 0, {}, {}) } }
        val session = MockMemberWorkoutDataSource.snapshot(System.currentTimeMillis()).sessions.first { it.workoutId == "upper-body" }
        scroll("scheduled_${session.id}")
        compose.onNodeWithTag("scheduled_${session.id}").performClick()
        scroll("workout_start")
        compose.onNodeWithTag("workout_start").performClick()
        scroll("exercise_press")
        compose.onNodeWithTag("exercise_press").performClick()
        scroll("exercise_play")
        compose.onNodeWithTag("exercise_play").performClick()
        compose.onNodeWithText("Video guide is being updated.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithTag("workout_back").performClick()
        scroll("workout_start")
        compose.onNodeWithTag("workout_start").performClick()
        compose.onNodeWithText("does not count toward your weekly goal.", substring = true).assertIsDisplayed()
    }

    @Test fun compactLibrarySupportsThemesLanguagesEmptyResultsAndReturnPath() {
        var dark by mutableStateOf(true)
        var language by mutableStateOf("en")
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                ProfileAppearance(dark, language) {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight()) {
                        MemberWorkoutScreen("library", 0, {}, {})
                    }
                }
            }
        }
        listOf(true to "en", false to "en", false to "vi", true to "vi").forEach { (d, l) ->
            compose.runOnIdle { dark = d; language = l }
            scroll("workout_search")
            compose.onNodeWithTag("workout_search").performTextReplacement("m2b-no-matching-exercise")
            val emptyLabel = if (l == "vi") "Chưa tìm thấy bài phù hợp" else "No matching exercises"
            compose.onNodeWithTag("workout_list").performScrollToNode(hasText(emptyLabel))
            compose.onNodeWithText(emptyLabel).assertIsDisplayed()
            scroll("workout_clear_filters")
            compose.onNodeWithTag("workout_clear_filters").performClick()
        }
        scroll("library_press")
        compose.onNodeWithTag("library_press").performClick()
        compose.onNodeWithTag("exercise_play").assertExists()
        compose.onNodeWithTag("workout_back").performClick()
        scroll("workout_search")
        compose.onNodeWithTag("workout_search").assertExists()
    }

    @Test fun homeAndPlanUseSameTargetAndConfirmedCatalogProjection() {
        val now = System.currentTimeMillis()
        val catalog = MockMemberWorkoutDataSource.snapshot(now)
        var home by mutableStateOf(true)
        var target by mutableIntStateOf(4)
        compose.setContent {
            CompositionLocalProvider(LocalMemberPreferences provides MemberPreferences(
                dark = true, language = "en", weeklyTarget = target,
                setAppearance = { _, _ -> }, setWeeklyTarget = { target = it },
            )) {
                ProfileAppearance(true, "en") {
                    if (home) MemberHomeScreen(onAction = {}, data = createMockMemberHome("en", target, now))
                    else MemberWorkoutScreen("plan", 0, {}, {}, catalog = catalog)
                }
            }
        }
        val completed = createMockMemberHome("en", target, now).weeklyGoal.completed
        compose.onNodeWithText("$completed / 4").assertExists()
        compose.runOnIdle { home = false }
        compose.onNodeWithTag("workout_weekly_goal_count").assertTextEquals("$completed / 4")
        compose.runOnIdle { target = 5 }
        compose.onNodeWithTag("workout_weekly_goal_count").assertTextEquals("$completed / 5")
        compose.runOnIdle { home = true }
        compose.onNodeWithText("$completed / 5").assertExists()
    }
}
