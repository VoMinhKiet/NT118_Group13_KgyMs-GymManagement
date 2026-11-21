package com.kgyms.app.ui.member.profile

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
import com.kgyms.app.ui.member.LocalMemberAvatar
import com.kgyms.app.ui.member.rememberMemberAvatarState
import com.kgyms.app.data.mock.MockMemberProfile
import com.kgyms.app.data.model.ProfileUiData
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.viewmodel.profile.ProfileSaveEvent
import com.kgyms.app.viewmodel.profile.ProfileViewModel
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.memberProfile
import com.kgyms.app.ui.member.LocalMemberAccount
import kotlinx.coroutines.launch
import com.kgyms.app.viewmodel.membership.MembershipViewModel
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.local.FitnessGoalPreferences
import com.kgyms.app.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.kgyms.app.viewmodel.profile.ChangePasswordViewModel
import com.kgyms.app.viewmodel.profile.EmailChangeError

internal enum class ProfileDestination {
    Profile, Membership, MembershipCards, Edit, Goals, Notifications, Settings, Password, Appearance, Privacy, Help, Contact,
}

/**
 * Internal navigation for the Profile feature, since the current branch
 * has no shared NavHost. Membership data is shared between the root summary and detail route.
 * Each destination keeps its form state across back navigation and recreation.
 * The optional checkout callback hands control to an external checkout implementation.
 */
@Composable
fun ProfileFlow(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    onLogout: (() -> Unit)? = null,
    initialProfile: ProfileUiData = MockMemberProfile,
    membershipScreen: (@Composable (ProfileUiData, () -> Unit) -> Unit)? = null,
    membershipViewModel: MembershipViewModel? = null,
    onPackageCheckout: ((String, GymPackage) -> Unit)? = null,
    profileViewModel: ProfileViewModel? = null,
) {
    val shared = LocalMemberAvatar.current
    val avatar = shared ?: rememberMemberAvatarState(initialProfile.name)
    CompositionLocalProvider(LocalMemberAvatar provides avatar) {
        val model = profileViewModel ?: if (LocalMemberAccount.current == null) viewModel<ProfileViewModel>() else null
        ProfileFlowContent(modifier, bottomBar, onLogout, initialProfile, membershipScreen, model, membershipViewModel, onPackageCheckout)
    }
}

@Composable
private fun ProfileFlowContent(
    modifier: Modifier,
    bottomBar: @Composable () -> Unit,
    onLogout: (() -> Unit)?,
    initialProfile: ProfileUiData,
    membershipScreen: (@Composable (ProfileUiData, () -> Unit) -> Unit)?,
    profileViewModel: ProfileViewModel?,
    membershipViewModel: MembershipViewModel?,
    onPackageCheckout: ((String, GymPackage) -> Unit)?,
) {
    val avatar = checkNotNull(LocalMemberAvatar.current)
    val profileState = profileViewModel?.uiState?.collectAsState()?.value
        ?: com.kgyms.app.viewmodel.profile.ProfileUiState()
    val account = LocalMemberAccount.current
    val localScope = rememberCoroutineScope()
    val scope = account?.scope ?: localScope
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { active = true; onDispose { active = false } }
    var stack by rememberSaveable { mutableStateOf(ProfileDestination.Profile.name) }
    // Tên hiển thị dùng chung qua LocalMemberAvatar (khởi tạo từ initialProfile.name).
    var email by rememberSaveable { mutableStateOf(initialProfile.email) }
    var phone by rememberSaveable { mutableStateOf(account?.user?.phone ?: "0901 234 567") }
    var birthday by rememberSaveable { mutableStateOf(account?.user?.birthday ?: "01/04/1998") }
    LaunchedEffect(profileState.user) {
        if (account == null) profileState.user?.let { user ->
            if (user.fullName.isNotBlank()) avatar.name = user.fullName
            email = user.email
            phone = user.phone
            birthday = user.birthday
        }
    }
    val avatarUri = avatar.avatarUri
    val sharedPreferences = LocalMemberPreferences.current
    var standaloneDark by rememberSaveable { mutableStateOf(true) }
    var standaloneLanguage by rememberSaveable { mutableStateOf("vi") }
    val dark = sharedPreferences?.dark ?: standaloneDark
    val language = sharedPreferences?.language ?: standaloneLanguage
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var showDeletionHelp by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = key(account?.uid) { rememberSaveableStateHolder() }
    var savingEmail by remember(account?.uid) { mutableStateOf(false) }
    var savingGoals by remember(account?.uid) { mutableStateOf(false) }
    var goalsFailed by remember(account?.uid) { mutableStateOf(false) }
    var previewGoals by rememberSaveable { mutableStateOf(arrayListOf("strength", "", "moderate")) }
    var previewTarget by rememberSaveable { mutableIntStateOf(3) }
    val currentRoute = stack.substringAfterLast('|')
    val destination = ProfileDestination.entries.firstOrNull { it.name == currentRoute } ?: ProfileDestination.Profile
    val profile = (account?.user ?: profileState.user)?.memberProfile(language) ?: initialProfile.copy(
        name = avatar.name, email = email,
        initials = avatar.initials,
    )
    val membershipModel = membershipViewModel ?: LocalMembershipModel.current ?: if (account != null || profile.isRealAccount || profileState.user != null)
        viewModel<MembershipViewModel>(key = "member-memberships") else null
    val membershipState = membershipState(membershipModel)
    val navigate: (ProfileDestination) -> Unit = { target ->
        if (target != destination) stack += "|${target.name}"
    }
    val back: () -> Unit = { if ('|' in stack) stack = stack.substringBeforeLast('|') }
    BackHandler(enabled = '|' in stack) { if (!savingEmail && !savingGoals && account?.saving != true) back() }

    ProfileAppearance(dark, language) {
        val signedOut = profileText("Bạn chưa đăng nhập.", "You are signed out.")
        val savedProfile = profileText("Đã cập nhật hồ sơ mẫu trong phiên này. Chưa lưu vào cơ sở dữ liệu.", "Sample profile updated for this session. Nothing was saved to a database.")
        val savedGoals = profileText("Đã lưu mục tiêu mẫu trong phiên này.", "Sample goals saved for this session.")
        val savedRemote = profileText("Đã lưu thông tin cá nhân.", "Personal information saved.")
        LaunchedEffect(profileState.saveEvent) {
            when (val event = profileState.saveEvent) {
                is ProfileSaveEvent.Saved -> {
                    avatar.name = event.user.fullName
                    email = event.user.email
                    phone = event.user.phone
                    birthday = event.user.birthday
                    if (destination == ProfileDestination.Edit) {
                        holder.removeState(ProfileDestination.Edit.name)
                        back()
                    }
                    message = buildString {
                        append(if (event.persisted) savedRemote else savedProfile)
                        event.emailVerificationSentTo?.let {
                            append("\n\n")
                            append(if (language == "vi") "Đã gửi email xác nhận tới $it. Email mới sẽ được áp dụng sau khi bạn xác nhận và đăng nhập lại."
                                else "A confirmation email was sent to $it. The new email applies after you confirm it and sign in again.")
                        }
                        event.emailError?.let { append("\n\n"); append(it.message(language)) }
                    }
                }
                is ProfileSaveEvent.Error -> message = event.error.message(language)
                null -> return@LaunchedEffect
            }
            profileViewModel?.consumeSaveEvent()
        }
        androidx.compose.foundation.layout.Box(modifier) {
            holder.SaveableStateProvider(destination.name) {
                when (destination) {
                    ProfileDestination.Profile -> ProfileScreen(
                        profile = profile, avatarUri = avatarUri, bottomBar = bottomBar, membershipState = membershipState,
                        onAction = { action ->
                            when (action) {
                                ProfileAction.Membership -> navigate(ProfileDestination.Membership)
                                ProfileAction.EditProfile -> navigate(ProfileDestination.Edit)
                                ProfileAction.FitnessGoals -> navigate(ProfileDestination.Goals)
                                ProfileAction.NotificationSettings -> navigate(ProfileDestination.Notifications)
                                ProfileAction.Settings -> navigate(ProfileDestination.Settings)
                                ProfileAction.HelpAndSupport -> navigate(ProfileDestination.Help)
                                ProfileAction.Logout -> if (onLogout != null) confirmLogout = true else
                                    run { message = signedOut }
                            }
                        },
                    )
                    ProfileDestination.Membership -> if (membershipScreen != null) membershipScreen(profile, back)
                    else MemberMembershipRoute(membershipModel, membershipState, back, onPackageCheckout)
                    ProfileDestination.MembershipCards -> MemberMembershipRoute(membershipModel, membershipState, back, null, ownedOnly = true)
                    ProfileDestination.Edit -> EditProfileScreen(
                        profile = profile,
                        phone = account?.user?.phone ?: phone,
                        birthday = account?.user?.birthday ?: birthday,
                        saving = account?.saving == true || profileState.isSaving || savingEmail,
                        saveFailed = account?.failed == true,
                        offline = account?.offline == true,
                        avatarUri = avatarUri,
                        onBack = { if (account?.saving != true && !savingEmail && !profileState.isSaving) back() },
                        onAvatarSelected = avatar::select,
                        avatarEditingEnabled = avatar.editable,
                    ) { updatedName, updatedEmail, updatedPhone, updatedBirthday ->
                        if (account == null) {
                            profileViewModel?.savePersonalInfo(updatedName, updatedEmail, updatedPhone, updatedBirthday)
                        } else scope.launch {
                            if (account.save(MemberProfileEdit(updatedName, updatedPhone, updatedBirthday)) && active) {
                                avatar.name = account.user?.fullName.orEmpty()
                                val profileSaved = if (language == "vi") "Đã lưu hồ sơ." else "Profile saved."
                                if (!updatedEmail.equals(account.user?.email, ignoreCase = true)) {
                                    savingEmail = true
                                    try {
                                        UserRepository().requestEmailChange(updatedEmail, account.uid)
                                        if (active) {
                                            if (stack.substringAfterLast('|') == ProfileDestination.Edit.name) {
                                                holder.removeState(ProfileDestination.Edit.name)
                                                back()
                                            }
                                            message = profileSaved + if (language == "vi") " Đã gửi email xác nhận tới $updatedEmail."
                                                else " Verification email sent to $updatedEmail."
                                        }
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        val reason = if (error is FirebaseAuthRecentLoginRequiredException) EmailChangeError.RecentLoginRequired
                                            else if (error is com.google.firebase.FirebaseNetworkException) EmailChangeError.Network
                                            else if (error is com.google.firebase.auth.FirebaseAuthUserCollisionException) EmailChangeError.AlreadyInUse
                                            else EmailChangeError.Failed
                                        if (active) message = "$profileSaved ${reason.message(language)}"
                                    } finally {
                                        savingEmail = false
                                    }
                                } else if (active) {
                                    if (stack.substringAfterLast('|') == ProfileDestination.Edit.name) {
                                        holder.removeState(ProfileDestination.Edit.name)
                                        back()
                                    }
                                    message = profileSaved
                                }
                            }
                        }
                    }
                    ProfileDestination.Goals -> FitnessGoalsScreen(
                        onBack = { if (!savingGoals) back() },
                        initialTarget = sharedPreferences?.weeklyTarget?.takeIf { it in 1..7 } ?: previewTarget,
                        initialGoals = sharedPreferences?.fitnessGoals?.takeIf { sharedPreferences.saveFitnessGoals != null }
                            ?: FitnessGoalPreferences(previewGoals[0], previewGoals[1], previewGoals[2]),
                        saving = savingGoals,
                        saveFailed = goalsFailed,
                        onSave = { target, goals ->
                            val save = sharedPreferences?.saveFitnessGoals
                            if (save == null) {
                                previewTarget = target
                                previewGoals = arrayListOf(goals.goal, goals.weight, goals.activity)
                                sharedPreferences?.setWeeklyTarget?.invoke(target)
                                message = savedGoals
                            } else if (!savingGoals) {
                                savingGoals = true
                                scope.launch {
                                    try {
                                        goalsFailed = !save(target, goals)
                                        if (!goalsFailed && active) message = if (language == "vi")
                                            "Đã lưu mục tiêu trên thiết bị." else "Goals saved on this device."
                                    } finally { savingGoals = false }
                                }
                            }
                        },
                    )
                    ProfileDestination.Notifications -> NotificationSettingsScreen(back, account?.uid)
                    ProfileDestination.Settings -> SettingsScreen(dark, language, back, navigate) { showDeletionHelp = true }
                    ProfileDestination.Password -> ChangePasswordScreen(back, onMessage = { message = it },
                        viewModel = viewModel<ChangePasswordViewModel>(key = "profile-password-${account?.uid.orEmpty()}"))
                    ProfileDestination.Appearance -> ThemeLanguageScreen(dark, language, back) { nextDark, nextLanguage ->
                        if (sharedPreferences != null) sharedPreferences.setAppearance(nextDark, nextLanguage)
                        else { standaloneDark = nextDark; standaloneLanguage = nextLanguage }
                        back()
                        message = if (nextLanguage == "vi") "Đã áp dụng giao diện và ngôn ngữ." else "Appearance and language applied."
                    }
                    ProfileDestination.Privacy -> PrivacyScreen(back)
                    ProfileDestination.Help -> HelpFaqScreen(back) {
                        if (stack.substringBeforeLast('|').substringAfterLast('|') == ProfileDestination.Contact.name) back()
                        else navigate(ProfileDestination.Contact)
                    }
                    ProfileDestination.Contact -> ContactSupportScreen(back, {
                        if (stack.substringBeforeLast('|').substringAfterLast('|') == ProfileDestination.Help.name) back()
                        else navigate(ProfileDestination.Help)
                    }, onMessage = { message = it })
                }
            }
        }
        if (showDeletionHelp) {
            AlertDialog(
                onDismissRequest = { showDeletionHelp = false },
                title = { Text(profileText("Trợ giúp xóa tài khoản", "Account deletion help")) },
                text = { Text(profileText("Chưa thể xóa tài khoản trong ứng dụng. Liên hệ hỗ trợ để hỏi về quy trình.",
                    "Account deletion is unavailable in the app. Contact support to ask about the process.")) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeletionHelp = false
                        navigate(ProfileDestination.Contact)
                    }, modifier = Modifier.testTag("profile_deletion_contact")) { Text(profileText("Liên hệ", "Contact support")) }
                },
                dismissButton = { TextButton(onClick = { showDeletionHelp = false }) { Text(profileText("Đóng", "Close")) } },
            )
        }
        if (confirmLogout && onLogout != null) {
            AlertDialog(
                onDismissRequest = { confirmLogout = false },
                title = { Text(profileText("Đăng xuất khỏi KgyMs?", "Log out of KgyMs?")) },
                text = { Text(profileText("Bạn có thể đăng nhập lại bất cứ lúc nào.", "You can sign in again at any time.")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmLogout = false
                            onLogout()
                        },
                        modifier = Modifier.testTag("profile_confirm"),
                    ) { Text(profileText("Đăng xuất", "Log Out")) }
                },
                dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(profileText("Hủy", "Cancel")) } },
            )
        }
        message?.let { text ->
            AlertDialog(
                onDismissRequest = { message = null },
                title = { Text("KgyMs") },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { message = null }, modifier = Modifier.testTag("profile_message_ok")) { Text("OK") } },
            )
        }
    }
}
