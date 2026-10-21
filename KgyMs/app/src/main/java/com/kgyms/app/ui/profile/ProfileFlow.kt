package com.kgyms.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kgyms.app.ui.member.LocalMemberPreferences

internal enum class ProfileDestination {
    Profile, Membership, Edit, Goals, Notifications, Settings, Password, Appearance, Privacy, Help, Contact,
}

/**
 * Temporary internal navigation for the Profile feature, since the current branch
 * has no shared NavHost. No Auth/Admin destinations or backend calls are owned here.
 * Each destination keeps its form state across back navigation and recreation.
 * A future shared host can supply its existing member bottom bar and Membership UI.
 */
@Composable
fun ProfileFlow(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    membershipScreen: @Composable (ProfileUiData, () -> Unit) -> Unit = { member, back ->
        ProfileMembershipScreen(member, back)
    },
) {
    var stack by rememberSaveable { mutableStateOf(ProfileDestination.Profile.name) }
    var name by rememberSaveable { mutableStateOf(MockMemberProfile.name) }
    var email by rememberSaveable { mutableStateOf(MockMemberProfile.email) }
    var phone by rememberSaveable { mutableStateOf("0901 234 567") }
    var birthday by rememberSaveable { mutableStateOf("01/04/1998") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    val sharedPreferences = LocalMemberPreferences.current
    var standaloneDark by rememberSaveable { mutableStateOf(true) }
    var standaloneLanguage by rememberSaveable { mutableStateOf("vi") }
    val dark = sharedPreferences?.dark ?: standaloneDark
    val language = sharedPreferences?.language ?: standaloneLanguage
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    val destination = ProfileDestination.valueOf(stack.substringAfterLast('|'))
    val profile = MockMemberProfile.copy(
        name = name, email = email,
        initials = name.trim().split(Regex("\\s+")).takeLast(2).joinToString("") { it.take(1).uppercase() },
    )
    val navigate: (ProfileDestination) -> Unit = { target ->
        if (target != destination) stack += "|${target.name}"
    }
    val back: () -> Unit = { if ('|' in stack) stack = stack.substringBeforeLast('|') }
    BackHandler(enabled = '|' in stack) { back() }

    ProfileAppearance(dark, language) {
        val savedProfile = profileText("Đã cập nhật hồ sơ mẫu trong phiên này. Chưa lưu vào cơ sở dữ liệu.", "Sample profile updated for this session. Nothing was saved to a database.")
        val savedGoals = profileText("Đã lưu mục tiêu mẫu trong phiên này.", "Sample goals saved for this session.")
        androidx.compose.foundation.layout.Box(modifier) {
            holder.SaveableStateProvider(destination.name) {
                when (destination) {
                    ProfileDestination.Profile -> ProfileScreen(
                        profile = profile, avatarUri = avatarUri, bottomBar = bottomBar,
                        onAction = { action ->
                            when (action) {
                                ProfileAction.Membership -> navigate(ProfileDestination.Membership)
                                ProfileAction.EditProfile -> navigate(ProfileDestination.Edit)
                                ProfileAction.FitnessGoals -> navigate(ProfileDestination.Goals)
                                ProfileAction.NotificationSettings -> navigate(ProfileDestination.Notifications)
                                ProfileAction.Settings -> navigate(ProfileDestination.Settings)
                                ProfileAction.HelpAndSupport -> navigate(ProfileDestination.Help)
                                ProfileAction.Logout -> confirmation = "logout"
                            }
                        },
                    )
                    ProfileDestination.Membership -> membershipScreen(profile, back)
                    ProfileDestination.Edit -> EditProfileScreen(
                        profile = profile,
                        phone = phone,
                        birthday = birthday,
                        avatarUri = avatarUri,
                        onBack = back,
                        onAvatarSelected = { avatarUri = it },
                    ) { updatedName, updatedEmail, updatedPhone, updatedBirthday ->
                        name = updatedName; email = updatedEmail; phone = updatedPhone; birthday = updatedBirthday
                        back()
                        message = savedProfile
                    }
                    ProfileDestination.Goals -> FitnessGoalsScreen(
                        onBack = back,
                        initialTarget = sharedPreferences?.weeklyTarget ?: 3,
                        onTargetSaved = { sharedPreferences?.setWeeklyTarget?.invoke(it) },
                    ) { message = savedGoals }
                    ProfileDestination.Notifications -> NotificationSettingsScreen(back)
                    ProfileDestination.Settings -> SettingsScreen(dark, language, back, navigate) { confirmation = "delete" }
                    ProfileDestination.Password -> ChangePasswordScreen(back) { message = it }
                    ProfileDestination.Appearance -> ThemeLanguageScreen(dark, language, back) { nextDark, nextLanguage ->
                        if (sharedPreferences != null) sharedPreferences.setAppearance(nextDark, nextLanguage)
                        else { standaloneDark = nextDark; standaloneLanguage = nextLanguage }
                        back()
                        message = if (nextLanguage == "vi") "Đã áp dụng giao diện và ngôn ngữ." else "Appearance and language applied."
                    }
                    ProfileDestination.Privacy -> PrivacyScreen(back)
                    ProfileDestination.Help -> HelpFaqScreen(back) { navigate(ProfileDestination.Contact) }
                    ProfileDestination.Contact -> ContactSupportScreen(back, back) { message = it }
                }
            }
        }
        if (confirmation != null) {
            val deleting = confirmation == "delete"
            val mockDeleted = profileText("Đã mô phỏng yêu cầu xóa tài khoản. Không có dữ liệu thật bị xóa.", "Account deletion simulated. No real data was deleted.")
            val mockLogout = profileText("Đã mô phỏng đăng xuất. Không thay đổi phiên đăng nhập thật.", "Logout simulated. The real login session is unchanged.")
            AlertDialog(
                onDismissRequest = { confirmation = null },
                title = { Text(if (deleting) profileText("Xóa tài khoản?", "Delete account?") else profileText("Đăng xuất khỏi KgyMs?", "Log out of KgyMs?")) },
                text = { Text(if (deleting) profileText("Bạn có chắc muốn xóa tài khoản? Đây chỉ là thao tác mô phỏng, không xóa dữ liệu thật.", "Are you sure? This is a mock action and will not delete real data.") else profileText("Bạn có thể đăng nhập lại bất cứ lúc nào.", "You can sign in again at any time.")) },
                confirmButton = {
                    TextButton(
                        onClick = { confirmation = null; message = if (deleting) mockDeleted else mockLogout },
                        modifier = Modifier.testTag("profile_confirm"),
                    ) { Text(if (deleting) profileText("Xác nhận", "Confirm") else profileText("Đăng xuất", "Log Out")) }
                },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text(profileText("Hủy", "Cancel")) } },
            )
        }
        message?.let { text ->
            AlertDialog(
                onDismissRequest = { message = null },
                title = { Text(profileText("KgyMs • Bản thử nghiệm", "KgyMs • Demo")) },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { message = null }, modifier = Modifier.testTag("profile_message_ok")) { Text("OK") } },
            )
        }
    }
}
