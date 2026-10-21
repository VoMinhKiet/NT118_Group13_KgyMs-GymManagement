package com.kgyms.app.ui.workout

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.kgyms.app.ui.member.MemberApp
import org.junit.Rule
import org.junit.Test

class MemberWorkoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recommendationTrackingSurvivesTabsAndRecreation() {
        val restore = StateRestorationTester(compose)
        restore.setContent { MemberApp() }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("home_workout_upper-body"))
        compose.onNodeWithTag("home_workout_upper-body").performClick()
        compose.onNodeWithTag("plan_content_workout").performScrollToNode(hasTestTag("mark_press"))
        compose.onNodeWithTag("mark_press").performClick().assertIsOn()
        compose.onNodeWithTag("member_tab_profile").performClick()
        compose.onNodeWithTag("member_tab_plan").performClick()
        compose.onNodeWithTag("mark_press").assertIsOn()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("mark_press").assertIsOn()
        compose.onNodeWithTag("exercise_press").performClick()
        compose.onNodeWithTag("workout_back").performClick()
        compose.onNodeWithTag("plan_content_workout").performScrollToNode(hasTestTag("mark_press"))
        compose.onNodeWithTag("mark_press").assertIsOn()
    }
    @Test fun librarySearchAndTrainerEntryResolve() {
        compose.setContent { MemberApp() }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("home_workouts_all"))
        compose.onNodeWithTag("home_workouts_all").performClick()
        compose.onNodeWithTag("workout_search").performTextInput("cable row")
        compose.onNodeWithTag("plan_content_workout").performScrollToNode(hasTestTag("library_row"))
        compose.onNodeWithTag("library_row").performClick()
        compose.onNodeWithTag("workout_back").performClick()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("home_action_pt"))
        compose.onNodeWithTag("home_action_pt").performClick()
        compose.onNodeWithTag("pt_workout").performClick()
        compose.onNodeWithTag("plan_content_workout").performScrollToNode(hasTestTag("session_pt-lower"))
        compose.onNodeWithTag("session_pt-lower").assertIsDisplayed()
    }
}
