package com.kgyms.app.ui.member.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import com.kgyms.app.ui.member.LocalMemberAvatar
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import com.kgyms.app.data.model.ProfileUiData
import com.kgyms.app.data.local.FitnessGoalPreferences
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.repository.AccountType
import com.kgyms.app.viewmodel.profile.ChangePasswordEvent
import com.kgyms.app.viewmodel.profile.ChangePasswordViewModel

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
    avatarEditingEnabled: Boolean = true,
    saving: Boolean = false,
    saveFailed: Boolean = false,
    offline: Boolean = false,
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
    val phoneValid = (profile.isRealAccount && number.isBlank()) || (number.filter(Char::isDigit).length in 9..15 &&
        number.all { it.isDigit() || it in " +()-" })
    val birthdayValid = (profile.isRealAccount && birth.isBlank()) || validProfileBirthday(birth)
    val avatar = LocalMemberAvatar.current
    val busy = avatar?.busy == true || saving
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAvatarSelected(uri.toString())
    }
    val pickAvatar = { if (!busy) avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
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
                editable = avatarEditingEnabled,
                onClick = if (busy || !avatarEditingEnabled) null else pickAvatar,
            )
            if (avatarEditingEnabled) {
                TextButton(onClick = pickAvatar, enabled = !busy) {
                    Text(profileText("Đổi ảnh đại diện", "Change profile photo"))
                }
                if (avatar?.hasAvatar == true || avatarUri != null) {
                    TextButton(onClick = { onAvatarSelected(null) }, enabled = !busy) {
                        Text(profileText("Dùng lại chữ viết tắt", "Use initials instead"))
                    }
                }
            } else {
                Text(profileText("Ảnh đại diện đang dùng chữ viết tắt của bạn.", "Your initials are used as your profile picture."), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(profileText("Ảnh đại diện lưu trên thiết bị này.", "Your photo is stored on this device."),
            style = MaterialTheme.typography.bodySmall, color = LocalProfilePalette.current.muted)
        if (avatar?.busy == true) Text(profileText("Đang xử lý ảnh…", "Processing photo…"), style = MaterialTheme.typography.bodySmall)
        if (avatar?.failed == true) Text(
            profileText("Không thể cập nhật ảnh. Vui lòng chọn lại.", "Could not update photo. Please try again."),
            color = LocalProfilePalette.current.danger,
        )
        ProfileField(profileText("Họ và tên", "Full name"), name, { name = it }, "profile_name",
            error = if (submitted && name.isBlank()) required else null, readOnly = saving)
        ProfileField("Email", email, { email = it }, "profile_email", KeyboardType.Email,
            error = if (submitted && !emailValid) emailError else null, readOnly = saving)
        if (profile.isRealAccount) Text(profileText("Đổi email cần xác nhận qua email mới và đăng nhập lại.",
            "Email changes require verification at the new address and signing in again."), style = MaterialTheme.typography.bodySmall)
        ProfileField(profileText("Số điện thoại", "Phone number"), number, { number = it }, "profile_phone", KeyboardType.Phone,
            error = if (submitted && !phoneValid) phoneError else null, readOnly = saving)
        ProfileField(profileText("Ngày sinh", "Date of birth"), birth, { birth = it }, "profile_birthday",
            error = if (submitted && !birthdayValid) birthError else null, readOnly = saving)
        if (offline) Text(profileText("Đang ngoại tuyến. Kết nối lại để lưu hồ sơ.",
            "You’re offline. Reconnect to save your profile."), color = LocalProfilePalette.current.danger)
        else if (saveFailed) Text(profileText("Không thể lưu hoặc tải lại hồ sơ. Kiểm tra kết nối/quyền truy cập rồi thử lại.",
            "Could not save or reload profile. Check connection/permissions and retry."), color = LocalProfilePalette.current.danger)
        ProfilePrimaryButton(profileText(if (saving) "Đang lưu…" else "Cập nhật hồ sơ", if (saving) "Saving…" else "Update Profile"), enabled = !busy && !offline) {
            submitted = true
            if (!busy && name.isNotBlank() && emailValid && phoneValid && birthdayValid) {
                onSave(name.trim(), email.trim(), number.trim(), birth)
            }
        }
    }
}

@Composable
internal fun FitnessGoalsScreen(
    onBack: () -> Unit,
    initialTarget: Int,
    initialGoals: FitnessGoalPreferences,
    saving: Boolean,
    saveFailed: Boolean,
    onSave: (Int, FitnessGoalPreferences) -> Unit,
) {
    var sessions by rememberSaveable(initialTarget) { mutableStateOf(initialTarget) }
    var goal by rememberSaveable(initialGoals.goal) { mutableStateOf(initialGoals.goal) }
    var weight by rememberSaveable(initialGoals.weight) { mutableStateOf(initialGoals.weight) }
    var activity by rememberSaveable(initialGoals.activity) { mutableStateOf(initialGoals.activity) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val validWeight = weight.isBlank() || weight.trim().replace(',', '.').toDoubleOrNull()?.let { it in 20.0..300.0 } == true
    ProfilePage(profileText("Mục tiêu tập luyện", "Fitness Goals"), onBack) {
        ProfileHeading(profileText("Mục tiêu cá nhân", "Personal goals"), profileText(
            "Số buổi tập cập nhật tiến độ. Các mục tiêu khác lưu trên thiết bị để bạn theo dõi.",
            "Weekly workouts update your progress. Other goals are stored on this device for reference."))
        ProfileCard {
            Text(profileText("Số buổi tập mỗi tuần", "Workouts per week"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { if (sessions > 1) sessions-- }, enabled = sessions > 1, modifier = Modifier.testTag("goal_decrease")) { Text("−") }
                Text("$sessions", Modifier.padding(12.dp).testTag("goal_sessions"), fontSize = 22.sp)
                OutlinedButton(onClick = { if (sessions < 7) sessions++ }, enabled = sessions < 7, modifier = Modifier.testTag("goal_increase")) { Text("+") }
            }
        }
        Text(profileText("Mục tiêu thể hình", "Fitness goal"))
        listOf("strength" to profileText("Tăng cơ", "Build muscle"), "weight" to profileText("Giảm mỡ", "Lose fat"),
            "health" to profileText("Sức khỏe tổng thể", "General fitness")).forEach { (key, label) ->
            ProfileChoice(label, goal == key, { goal = key }, Modifier.fillMaxWidth())
        }
        ProfileField(profileText("Cân nặng mục tiêu (kg, tùy chọn)", "Target weight (kg, optional)"), weight, { weight = it }, "goal_weight", KeyboardType.Decimal,
            error = if (submitted && !validWeight) profileText("Nhập cân nặng từ 20 đến 300 kg.", "Enter a weight between 20 and 300 kg.") else null)
        Text(profileText("Mức độ hoạt động", "Activity level"))
        listOf("low" to profileText("Nhẹ", "Low"), "moderate" to profileText("Vừa", "Moderate"),
            "high" to profileText("Cao", "High")).forEach { (key, label) ->
            ProfileChoice(label, activity == key, { activity = key }, Modifier.fillMaxWidth())
        }
        if (saveFailed) Text(profileText("Không lưu được mục tiêu. Vui lòng thử lại.", "Could not save your goals. Please retry."),
            color = LocalProfilePalette.current.danger)
        ProfilePrimaryButton(profileText(if (saving) "Đang lưu…" else "Lưu mục tiêu", if (saving) "Saving…" else "Save Goals"), enabled = !saving) {
            submitted = true
            if (validWeight) onSave(sessions, FitnessGoalPreferences(goal, weight.trim(), activity))
        }
    }
}

@Composable
internal fun ChangePasswordScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ChangePasswordViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val changed = profileText("Đã đổi mật khẩu thành công.", "Your password has been changed.")
    val noBrowser = profileText("Không tìm thấy trình duyệt để mở trang của Google.", "No browser found to open the Google page.")
    val language = LocalProfileLanguage.current
    val vietnamese = language == "vi"
    var clearForm by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            ChangePasswordEvent.PasswordChanged -> {
                clearForm++
                onMessage(changed)
            }
            is ChangePasswordEvent.ResetEmailSent -> onMessage(
                if (vietnamese) "Đã gửi email đặt lại mật khẩu tới ${event.email}. Vui lòng kiểm tra hộp thư (kể cả mục Spam)."
                else "A password reset email was sent to ${event.email}. Please check your inbox (and Spam)."
            )
            is ChangePasswordEvent.OpenUrl -> try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
            } catch (_: ActivityNotFoundException) {
                onMessage(noBrowser)
            }
            is ChangePasswordEvent.Error -> onMessage(event.error.message(language))
            null -> return@LaunchedEffect
        }
        viewModel.consumeEvent()
    }

    if (uiState.accountType == AccountType.Google) {
        GooglePasswordContent(
            email = uiState.email,
            onBack = onBack,
            onChangePassword = viewModel::onChangeGooglePasswordClick,
            onForgotPassword = viewModel::onForgotPasswordClick,
        )
    } else {
        key(clearForm) {
            EmailPasswordContent(
                loading = uiState.isLoading,
                onBack = onBack,
                onForgotPassword = viewModel::onForgotPasswordClick,
                onSubmit = viewModel::changePassword,
            )
        }
    }

    if (uiState.showResetConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResetConfirm,
            title = { Text(profileText("Quên mật khẩu?", "Forgot password?")) },
            text = {
                Text(
                    if (uiState.email.isNotBlank()) profileText(
                        "Chúng tôi sẽ gửi liên kết đặt lại mật khẩu tới ${uiState.email}.",
                        "We will send a password reset link to ${uiState.email}.",
                    ) else profileText("Gửi liên kết đặt lại mật khẩu tới email của bạn?", "Send a password reset link to your email?")
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSendResetEmail, modifier = Modifier.testTag("password_reset_confirm")) {
                    Text(profileText("Gửi email", "Send email"))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissResetConfirm) { Text(profileText("Hủy", "Cancel")) } },
        )
    }
}

/** Tài khoản đăng nhập bằng Google: mật khẩu do Google quản lý. */
@Composable
private fun GooglePasswordContent(
    email: String,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    ProfilePage(profileText("Đổi mật khẩu", "Change Password"), onBack) {
        ProfileHeading(
            profileText("Tài khoản Google", "Google account"),
            profileText(
                "Bạn đăng nhập bằng Google${if (email.isNotBlank()) " ($email)" else ""}. Mật khẩu được quản lý bởi Google, hãy đổi hoặc khôi phục trên trang của Google.",
                "You signed in with Google${if (email.isNotBlank()) " ($email)" else ""}. Your password is managed by Google; change or recover it on Google's page.",
            ),
        )
        ProfilePrimaryButton(profileText("Đổi mật khẩu Google", "Change Google password"), tag = "google_change_password", onClick = onChangePassword)
        TextButton(onClick = onForgotPassword, modifier = Modifier.testTag("google_forgot_password")) {
            Text(profileText("Quên mật khẩu Google?", "Forgot Google password?"))
        }
    }
}

/** Tài khoản email/password (hoặc chế độ thử nghiệm khi chưa đăng nhập). */
@Composable
private fun EmailPasswordContent(
    loading: Boolean,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showCurrent by rememberSaveable { mutableStateOf(false) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val required = profileText("Nhập mật khẩu hiện tại.", "Enter your current password.")
    val lengthError = profileText("Mật khẩu mới cần ít nhất 6 ký tự.", "Use at least 6 characters.")
    val matchError = profileText("Hai mật khẩu mới chưa khớp.", "The new passwords do not match.")
    val sameError = profileText("Mật khẩu mới phải khác mật khẩu hiện tại.", "Choose a different new password.")
    ProfilePage(profileText("Đổi mật khẩu", "Change Password"), onBack) {
        ProfileField(profileText("Mật khẩu hiện tại", "Current password"), current, { current = it }, "password_current", KeyboardType.Password,
            error = if (submitted && current.isBlank()) required else null,
            password = true, visible = showCurrent, onVisibility = { showCurrent = !showCurrent })
        TextButton(onClick = onForgotPassword, enabled = !loading, modifier = Modifier.testTag("password_forgot")) {
            Text(profileText("Quên mật khẩu?", "Forgot password?"))
        }
        ProfileField(profileText("Mật khẩu mới", "New password"), password, { password = it }, "password_new", KeyboardType.Password,
            error = if (submitted && password.length < 6) lengthError else if (submitted && password == current) sameError else null,
            password = true, visible = showNew, onVisibility = { showNew = !showNew })
        ProfileField(profileText("Xác nhận mật khẩu", "Confirm new password"), confirm, { confirm = it }, "password_confirm", KeyboardType.Password,
            error = if (submitted && (confirm != password || confirm.isEmpty())) matchError else null,
            password = true, visible = showConfirm, onVisibility = { showConfirm = !showConfirm })
        if (loading) Text(profileText("Đang xử lý…", "Processing…"), style = MaterialTheme.typography.bodySmall)
        ProfilePrimaryButton(profileText("Đổi mật khẩu", "Change Password")) {
            submitted = true
            if (!loading && current.isNotBlank() && password.length >= 6 && password != current && confirm == password) {
                onSubmit(current, password)
            }
        }
    }
}
