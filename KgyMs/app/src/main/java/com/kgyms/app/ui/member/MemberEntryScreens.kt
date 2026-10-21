package com.kgyms.app.ui.member

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.home.createMockMemberHome
import com.kgyms.app.ui.profile.LocalProfileLanguage
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.profileText as tr

internal enum class MemberPlanSection { Overview, Workout, Nutrition }

@Composable
private fun EntryPage(title: String, tag: String, onHome: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(24.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(tr("Xem trước • Dữ liệu mẫu", "Preview • Sample data"), color = LocalProfilePalette.current.muted)
        content()
        TextButton(onClick = onHome, modifier = Modifier.testTag("member_entry_home")) { Text(tr("Về Trang chủ", "Back to Home")) }
    }
}

@Composable
internal fun MemberPlanPreview(section: MemberPlanSection, onSection: (MemberPlanSection) -> Unit, onHome: () -> Unit) {
    val language = LocalProfileLanguage.current
    val target = LocalMemberPreferences.current?.weeklyTarget ?: 6
    val data = remember(language, target) { createMockMemberHome(language, target) }
    EntryPage(tr("Kế hoạch của tôi", "My Plan"), "member_screen_plan", onHome) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemberPlanSection.entries.forEach { choice ->
                FilterChip(selected = section == choice, onClick = { onSection(choice) },
                    modifier = Modifier.weight(1f).testTag("plan_section_${choice.name.lowercase()}"),
                    label = { Text(when (choice) {
                        MemberPlanSection.Overview -> tr("Tổng quan", "Overview")
                        MemberPlanSection.Workout -> tr("Tập luyện", "Workout")
                        MemberPlanSection.Nutrition -> tr("Dinh dưỡng", "Nutrition")
                    }) })
            }
        }
        if (section == MemberPlanSection.Overview) {
            EntryCard(tr("Kế hoạch tập luyện", "Workout Plan"), tr("Bài tập và kế hoạch do HLV giao.", "Your workouts and trainer-assigned plans."), "plan_workout") { onSection(MemberPlanSection.Workout) }
            EntryCard(tr("Kế hoạch dinh dưỡng", "Nutrition Plan"), tr("Bữa ăn gợi ý và thực đơn do HLV giao.", "Meal ideas and trainer-assigned nutrition."), "plan_nutrition") { onSection(MemberPlanSection.Nutrition) }
        } else {
            Text(if (section == MemberPlanSection.Workout) tr("Khu vực tập luyện", "Workout area") else tr("Khu vực dinh dưỡng", "Nutrition area"),
                modifier = Modifier.testTag("plan_content_${section.name.lowercase()}"), style = MaterialTheme.typography.titleLarge)
            if (section == MemberPlanSection.Workout) {
                val workout = data.workouts.first()
                Text("${workout.name} • ${workout.minutes} ${tr("phút", "min")} • ${workout.level}")
            } else {
                val meal = data.meals.first()
                Text("${meal.name} • ${meal.calories} kcal • ${meal.proteinGrams} g ${tr("đạm", "protein")}")
            }
            Text(tr("Nội dung minh họa. Chưa có màn chi tiết hoặc thao tác bắt đầu/lưu kế hoạch.",
                "Illustrative content. Full details and start/save actions are not available yet."), color = LocalProfilePalette.current.muted)
        }
    }
}

@Composable
internal fun MemberQrPreview(onHome: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    EntryPage(tr("Dịch vụ QR", "QR services"), "member_screen_qr", onHome) {
        EntryCard(tr("Quét QR điểm danh tại gym", "Scan gym check-in QR"),
            tr("Xem trước quy trình điểm danh.", "Preview the gym check-in flow."), "qr_checkin") { choice = "checkin" }
        EntryCard(tr("QR thanh toán", "QR payment"),
            tr("Xem trước thanh toán gói tập.", "Preview membership payment."), "qr_payment") { choice = "payment" }
    }
    choice?.let { selected ->
        AlertDialog(onDismissRequest = { choice = null }, modifier = Modifier.testTag("qr_preview"),
            title = { Text(if (selected == "checkin") tr("Điểm danh • Xem trước", "Check-in • Preview") else tr("Thanh toán • Xem trước", "Payment • Preview")) },
            text = { Text(if (selected == "checkin")
                tr("Chưa bật camera hoặc xác nhận điểm danh. Xem trước này không được tính vào mục tiêu tuần.", "No camera or attendance confirmation is active. This preview does not count toward your weekly goal.")
                else tr("Chưa tạo mã hoặc giao dịch thanh toán. Không có tiền được chuyển và không có trạng thái thanh toán thành công.", "No payment code or transaction has been created. No money is transferred and no payment success is recorded.")) },
            confirmButton = { TextButton(onClick = { choice = null }) { Text(tr("Đóng", "Close")) } })
    }
}

@Composable
private fun EntryCard(title: String, detail: String, tag: String, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = p.card,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = p.muted)
        }
    }
}
