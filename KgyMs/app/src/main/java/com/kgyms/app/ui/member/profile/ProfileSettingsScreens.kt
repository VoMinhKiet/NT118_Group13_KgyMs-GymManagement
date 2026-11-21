package com.kgyms.app.ui.member.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.data.local.ProfileDevicePreferences
import com.kgyms.app.data.repository.LanguageRepository
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(dark: Boolean, language: String, onBack: () -> Unit, onNavigate: (ProfileDestination) -> Unit, onDeleteHelp: () -> Unit) {
    ProfilePage(profileText("Cài đặt", "Settings"), onBack) {
        ProfileSettingRow(profileText("Giao diện", "Appearance"), if (dark) profileText("Tối", "Dark") else profileText("Sáng", "Light")) { onNavigate(ProfileDestination.Appearance) }
        ProfileSettingRow(profileText("Ngôn ngữ", "Language"), if (language == "vi") "Tiếng Việt" else "English") { onNavigate(ProfileDestination.Appearance) }
        ProfileSettingRow(profileText("Thông báo", "Notifications")) { onNavigate(ProfileDestination.Notifications) }
        ProfileSettingRow(profileText("Mật khẩu", "Password")) { onNavigate(ProfileDestination.Password) }
        ProfileSettingRow(profileText("Quyền riêng tư", "Privacy")) { onNavigate(ProfileDestination.Privacy) }
        ProfileSettingRow(profileText("Thẻ hội viên", "Membership cards")) { onNavigate(ProfileDestination.MembershipCards) }
        ProfileSettingRow(profileText("Trợ giúp xóa tài khoản", "Account deletion help")) { onDeleteHelp() }
    }
}

@Composable
internal fun NotificationSettingsScreen(onBack: () -> Unit, uid: String?) {
    val context = LocalContext.current
    val storage = remember(context, uid) {
        uid?.let { ProfileDevicePreferences(context.getSharedPreferences(LanguageRepository.PREFS_NAME, 0), it) }
    }
    ProfilePage(profileText("Thông báo", "Notification Settings"), onBack) {
        ProfileHeading(profileText("Tùy chọn thông báo", "Notification preferences"), profileText(
            "Thông báo phòng tập và nhắc nhở được lọc trong hộp thư Trang chủ trên thiết bị này. Thông báo giao dịch vẫn hiển thị. Chưa có thông báo ngoài ứng dụng.",
            "Gym notices and reminders are filtered in the Home inbox on this device. Transaction notices stay visible. System notifications are unavailable."))
        NotificationToggle(profileText("Tin phòng tập và ưu đãi", "Gym notices and offers"), "general", true, storage)
        NotificationToggle(profileText("Nhắc nhở", "Reminders"), "reminders", true, storage)
        Text(profileText("Các tùy chọn hệ thống bên dưới chỉ được lưu trên thiết bị.", "The system preferences below are stored on this device only."),
            color = LocalProfilePalette.current.muted)
        NotificationToggle(profileText("Âm thanh", "Sound"), "sound", false, storage)
        NotificationToggle(profileText("Không làm phiền", "Do Not Disturb"), "dnd", true, storage)
        NotificationToggle(profileText("Rung", "Vibration"), "vibration", true, storage)
        NotificationToggle(profileText("Màn hình khóa", "Lock Screen Notifications"), "lock", true, storage)
    }
}

@Composable
private fun NotificationToggle(title: String, key: String, default: Boolean, storage: ProfileDevicePreferences?) {
    var selected by rememberSaveable(storage) { mutableStateOf(storage?.notification(key, default) ?: default) }
    var saving by remember(storage) { mutableStateOf(false) }
    var failed by remember(storage) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ProfileCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title)
                Text(
                    when {
                        saving -> profileText("Đang lưu…", "Saving…")
                        failed -> profileText("Chưa lưu được. Hãy thử lại.", "Could not save. Please retry.")
                        selected -> profileText("Tùy chọn bật", "Preference on")
                        else -> profileText("Tùy chọn tắt", "Preference off")
                    },
                    color = if (failed) LocalProfilePalette.current.danger else LocalProfilePalette.current.muted,
                )
            }
            Switch(checked = selected, enabled = !saving, onCheckedChange = { next ->
                if (storage == null) selected = next else {
                    saving = true
                    scope.launch {
                        try {
                            failed = !storage.saveNotification(key, next)
                            if (!failed) selected = next
                        } finally { saving = false }
                    }
                }
            }, modifier = Modifier.testTag("notification_$key").semantics { contentDescription = title })
        }
    }
}

@Composable
internal fun ThemeLanguageScreen(dark: Boolean, language: String, onBack: () -> Unit, onSave: (Boolean, String) -> Unit) {
    var draftDark by rememberSaveable(dark) { mutableStateOf(dark) }
    var draftLanguage by rememberSaveable(language) { mutableStateOf(language) }
    ProfilePage(profileText("Giao diện & ngôn ngữ", "Theme & Language"), onBack) {
        ProfileHeading(profileText("Cá nhân hóa", "Personalize"), profileText("Xem trước giao diện cho toàn bộ Member.", "Preview the appearance for all Member screens."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileChoice(profileText("Tối", "Dark"), draftDark, { draftDark = true }, Modifier.weight(1f).testTag("theme_dark"))
            ProfileChoice(profileText("Sáng", "Light"), !draftDark, { draftDark = false }, Modifier.weight(1f).testTag("theme_light"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileChoice(profileText("Tiếng Việt", "Vietnamese"), draftLanguage == "vi", { draftLanguage = "vi" }, Modifier.weight(1f).testTag("language_vi"))
            ProfileChoice(profileText("Tiếng Anh", "English"), draftLanguage == "en", { draftLanguage = "en" }, Modifier.weight(1f).testTag("language_en"))
        }
        ProfileAppearance(draftDark, draftLanguage) {
            ProfileCard {
                Text(profileText("XEM TRƯỚC", "PREVIEW"), color = LocalProfilePalette.current.muted)
                ProfileHeading(if (draftDark) profileText("Tối", "Dark") else profileText("Sáng", "Light"))
                Text(if (draftDark) profileText("Xám đậm • Tím", "Dark gray • Purple") else profileText("Xám nhạt • Trắng • Tím", "Light gray • White • Purple"))
                Text(profileText("Hồ sơ của bạn", "Your profile"))
                Surface(
                    color = LocalProfilePalette.current.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(profileText("Màu nhấn", "Accent color"), modifier = Modifier.padding(16.dp))
                }
            }
        }
        ProfilePrimaryButton(profileText("Lưu thay đổi", "Save Changes")) { onSave(draftDark, draftLanguage) }
    }
}

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf("") }
    val sections = listOf(
        "policy" to profileText("Chính sách quyền riêng tư", "Privacy Policy"),
        "data" to profileText("Dữ liệu & quyền truy cập", "Data & Permissions"),
        "account" to profileText("Quyền riêng tư tài khoản", "Account Privacy"),
    )
    val details = mapOf(
        "policy" to profileText("Chưa có liên kết đến chính sách chính thức trong ứng dụng.", "An official privacy policy link is unavailable in the app."),
        "data" to profileText("Thông tin cá nhân lưu trong hồ sơ Firebase của tài khoản. Ảnh đại diện, mục tiêu và tùy chọn thông báo lưu trên thiết bị.",
            "Personal details are stored in your Firebase account profile. Your photo, goals and notification preferences are stored on this device."),
        "account" to profileText("Firebase Auth quản lý email đăng nhập và mật khẩu. Email mới chỉ có hiệu lực sau khi xác nhận.",
            "Firebase Auth manages sign-in email and password. Changes to your email take effect after verification."),
    )
    ProfilePage(profileText("Quyền riêng tư", "Privacy"), onBack) {
        sections.forEach { (key, title) ->
            ProfileSettingRow(title) { expanded = if (expanded == key) "" else key }
            if (expanded == key) ProfileCard { Text(details.getValue(key)) }
        }
    }
}
