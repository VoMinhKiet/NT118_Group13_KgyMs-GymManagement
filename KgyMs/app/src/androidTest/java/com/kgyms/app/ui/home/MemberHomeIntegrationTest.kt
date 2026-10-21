package com.kgyms.app.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.theme.KgyMsTheme
import org.junit.Rule
import org.junit.Test

/** Runs against the real stage-1 shell; no Auth or network is invoked. */
class MemberHomeIntegrationTest {
    @get:Rule val compose = createComposeRule()

    private fun start(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KgyMsTheme(darkTheme = true, dynamicColor = false) { MemberApp() }
        }
        return restoration
    }

    private fun homeItem(tag: String) {
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag(tag))
    }

    private fun previewAndClose(tag: String) {
        homeItem(tag)
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("home_preview").assertIsDisplayed()
        compose.onNodeWithTag("home_preview_close").performClick()
        compose.onNodeWithTag("home_preview").assertDoesNotExist()
    }

    @Test fun homeSectionsAndEveryPreviewHaveWorkingDismissal() {
        start()
        compose.onNodeWithTag("member_screen_home").assertIsDisplayed()
        previewAndClose("home_notifications")
        previewAndClose("home_action_pt")
        previewAndClose("home_workout_upper-body")
        previewAndClose("home_meal_chicken-bowl")
        previewAndClose("home_membership")
        homeItem("home_weekly_activity")
        compose.onNodeWithTag("home_weekly_activity").assertIsDisplayed()
        compose.onNodeWithTag("member_tab_qr").assertIsDisplayed()
    }

    @Test fun shortcutsAndSeeAllUseExistingTabsAndBackReturnsHome() {
        start()
        listOf("home_action_workouts", "home_action_nutrition", "home_workouts_all", "home_meals_all").forEach { tag ->
            homeItem(tag)
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag("member_screen_plan").assertIsDisplayed()
            compose.onNodeWithTag("member_tab_plan").assertIsSelected()
            val area = if (tag.contains("nutrition") || tag.contains("meals")) "nutrition" else "workout"
            compose.onNodeWithTag("plan_content_$area").assertIsDisplayed()
            Espresso.pressBack()
            compose.onNodeWithTag("plan_section_overview").assertIsSelected()
            Espresso.pressBack()
            compose.onNodeWithTag("member_tab_home").assertIsSelected()
        }
        homeItem("home_action_progress")
        compose.onNodeWithTag("home_action_progress").performClick()
        compose.onNodeWithTag("member_screen_progress").assertIsDisplayed()
        Espresso.pressBack()
        homeItem("home_workout_upper-body")
        compose.onNodeWithTag("home_workout_upper-body").performClick()
        compose.onNodeWithTag("home_preview_plan").performClick()
        compose.onNodeWithTag("member_screen_plan").assertIsDisplayed()
    }

    @Test fun avatarProfileBackAndScrollStateSurviveTabChangesAndRecreation() {
        val restoration = start()
        compose.onNodeWithTag("home_avatar").performClick()
        compose.onNodeWithTag("profile_list").assertIsDisplayed()
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_EditProfile"))
        compose.onNodeWithTag("action_EditProfile").performClick()
        compose.onNodeWithTag("member_tab_profile").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithTag("profile_list").assertIsDisplayed()
        compose.onNodeWithTag("member_tab_home").performClick()
        homeItem("home_membership")
        compose.onNodeWithTag("member_tab_plan").performClick()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("home_membership").assertIsDisplayed()
        compose.onNodeWithTag("home_membership").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("home_preview").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("home_preview").assertDoesNotExist()
        compose.onNodeWithTag("member_tab_home").assertIsSelected()
    }
}
