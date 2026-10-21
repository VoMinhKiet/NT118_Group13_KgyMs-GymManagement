package com.kgyms.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.LocalProfileLanguage
import com.kgyms.app.ui.profile.ProfileAppearance
import com.kgyms.app.ui.profile.profileText as tr
import com.kgyms.app.ui.theme.KgyMsTheme
import kotlinx.coroutines.delay

@Composable
fun MemberHomeScreen(
    onAction: (MemberHomeAction) -> Unit,
    modifier: Modifier = Modifier,
    data: MemberHomeUiData? = null,
) {
    val preferences = LocalMemberPreferences.current
    val language = LocalProfileLanguage.current
    // Refresh the sample week when an open screen crosses midnight/week boundaries.
    val clock by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000) }
    }
    val home = data ?: remember(language, preferences?.weeklyTarget, clock) {
        createMockMemberHome(language, preferences?.weeklyTarget ?: 6, clock)
    }
    val p = LocalProfilePalette.current
    var previewKey by rememberSaveable { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize().background(p.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().safeDrawingPadding().testTag("member_screen_home"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") { HomeHeader(home, { onAction(MemberHomeAction.Profile) }, { previewKey = "notifications" }) }
            item(key = "goal") { WeeklyGoalCard(home.weeklyGoal) }
            item(key = "actions") { HomeQuickActions(onAction, { previewKey = "pt" }) }
            item(key = "workouts") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeading(tr("Gợi ý cho bạn", "Recommended for You"), "home_workouts_all") { onAction(MemberHomeAction.WorkoutLibrary) }
                    home.workouts.forEach { workout -> WorkoutRecommendationCard(workout) { onAction(MemberHomeAction.RecommendedWorkout) } }
                }
            }
            item(key = "meals") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeading(tr("Bữa ăn hôm nay", "Today's Meal Plan"), "home_meals_all") { onAction(MemberHomeAction.Nutrition) }
                    home.meals.forEach { meal -> MealRecommendationCard(meal) { previewKey = "meal:${meal.id}" } }
                }
            }
            item(key = "membership") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Gói hội viên", "My Membership"), color = p.text, style = MaterialTheme.typography.titleLarge)
                    HomeMembershipCard(home.membership) { previewKey = "membership" }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { previewKey = "booking" }, modifier = Modifier.weight(1f).testTag("home_booking")) {
                            Text(tr("Đặt lịch", "Bookings"))
                        }
                        OutlinedButton(onClick = { previewKey = "card-request" }, modifier = Modifier.weight(1f).testTag("home_card_request")) {
                            Text(tr("Mở thẻ / Gia hạn", "Join / Renew"))
                        }
                    }
                }
            }
            item(key = "activity") { HomeWeeklyActivity(home.activity) }
        }
    }
    previewKey?.let { key ->
        HomePreviewDialog(key, home, onDismiss = {
            previewKey = if (key.startsWith("pt-")) "pt" else if (key.startsWith("booking-")) "booking" else null
        }, onPreview = { previewKey = it }, onAction = { action -> previewKey = null; onAction(action) })
    }
}

@Composable
private fun HomePreviewDialog(
    key: String, home: MemberHomeUiData, onDismiss: () -> Unit,
    onPreview: (String) -> Unit, onAction: (MemberHomeAction) -> Unit,
) {
    val workout = home.workouts.firstOrNull { key == "workout:${it.id}" }
    val meal = home.meals.firstOrNull { key == "meal:${it.id}" }
    val title = when {
        workout != null -> workout.name
        meal != null -> meal.name
        key == "notifications" -> tr("Thông báo", "Notifications")
        key == "membership" -> tr("Gói hội viên", "My Membership")
        key == "pt" -> tr("Huấn luyện viên của tôi", "My PT")
        key == "booking" -> tr("Đặt lịch", "Bookings")
        key == "card-request" -> tr("Mở thẻ / Gia hạn", "Join / Renew")
        key == "pt-chat" -> tr("Trao đổi với HLV", "Contact your trainer")
        key == "pt-workout" -> tr("Bài tập HLV giao", "Trainer-assigned workouts")
        key == "pt-meal" -> tr("Thực đơn HLV giao", "Trainer-assigned meals")
        else -> tr("Đặt lịch • Xem trước", "Booking • Preview")
    }
    AlertDialog(
        modifier = Modifier.testTag("home_preview"), onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(tr("XEM TRƯỚC • DỮ LIỆU MẪU", "PREVIEW • SAMPLE DATA"),
                    style = MaterialTheme.typography.labelSmall, color = LocalProfilePalette.current.accent)
                when {
                    workout != null -> {
                        Text("${workout.minutes} ${tr("phút", "min")} • ${workout.level} • ${workout.category}")
                        Text(tr("Khám phá bài tập trong khu vực tập luyện. Màn chi tiết sẽ được bổ sung sau.",
                            "Explore this session in the Workout area. Full details are coming soon."))
                    }
                    meal != null -> {
                        Text("${meal.mealType} • ${meal.calories} kcal • ${meal.proteinGrams} g ${tr("đạm", "protein")}")
                        Text(tr("Gợi ý bữa ăn cân đối. Thành phần và thực đơn đầy đủ sẽ được bổ sung sau.",
                            "A balanced meal idea. Ingredients and complete meal plans are coming soon."))
                    }
                    key == "notifications" -> {
                        home.notifications.forEach { notice ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(notice.title, style = MaterialTheme.typography.titleSmall)
                                Text(notice.message, color = LocalProfilePalette.current.muted)
                            }
                            HorizontalDivider(color = LocalProfilePalette.current.border)
                        }
                        Text(tr("Thông báo minh họa, không phải thông báo trực tiếp.", "These are sample notices, not live notifications."))
                    }
                    key == "membership" -> {
                        val member = home.membership
                        Text("${home.memberName}\n\n${member.packageName}\n${member.status}\n${tr("Hết hạn", "Expiry")}: ${member.expiresOn}\n${member.daysRemaining} ${tr("ngày còn lại", "days remaining")}")
                        Text(tr("Thông tin minh họa. Chưa gia hạn hoặc thanh toán thật.", "Illustrative membership details. No renewal or payment is processed."))
                    }
                    key == "pt" -> {
                        val trainer = home.trainer
                        if (trainer == null) Text(tr("Bạn chưa có HLV phụ trách. Có thể xem trước yêu cầu đặt lịch tư vấn bên dưới.",
                            "You have no assigned trainer. You can preview a consultation booking below."))
                        else {
                            Text(trainer.name, style = MaterialTheme.typography.titleMedium)
                            Text(trainer.specialty)
                            TextButton(onClick = { onPreview("pt-chat") }, modifier = Modifier.testTag("pt_chat")) { Text(tr("Trao đổi với HLV", "Contact trainer")) }
                            TextButton(onClick = { onAction(MemberHomeAction.AssignedWorkout) }, modifier = Modifier.testTag("pt_workout")) { Text(tr("Kế hoạch tập HLV giao", "Trainer Workout Plan")) }
                            TextButton(onClick = { onPreview("pt-meal") }) { Text(tr("Thực đơn HLV giao", "Trainer Meal Plan")) }
                        }
                        TextButton(onClick = { onPreview("pt-booking") }, modifier = Modifier.testTag("pt_booking")) { Text(tr("Đặt lịch với HLV", "Book a trainer")) }
                    }
                    key == "booking" -> {
                        listOf("gym" to tr("Đặt lịch tập luyện", "Book a gym session"),
                            "pt" to tr("Đặt lịch với HLV", "Book a trainer"),
                            "class" to tr("Đăng ký lớp học", "Register for a class")).forEach { (id, label) ->
                            TextButton(onClick = { onPreview("booking-$id") }, modifier = Modifier.testTag("booking_$id")) { Text(label) }
                        }
                    }
                    key == "card-request" -> Text(tr("Xem trước đăng ký mở thẻ hoặc gia hạn online. Yêu cầu thực tế sẽ cần Admin xác nhận. Hiện chưa tạo yêu cầu, mã thanh toán hay trạng thái chờ xác nhận thật.",
                        "Preview joining or renewing online. Real requests will require Admin approval. No request, payment code or real pending status has been created."))
                    key == "pt-chat" -> Text(tr("Chưa có nhắn tin trực tiếp. Không có tin nhắn nào được gửi.", "Live messaging is not available. No message has been sent."))
                    key == "pt-workout" || key == "pt-meal" -> Text(tr("Chưa có kế hoạch HLV giao thật. Khu vực này đang ở chế độ xem trước.", "No real trainer-assigned plan is available. This area is a preview."))
                    else -> Text(tr("Xem trước đặt lịch. Chưa có lịch nào được gửi hoặc lưu. Đặt lịch không được tính là đã hoàn thành buổi tập; yêu cầu thật sẽ cần xác nhận.",
                        "Booking preview. No appointment has been submitted or saved. A booking does not count as a completed workout; real requests will require confirmation."))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("home_preview_close")) {
                Text(if (key.startsWith("pt-") || key.startsWith("booking-")) tr("Quay lại", "Back") else tr("Đóng", "Close"))
            }
        },
        dismissButton = {
            if (workout != null || meal != null) TextButton(onClick = {
                onAction(if (meal != null) MemberHomeAction.Nutrition else MemberHomeAction.Workouts)
            }, modifier = Modifier.testTag("home_preview_plan")) {
                Text(if (meal != null) tr("Mở Dinh dưỡng", "Open Nutrition") else tr("Mở Tập luyện", "Open Workout"))
            }
        },
    )
}

@Preview(name = "Home dark", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "Home compact", widthDp = 320, heightDp = 640, fontScale = 1.5f, showBackground = true)
@Composable
private fun MemberHomePreview() {
    KgyMsTheme(darkTheme = true, dynamicColor = false) {
        ProfileAppearance(true, "vi") { MemberHomeScreen(onAction = {}) }
    }
}

@Preview(name = "Home light English", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MemberHomeLightPreview() {
    KgyMsTheme(darkTheme = false, dynamicColor = false) {
        ProfileAppearance(false, "en") { MemberHomeScreen(onAction = {}) }
    }
}
