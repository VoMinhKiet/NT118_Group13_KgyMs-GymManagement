package com.kgyms.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(dark: Boolean, language: String, onBack: () -> Unit, onNavigate: (ProfileDestination) -> Unit, onDelete: () -> Unit) {
    ProfilePage(profileText("Cài đặt", "Settings"), onBack) {
        ProfileSettingRow(profileText("Giao diện", "Appearance"), if (dark) profileText("Tối cao cấp", "Dark Premium") else profileText("Trắng & hồng cao cấp", "White & Pink Premium")) { onNavigate(ProfileDestination.Appearance) }
        ProfileSettingRow(profileText("Ngôn ngữ", "Language"), if (language == "vi") "Tiếng Việt" else "English") { onNavigate(ProfileDestination.Appearance) }
        ProfileSettingRow(profileText("Thông báo", "Notifications")) { onNavigate(ProfileDestination.Notifications) }
        ProfileSettingRow(profileText("Mật khẩu", "Password")) { onNavigate(ProfileDestination.Password) }
        ProfileSettingRow(profileText("Quyền riêng tư", "Privacy")) { onNavigate(ProfileDestination.Privacy) }
        ProfileSettingRow(profileText("Xóa tài khoản", "Delete Account")) { onDelete() }
    }
}

@Composable
internal fun NotificationSettingsScreen(onBack: () -> Unit) {
    var general by rememberSaveable { mutableStateOf(true) }
    var sound by rememberSaveable { mutableStateOf(false) }
    var dnd by rememberSaveable { mutableStateOf(true) }
    var vibration by rememberSaveable { mutableStateOf(true) }
    var lockScreen by rememberSaveable { mutableStateOf(true) }
    var reminders by rememberSaveable { mutableStateOf(true) }
    val enabled = profileText("Đang bật", "On")
    val disabled = profileText("Tắt", "Off")
    ProfilePage(profileText("Thông báo", "Notification Settings"), onBack) {
        ProfileHeading(profileText("Tùy chọn thông báo", "Notification preferences"), profileText("Kiểm soát những gì bạn muốn nhận.", "Control what you want to receive."))
        NotificationToggle(profileText("Thông báo chung", "General Notifications"), if (general) enabled else disabled, general, { general = it }, "notification_general")
        NotificationToggle(profileText("Âm thanh", "Sound"), if (sound) enabled else disabled, sound, { sound = it }, "notification_sound")
        NotificationToggle(profileText("Không làm phiền", "Do Not Disturb"), "22:00 – 07:00", dnd, { dnd = it }, "notification_dnd")
        NotificationToggle(profileText("Rung", "Vibration"), if (vibration) enabled else disabled, vibration, { vibration = it }, "notification_vibration")
        NotificationToggle(profileText("Màn hình khóa", "Lock Screen Notifications"), if (lockScreen) enabled else disabled, lockScreen, { lockScreen = it }, "notification_lock")
        NotificationToggle(profileText("Nhắc nhở", "Reminders"), if (reminders) enabled else disabled, reminders, { reminders = it }, "notification_reminders")
    }
}

@Composable
private fun NotificationToggle(title: String, detail: String, enabled: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    ProfileCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title)
                Text(detail, color = LocalProfilePalette.current.muted)
            }
            Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
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
                ProfileHeading(if (draftDark) profileText("Tối cao cấp", "Dark Premium") else profileText("Trắng & hồng cao cấp", "White & Pink Premium"))
                Text(if (draftDark) profileText("Tím • Tím đậm • Xanh băng", "Violet • Purple • Icy Blue") else profileText("Trắng • Hồng nhạt • Hồng đậm", "White • Pastel Pink • Rose"))
                Text(profileText("Hồ sơ của bạn", "Your profile"))
                Surface(
                    color = LocalProfilePalette.current.accent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
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
        "policy" to profileText("Đây là nội dung minh họa. Chính sách chính thức sẽ được bổ sung khi ứng dụng triển khai.", "This is sample content. The official policy will be provided when the app launches."),
        "data" to profileText("Các màn Profile hiện chỉ dùng dữ liệu mẫu và state cục bộ. Không yêu cầu quyền truy cập hoặc gửi dữ liệu đến máy chủ.", "Profile uses sample data and local state. It requests no permissions and sends no data to a server."),
        "account" to profileText("Thay đổi hồ sơ và tùy chọn chỉ áp dụng cho bản UI thử nghiệm. Chưa kết nối tài khoản thật.", "Profile changes and preferences apply only to this UI demo. No real account is connected."),
    )
    ProfilePage(profileText("Quyền riêng tư", "Privacy"), onBack) {
        sections.forEach { (key, title) ->
            ProfileSettingRow(title) { expanded = if (expanded == key) "" else key }
            if (expanded == key) ProfileCard { Text(details.getValue(key)) }
        }
    }
}

/** No existing Membership screen is present in the current branch. */
@Composable
internal fun ProfileMembershipScreen(profile: ProfileUiData, onBack: () -> Unit) {
    ProfilePage(profileText("Gói hội viên", "My Membership"), onBack) {
        ProfileHeading(if (profile.membership == "Premium Member") profileText("Hội viên Premium", "Premium Member") else profile.membership, profileText("Thông tin gói tập mẫu.", "Sample membership details."))
        ProfileCard {
            Text(profile.name)
            Text(profileText("Trạng thái: Đang hoạt động", "Status: Active"), color = LocalProfilePalette.current.accent)
            Text(profileText("Tập luyện tại phòng gym", "Gym access"))
            Text(profileText("Theo dõi hoạt động và tiến độ", "Activity and progress tracking"))
        }
        Text(profileText("Chưa kết nối hệ thống gói tập hoặc thanh toán.", "Not connected to membership or payment services."), color = LocalProfilePalette.current.muted)
    }
}
