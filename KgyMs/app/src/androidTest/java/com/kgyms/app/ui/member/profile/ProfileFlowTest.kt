package com.kgyms.app.ui.member.profile

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kgyms.app.data.local.FitnessGoalPreferences
import com.kgyms.app.data.local.ProfileDevicePreferences
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.User
import com.kgyms.app.data.model.memberAccountKey
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.theme.KgyMsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Profile navigation, validation and persistence on Android. */
class ProfileFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun start(): StateRestorationTester {
        return StateRestorationTester(compose).also { tester ->
            tester.setContent { KgyMsTheme { ProfileFlow() } }
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

    @Test fun settingsValidatePasswordsAndApplyLightThemeAndEnglishLocally() {
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
        compose.onNodeWithText("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.").assertExists()
        dismissMessage()
        back()
        compose.onNodeWithText("Giao diện").performClick()
        compose.onNodeWithTag("theme_light").performClick().assertIsSelected()
        compose.onNodeWithText("Xám nhạt • Trắng • Tím").assertExists()
        compose.onNodeWithTag("language_en").performClick()
        save()
        dismissMessage()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Light", substring = true).assertExists()
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Data & Permissions").performClick()
        compose.onNodeWithText("Personal details are stored in your Firebase account profile.", substring = true).assertExists()
        back()
        compose.onNodeWithText("Account deletion help").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Account deletion help").performClick()
        compose.onNodeWithTag("profile_deletion_contact").performClick()
        compose.onNodeWithText("Contact Support").assertExists()
        back()
        back()
        open(ProfileAction.Logout)
        compose.onNodeWithText("You are signed out.").assertExists()
        compose.onNodeWithTag("profile_confirm").assertDoesNotExist()
        dismissMessage()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Nguyễn Minh Anh").assertExists()
    }

    @Test fun membershipGoalsFaqAndContactHaveWorkingReturnPaths() {
        start()
        open(ProfileAction.Membership)
        compose.onNodeWithTag("packages_tab_owned").assertIsSelected()
        compose.onNodeWithText("Vui lòng đăng nhập để xem gói tập và hội viên.").assertExists()
        compose.onNodeWithText("Hội viên Premium").assertDoesNotExist()
        compose.onNodeWithTag("packages_back").performClick()
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
        compose.onNodeWithText("Website và mạng xã hội chưa có địa chỉ hỗ trợ được cấu hình.").assertExists()
        compose.onNodeWithText("Hotline CSKH").performClick()
        compose.onNodeWithText("Mở trình quay số?").assertExists()
        compose.onNodeWithText("Không").performClick()
        back()
        compose.onNodeWithTag("faq_search").assertTextContains("Tài khoản")
        back()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Nguyễn Minh Anh").assertExists()
    }

    @Test fun devicePreferencesRestoreForTheOwnerAcrossInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = context.getSharedPreferences("profile-instrumentation", Context.MODE_PRIVATE)
        val uid = UUID.randomUUID().toString()
        val first = ProfileDevicePreferences(storage, uid)
        val goals = FitnessGoalPreferences("weight", "70.5", "high")
        assertTrue(first.saveGoals(5, goals))
        assertTrue(first.saveNotification("sound", true))
        assertTrue(first.saveNotification("general", false))

        val reopened = ProfileDevicePreferences(storage, uid)
        assertEquals(goals, reopened.goals())
        assertEquals(5, storage.getInt("weekly_target_" + memberAccountKey(uid), 0))
        assertTrue(reopened.notification("sound", false))
        assertFalse(reopened.notification("general", true))

        val second = ProfileDevicePreferences(storage, "second-$uid")
        assertEquals(FitnessGoalPreferences(), second.goals())
        assertFalse(second.notification("sound", false))
        assertTrue(second.notification("general", true))
    }

    @Test fun offlineAndRejectedSavesKeepTheDraftForRetry() {
        var saved = User(uid = "profile-owner", fullName = "Original", email = "owner@example.invalid")
        var reject = true
        var writes = 0
        lateinit var account: MemberAccountState
        val repository = object : MemberProfileDataSource {
            override suspend fun load(uid: String) = saved
            override suspend fun save(uid: String, edit: MemberProfileEdit) {
                assertEquals(saved.uid, uid)
                writes++
                if (reject) throw IllegalStateException("Permission denied")
                saved = saved.copy(fullName = edit.fullName)
            }
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            account = remember { MemberAccountState(MemberAccountSession(saved.uid, repository) { saved.uid }, scope, saved) }
            CompositionLocalProvider(LocalMemberAccount provides account) { ProfileFlow() }
        }
        open(ProfileAction.EditProfile)
        compose.onNodeWithTag("profile_name").performTextReplacement("New display name")
        compose.runOnIdle { account.updateOffline(true) }
        compose.onNodeWithTag("profile_save").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, writes); account.updateOffline(false) }
        save()
        compose.onNodeWithText("Không thể lưu hoặc tải lại hồ sơ.", substring = true).assertExists()
        compose.onNodeWithTag("profile_name").assertTextContains("New display name")
        compose.runOnIdle { assertEquals("Original", saved.fullName); reject = false }
        save()
        dismissMessage()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("New display name").assertExists()
        assertEquals(2, writes)
    }
}
