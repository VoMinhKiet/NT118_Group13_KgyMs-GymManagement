package com.kgyms.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class FaqEntry(val key: String, val category: String, val question: String, val answer: String)

@Composable
internal fun HelpFaqScreen(onBack: () -> Unit, onContact: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    val entries = listOf(
        FaqEntry("account", profileText("Tài khoản & bảo mật", "Account & Security"),
            profileText("Làm sao đổi thông tin cá nhân?", "How do I edit my personal information?"),
            profileText("Vào Hồ sơ → Thông tin cá nhân. Trong bản thử nghiệm, cập nhật chỉ thay đổi dữ liệu cục bộ.", "Open Profile → Personal Information. Updates in this demo only change local data.")),
        FaqEntry("membership", profileText("Gói tập & thanh toán", "Membership & Payments"),
            profileText("Xem gói tập hiện tại ở đâu?", "Where can I view my membership?"),
            profileText("Chọn Gói hội viên trong Hồ sơ để xem gói Premium mẫu. Chưa hỗ trợ thanh toán thật.", "Choose My Membership in Profile to view the sample Premium plan. Real payments are not supported yet.")),
        FaqEntry("workout", profileText("Tập luyện & HLV", "Workout & PT"), profileText("Điều chỉnh mục tiêu tập ở đâu?", "How do I adjust my fitness goals?"),
            profileText("Chọn Mục tiêu tập luyện để điều chỉnh số buổi tập, mục tiêu thể hình và cân nặng mẫu.", "Choose Fitness Goals to adjust weekly workouts, fitness goals and sample target weight.")),
        FaqEntry("nutrition", profileText("Dinh dưỡng", "Nutrition"), profileText("Có kế hoạch dinh dưỡng chưa?", "Is a nutrition plan available?"),
            profileText("Phần Hồ sơ hiện chưa kết nối kế hoạch dinh dưỡng. Bạn có thể xem các kênh hỗ trợ ở tab Liên hệ.", "This Profile demo is not connected to a nutrition plan. View support channels in the Contact tab.")),
        FaqEntry("checkin", profileText("Điểm danh", "Check-in"), profileText("Hồ sơ có điểm danh thật không?", "Does Profile support real check-in?"),
            profileText("Không. Phần này chỉ mô phỏng giao diện Hồ sơ, chưa gọi dịch vụ điểm danh.", "No. This flow previews Profile UI and does not call check-in services.")),
    )
    val filtered = entries.filter { (it.category + " " + it.question + " " + it.answer).contains(search.trim(), ignoreCase = true) }
    ProfilePage(profileText("Trợ giúp & câu hỏi", "Help & FAQ"), onBack) {
        ProfileHeading(profileText("Chúng tôi có thể giúp gì?", "How can we help?"))
        SupportTabs(true, {}, onContact)
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
internal fun ContactSupportScreen(onBack: () -> Unit, onFaq: () -> Unit, onMessage: (String) -> Unit) {
    val message = profileText("Kênh hỗ trợ mẫu. Chưa mở ứng dụng, gọi điện hoặc website thật.", "Sample support channel. No app, call or website has been opened.")
    ProfilePage(profileText("Liên hệ hỗ trợ", "Contact Support"), onBack) {
        SupportTabs(false, onFaq, {})
        listOf(profileText("Hotline CSKH", "Customer Service Hotline"), profileText("Trang web", "Website"), "Zalo / WhatsApp", "Facebook", "Instagram").forEach { label ->
            ProfileSettingRow(label, profileText("Mở kênh hỗ trợ", "View support channel")) { onMessage("$label\n\n$message") }
        }
    }
}

@Composable
private fun SupportTabs(faqSelected: Boolean, onFaq: () -> Unit, onContact: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileChoice(profileText("Câu hỏi", "FAQ"), faqSelected, onFaq, Modifier.weight(1f))
        ProfileChoice(profileText("Liên hệ", "Contact"), !faqSelected, onContact, Modifier.weight(1f))
    }
}
