package com.kgyms.app.ui.home

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.profile.ProfileAppearance
import com.kgyms.app.ui.theme.KgyMsTheme
import org.junit.*
import org.junit.Assert.*

class MemberPreferencesIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val storage get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("kgyms_member_ui", Context.MODE_PRIVATE)
    private var previous: Map<String, *> = emptyMap<String, Any>()

    @Before fun seedOnlyTheMemberUiPreferences() {
        previous = storage.all.toMap()
        storage.edit().clear().putBoolean("dark", true).putString("language", "vi").putInt("weekly_target", 6).commit()
    }
    @After fun restorePreferences() {
        val editor = storage.edit().clear()
        previous.forEach { (key, value) -> when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
        } }
        editor.commit()
    }
    private fun start(emptyTrainer: Boolean = false): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KgyMsTheme(darkTheme = true, dynamicColor = false) {
                if (emptyTrainer) ProfileAppearance(false, "en") {
                    MemberHomeScreen(onAction = {}, data = createMockMemberHome("en").copy(trainer = null))
                } else MemberApp()
            }
        }
        return restoration
    }
    private fun profileAction(tag: String) {
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun homeItem(tag: String) {
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag(tag))
    }

    @Test fun profileAppearanceControlsAllMemberTabsAndPersistsTheChoice() {
        val restoration = start()
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("action_Settings")
        compose.onNodeWithText("Giao diện").performScrollTo().performClick()
        compose.onNodeWithTag("theme_light").performClick()
        compose.onNodeWithTag("language_en").performClick()
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_message_ok").performClick()
        compose.onNodeWithText("Settings").assertExists()
        assertFalse(storage.getBoolean("dark", true))
        assertEquals("en", storage.getString("language", "vi"))
        compose.onNodeWithTag("profile_back").performClick()
        compose.onNodeWithTag("member_tab_home").performClick()
        homeItem("home_action_workouts")
        compose.onNodeWithText("Workouts").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("member_tab_home").assertIsSelected()
        compose.onNodeWithTag("member_tab_qr").performClick()
        compose.onNodeWithText("QR services").assertExists()
        compose.onNodeWithTag("member_tab_plan").performClick()
        compose.onNodeWithText("My Plan").assertExists()
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("member_screen_progress").assertIsDisplayed()
    }

    @Test fun savedFitnessTargetImmediatelyChangesHomeProgressAndSurvivesRestoration() {
        val restoration = start()
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("action_FitnessGoals")
        compose.onNodeWithTag("goal_decrease").performClick()
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_message_ok").performClick()
        assertEquals(5, storage.getInt("weekly_target", 0))
        compose.onNodeWithTag("profile_back").performClick()
        compose.onNodeWithTag("member_tab_home").performClick()
        homeItem("home_weekly_goal")
        compose.onNodeWithText("/5", substring = true, useUnmergedTree = true).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("/5", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun qrOptionsHaveDistinctHonestPreviewsAndBackReturnsHome() {
        start()
        compose.onNodeWithTag("member_tab_qr").performClick().assertIsSelected()
        compose.onNodeWithTag("qr_checkin").performClick()
        compose.onNodeWithText("Chưa bật camera", substring = true).assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("qr_payment").performClick()
        compose.onNodeWithText("Chưa tạo mã", substring = true).assertExists()
        Espresso.pressBack()
        Espresso.pressBack()
        compose.onNodeWithTag("member_tab_home").assertIsSelected()
    }

    @Test fun unassignedTrainerAndBookingEntryNeverPretendToSubmit() {
        start(emptyTrainer = true)
        homeItem("home_action_pt")
        compose.onNodeWithTag("home_action_pt").performClick()
        compose.onNodeWithText("You have no assigned trainer", substring = true).assertExists()
        compose.onNodeWithTag("pt_chat").assertDoesNotExist()
        compose.onNodeWithTag("pt_booking").performClick()
        compose.onNodeWithText("No appointment has been submitted", substring = true).assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("You have no assigned trainer", substring = true).assertExists()
    }
}
