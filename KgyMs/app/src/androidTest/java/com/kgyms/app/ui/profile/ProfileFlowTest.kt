package com.kgyms.app.ui.profile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.kgyms.app.ui.theme.KgyMsTheme
import org.junit.Rule
import org.junit.Test

/** Feature-level checks: destinations, retained state, validation and mock-only confirmations. */
class ProfileFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun start(): StateRestorationTester {
        return StateRestorationTester(compose).also { tester ->
            tester.setContent { KgyMsTheme(darkTheme = true, dynamicColor = false) { ProfileFlow() } }
        }
    }

    private fun open(action: ProfileAction) {
        val tag = "action_${action.name}"
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun back() { compose.onNodeWithTag("profile_back").performClick() }
    private fun dismissMessage() { compose.onNodeWithTag("profile_message_ok").performClick() }
    private fun save() { compose.onNodeWithTag("profile_save").performScrollTo().performClick() }

    @Test fun editedProfileAndSwitchesSurviveBackAndRecreation() {
        val restoration = start()
        open(ProfileAction.EditProfile)
        compose.onNodeWithTag("profile_name").performTextReplacement("Trương Tấn Kiệt")
        compose.onNodeWithTag("profile_email").performTextReplacement("kiet@example.com")
        save()
        dismissMessage()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Trương Tấn Kiệt").assertExists()
        compose.onNodeWithText("kiet@example.com").assertExists()
        open(ProfileAction.EditProfile)
        compose.onNodeWithTag("profile_name").assertTextContains("Trương Tấn Kiệt")
        back()
        open(ProfileAction.NotificationSettings)
        compose.onNodeWithTag("notification_general").assertIsOn().performClick().assertIsOff()
        back()
        open(ProfileAction.NotificationSettings)
        compose.onNodeWithTag("notification_general").assertIsOff()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("notification_general").assertIsOff()
        back()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Trương Tấn Kiệt").assertExists()
    }

    @Test fun settingsValidatePasswordsAndApplyPinkThemeAndEnglishLocally() {
        start()
        open(ProfileAction.Settings)
        compose.onNodeWithText("Mật khẩu").performClick()
        compose.onNodeWithTag("password_current").performTextReplacement("OldPassword1")
        compose.onNodeWithTag("password_new").performTextReplacement("NewPassword2")
        compose.onNodeWithTag("password_confirm").performTextReplacement("Mismatch3")
        save()
        compose.onNodeWithText("Hai mật khẩu mới chưa khớp.").assertExists()
        compose.onNodeWithTag("password_confirm").performTextReplacement("NewPassword2")
        save()
        compose.onNodeWithText("Đã mô phỏng đổi mật khẩu. Mật khẩu tài khoản thật không thay đổi.").assertExists()
        dismissMessage()
        back()
        compose.onNodeWithText("Theme giao diện").performClick()
        compose.onNodeWithTag("theme_light").performClick().assertIsSelected()
        compose.onNodeWithText("White & Pink Premium").assertExists()
        compose.onNodeWithText("English").performClick()
        save()
        dismissMessage()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("White & Pink Premium").assertExists()
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Data & Permissions").performClick()
        compose.onNodeWithText("Profile uses sample data and local state. It requests no permissions and sends no data to a server.").assertExists()
        back()
        compose.onNodeWithText("Delete Account").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Delete Account").performClick()
        compose.onNodeWithTag("profile_confirm").performClick()
        compose.onNodeWithText("Account deletion simulated. No real data was deleted.").assertExists()
        dismissMessage()
        back()
        open(ProfileAction.Logout)
        compose.onNodeWithText("Log out of KgyMs?").assertExists()
        compose.onNodeWithTag("profile_confirm").performClick()
        compose.onNodeWithText("Logout simulated. The real login session is unchanged.").assertExists()
        dismissMessage()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Nguyễn Minh Anh").assertExists()
    }

    @Test fun membershipGoalsFaqAndContactHaveWorkingReturnPaths() {
        start()
        open(ProfileAction.Membership)
        compose.onNodeWithText("Premium Member").assertExists()
        back()
        open(ProfileAction.FitnessGoals)
        compose.onNodeWithTag("goal_weight").performScrollTo().performTextReplacement("70")
        save()
        dismissMessage()
        back()
        open(ProfileAction.FitnessGoals)
        compose.onNodeWithTag("goal_weight").performScrollTo().assertTextContains("70")
        back()
        open(ProfileAction.HelpAndSupport)
        compose.onNodeWithTag("faq_search").performTextReplacement("xyz-no-match")
        compose.onNodeWithText("Không tìm thấy câu hỏi phù hợp.").assertExists()
        compose.onNodeWithTag("faq_search").performTextReplacement("Tài khoản")
        compose.onNodeWithText("Tài khoản & bảo mật").performClick()
        compose.onNodeWithText("Làm sao đổi thông tin cá nhân?").assertExists()
        compose.onNodeWithText("Liên hệ", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Website").performClick()
        compose.onNodeWithText("Website\n\nKênh hỗ trợ mẫu. Chưa mở ứng dụng, gọi điện hoặc website thật.").assertExists()
        dismissMessage()
        back()
        compose.onNodeWithTag("faq_search").assertTextContains("Tài khoản")
        back()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Nguyễn Minh Anh").assertExists()
    }
}
