package com.kgyms.app.ui.member.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.viewmodel.profile.ContactSupportViewModel

private data class FaqEntry(val key: String, val category: String, val question: String, val answer: String)

@Composable
internal fun HelpFaqScreen(onBack: () -> Unit, onContact: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    val entries = listOf(
        FaqEntry("account", profileText("Tài khoản & bảo mật", "Account & Security"),
            profileText("Làm sao đổi thông tin cá nhân?", "How do I edit my personal information?"),
            profileText("Vào Hồ sơ → Thông tin cá nhân để lưu vào tài khoản. Email mới cần xác nhận.", "Open Profile → Personal Information to update your account. A new email address requires verification.")),
        FaqEntry("membership", profileText("Gói tập & thanh toán", "Membership & Payments"),
            profileText("Xem gói tập hiện tại ở đâu?", "Where can I view my membership?"),
            profileText("Chọn Gói tập & hội viên trong Hồ sơ để xem gói và trạng thái hiện tại.", "Open Packages & Membership in Profile to see your plans and their current status.")),
        FaqEntry("workout", profileText("Tập luyện & HLV", "Workout & PT"), profileText("Điều chỉnh mục tiêu tập ở đâu?", "How do I adjust my fitness goals?"),
            profileText("Chọn Mục tiêu tập luyện để điều chỉnh số buổi tập và lưu mục tiêu cá nhân.", "Open Fitness Goals to adjust weekly workouts and save your personal goals.")),
        FaqEntry("nutrition", profileText("Dinh dưỡng", "Nutrition"), profileText("Có kế hoạch dinh dưỡng chưa?", "Is a nutrition plan available?"),
            profileText("Mở Dinh dưỡng từ Trang chủ hoặc tab Kế hoạch để xem thực đơn.", "Open Nutrition from Home or the Plan tab to view your meal plan.")),
        FaqEntry("checkin", profileText("Điểm danh", "Check-in"), profileText("Mở điểm danh ở đâu?", "Where do I open check-in?"),
            profileText("Mở tab QR và chọn Điểm danh.", "Open the QR tab and select Check-in.")),
    )
    val filtered = entries.filter { (it.category + " " + it.question + " " + it.answer).contains(search.trim(), ignoreCase = true) }
    ProfilePage(profileText("Trợ giúp & câu hỏi", "Help & FAQ"), onBack) {
        ProfileHeading(profileText("Chúng tôi có thể giúp gì?", "How can we help?"))
        SupportTabs(true, onContact)
        ProfileField(profileText("Tìm kiếm", "Search"), search, { search = it }, "faq_search")
        filtered.forEach { entry ->
            ProfileSettingRow(entry.category, profileText("Xem câu hỏi thường gặp", "View frequently asked questions")) {
                expanded = if (expanded == entry.key) "" else entry.key
            }
            if (expanded == entry.key) ProfileCard {
                Text(entry.question, color = LocalProfilePalette.current.accent)
                Text(entry.answer)
            }
        }
        if (filtered.isEmpty()) Text(profileText("Không tìm thấy câu hỏi phù hợp.", "No matching questions found."), color = LocalProfilePalette.current.muted)
    }
}

@Composable
internal fun ContactSupportScreen(
    onBack: () -> Unit,
    onFaq: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ContactSupportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val noDialer = profileText("Thiết bị không có ứng dụng quay số.", "No dialer is available on this device.")

    LaunchedEffect(uiState.pendingCall) {
        val phone = uiState.pendingCall ?: return@LaunchedEffect
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
        } catch (_: ActivityNotFoundException) {
            onMessage(noDialer)
        } catch (_: SecurityException) {
            onMessage(noDialer)
        } finally {
            viewModel.onCallHandled()
        }
    }

    ProfilePage(profileText("Liên hệ hỗ trợ", "Contact Support"), onBack) {
        SupportTabs(false, onFaq)
        ProfileSettingRow(profileText("Hotline CSKH", "Customer Service Hotline"), uiState.hotlinePhone) { viewModel.onHotlineClick() }
        Text(profileText("Website và mạng xã hội chưa có địa chỉ hỗ trợ được cấu hình.",
            "Support website and social channels have no configured addresses."), color = LocalProfilePalette.current.muted)
    }

    if (uiState.showCallConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCallConfirm,
            title = { Text(profileText("Mở trình quay số?", "Open the dialer?")) },
            text = { Text(profileText("Số ${uiState.hotlinePhone} sẽ được điền sẵn.", "The dialer will prefill ${uiState.hotlinePhone}.")) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCall, modifier = Modifier.testTag("hotline_call_confirm")) {
                    Text(profileText("Có", "Yes"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCallConfirm) { Text(profileText("Không", "No")) }
            },
        )
    }
}

@Composable
private fun SupportTabs(faqSelected: Boolean, onOtherTab: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val faq = profileText("Câu hỏi", "FAQ")
        val contact = profileText("Liên hệ", "Contact")
        if (faqSelected) {
            Text(faq, Modifier.weight(1f).padding(12.dp), color = LocalProfilePalette.current.accent)
            ProfileChoice(contact, false, onOtherTab, Modifier.weight(1f))
        } else {
            ProfileChoice(faq, false, onOtherTab, Modifier.weight(1f))
            Text(contact, Modifier.weight(1f).padding(12.dp), color = LocalProfilePalette.current.accent)
        }
    }
}
