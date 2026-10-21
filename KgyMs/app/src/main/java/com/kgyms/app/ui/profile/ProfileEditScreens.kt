package com.kgyms.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal fun validProfileBirthday(value: String): Boolean {
    if (!Regex("\\d{2}/\\d{2}/\\d{4}").matches(value)) return false
    val parser = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    return parser.parse(value, position) != null && position.index == value.length
}

@Composable
internal fun EditProfileScreen(
    profile: ProfileUiData,
    phone: String,
    birthday: String,
    avatarUri: String?,
    onBack: () -> Unit,
    onAvatarSelected: (String?) -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var email by rememberSaveable { mutableStateOf(profile.email) }
    var number by rememberSaveable { mutableStateOf(phone) }
    var birth by rememberSaveable { mutableStateOf(birthday) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val required = profileText("Không được để trống.", "This field is required.")
    val emailError = profileText("Nhập email hợp lệ.", "Enter a valid email.")
    val phoneError = profileText("Nhập số điện thoại hợp lệ.", "Enter a valid phone number.")
    val birthError = profileText("Dùng ngày hợp lệ theo dd/MM/yyyy.", "Use a valid date in dd/MM/yyyy format.")
    val emailValid = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    val phoneValid = number.filter(Char::isDigit).length in 9..15 &&
        number.all { it.isDigit() || it in " +()-" }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAvatarSelected(uri.toString())
    }
    ProfilePage(profileText("Chỉnh sửa hồ sơ", "Edit Profile"), onBack) {
        ProfileHeading(profileText("Thông tin cá nhân", "Personal Information"), profileText("Cập nhật khi cần.", "Update your details when needed."))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileAvatar(
                avatarUri = avatarUri,
                initials = profile.initials,
                modifier = Modifier.size(104.dp),
                editable = true,
                onClick = { avatarPicker.launch("image/*") },
            )
            TextButton(onClick = { avatarPicker.launch("image/*") }) {
                Text(profileText("Đổi ảnh đại diện", "Change profile photo"))
            }
            if (avatarUri != null) {
                TextButton(onClick = { onAvatarSelected(null) }) {
                    Text(profileText("Dùng lại chữ viết tắt", "Use initials instead"))
                }
            }
        }
        ProfileField(profileText("Họ và tên", "Full name"), name, { name = it }, "profile_name",
            error = if (submitted && name.isBlank()) required else null)
        ProfileField("Email", email, { email = it }, "profile_email", KeyboardType.Email,
            error = if (submitted && !emailValid) emailError else null)
        ProfileField(profileText("Số điện thoại", "Phone number"), number, { number = it }, "profile_phone", KeyboardType.Phone,
            error = if (submitted && !phoneValid) phoneError else null)
        ProfileField(profileText("Ngày sinh", "Date of birth"), birth, { birth = it }, "profile_birthday",
            error = if (submitted && !validProfileBirthday(birth)) birthError else null)
        ProfilePrimaryButton(profileText("Cập nhật hồ sơ", "Update Profile")) {
            submitted = true
            if (name.isNotBlank() && emailValid && phoneValid && validProfileBirthday(birth)) {
                onSave(name.trim(), email.trim(), number.trim(), birth)
            }
        }
    }
}

@Composable
internal fun FitnessGoalsScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    var sessions by rememberSaveable { mutableStateOf(3) }
    var goal by rememberSaveable { mutableStateOf("strength") }
    var weight by rememberSaveable { mutableStateOf("65") }
    var activity by rememberSaveable { mutableStateOf("moderate") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val validWeight = weight.replace(',', '.').toDoubleOrNull()?.let { it in 20.0..300.0 } == true
    ProfilePage(profileText("Mục tiêu tập luyện", "Fitness Goals"), onBack) {
        ProfileHeading(profileText("Kế hoạch của bạn", "Your plan"), profileText("Điều chỉnh mục tiêu phù hợp với bạn.", "Choose goals that work for you."))
        ProfileCard {
            Text(profileText("Số buổi tập mỗi tuần", "Workouts per week"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { if (sessions > 1) sessions-- }, enabled = sessions > 1) { Text("−") }
                Text("$sessions", Modifier.padding(12.dp), fontSize = 22.sp)
                OutlinedButton(onClick = { if (sessions < 7) sessions++ }, enabled = sessions < 7) { Text("+") }
            }
        }
        Text(profileText("Mục tiêu thể hình", "Fitness goal"))
        listOf("strength" to profileText("Tăng cơ", "Build muscle"), "weight" to profileText("Giảm mỡ", "Lose fat"),
            "health" to profileText("Sức khỏe tổng thể", "General fitness")).forEach { (key, label) ->
            ProfileChoice(label, goal == key, { goal = key }, Modifier.fillMaxWidth())
        }
        ProfileField(profileText("Cân nặng mục tiêu (kg)", "Target weight (kg)"), weight, { weight = it }, "goal_weight", KeyboardType.Decimal,
            error = if (submitted && !validWeight) profileText("Nhập cân nặng từ 20 đến 300 kg.", "Enter a weight between 20 and 300 kg.") else null)
        Text(profileText("Mức độ hoạt động", "Activity level"))
        listOf("low" to profileText("Nhẹ", "Low"), "moderate" to profileText("Vừa", "Moderate"),
            "high" to profileText("Cao", "High")).forEach { (key, label) ->
            ProfileChoice(label, activity == key, { activity = key }, Modifier.fillMaxWidth())
        }
        ProfilePrimaryButton(profileText("Lưu mục tiêu", "Save Goals")) {
            submitted = true
            if (validWeight) onSaved()
        }
    }
}

@Composable
internal fun ChangePasswordScreen(onBack: () -> Unit, onMessage: (String) -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showCurrent by rememberSaveable { mutableStateOf(false) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val required = profileText("Nhập mật khẩu hiện tại.", "Enter your current password.")
    val lengthError = profileText("Mật khẩu mới cần ít nhất 8 ký tự.", "Use at least 8 characters.")
    val matchError = profileText("Hai mật khẩu mới chưa khớp.", "The new passwords do not match.")
    val sameError = profileText("Mật khẩu mới phải khác mật khẩu hiện tại.", "Choose a different new password.")
    val success = profileText("Đã mô phỏng đổi mật khẩu. Mật khẩu tài khoản thật không thay đổi.", "Password change simulated. Your real password is unchanged.")
    val forgot = profileText("Mô phỏng khôi phục mật khẩu. Chưa gửi email hoặc mã xác nhận.", "Password recovery preview. No email or code has been sent.")
    ProfilePage(profileText("Đổi mật khẩu", "Change Password"), onBack) {
        ProfileField(profileText("Mật khẩu hiện tại", "Current password"), current, { current = it }, "password_current", KeyboardType.Password,
            error = if (submitted && current.isBlank()) required else null,
            password = true, visible = showCurrent, onVisibility = { showCurrent = !showCurrent })
        TextButton(onClick = { onMessage(forgot) }) { Text(profileText("Quên mật khẩu?", "Forgot password?")) }
        ProfileField(profileText("Mật khẩu mới", "New password"), password, { password = it }, "password_new", KeyboardType.Password,
            error = if (submitted && password.length < 8) lengthError else if (submitted && password == current) sameError else null,
            password = true, visible = showNew, onVisibility = { showNew = !showNew })
        ProfileField(profileText("Xác nhận mật khẩu", "Confirm new password"), confirm, { confirm = it }, "password_confirm", KeyboardType.Password,
            error = if (submitted && (confirm != password || confirm.isEmpty())) matchError else null,
            password = true, visible = showConfirm, onVisibility = { showConfirm = !showConfirm })
        ProfilePrimaryButton(profileText("Đổi mật khẩu", "Change Password")) {
            submitted = true
            if (current.isNotBlank() && password.length >= 8 && password != current && confirm == password) {
                current = ""; password = ""; confirm = ""
                submitted = false
                onMessage(success)
            }
        }
    }
}
