@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kgyms.app.ui.member.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.model.AttemptState
import com.kgyms.app.data.model.PlanDates
import com.kgyms.app.data.model.WorkoutAttempt
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfileCard
import com.kgyms.app.ui.member.profile.profileText as tr
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun ProgressWeeklyCard(stats: ProgressAnalytics, date: DateFormat, load: ProgressLoad) {
    val p = LocalProfilePalette.current
    val week = stats.currentWeek
    ProfileCard {
        Text(tr("Mục tiêu tuần", "Weekly goal"), style = MaterialTheme.typography.titleMedium)
        val target = stats.target
        Text(if (target > 0) tr("$target buổi mỗi tuần", "$target sessions per week")
            else tr("Chọn mục tiêu hoặc tạo lịch tập", "Choose a goal or create a plan"),
            Modifier.testTag("progress_weekly_target"), color = p.accent)
        Text("${date.format(Date(week.start))} – ${date.format(Date(week.endExclusive - 1))}",
            color = p.muted, style = MaterialTheme.typography.bodySmall)
        if (LocalMemberPreferences.current?.weeklyTargetOverridden == true) {
            Text(tr("Mục tiêu riêng của bạn", "Your custom goal"), color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
        val completed = week.completed
        if (completed == null) {
            val unread = when (load) {
                ProgressLoad.Loading -> tr("Đang tải kết quả tuần này…", "Loading this week's results…")
                ProgressLoad.Failed -> tr("Không thể tải kết quả tuần này", "Could not load this week's results")
                else -> tr("Chưa có số liệu hoàn thành tuần này", "This week's completion is unavailable")
            }
            Text(unread, color = p.muted)
        } else {
            Column(Modifier.fillMaxWidth().testTag("progress_completed"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (target > 0) "$completed / $target" else "$completed",
                    Modifier.testTag("history_reported_unique"), style = MaterialTheme.typography.headlineSmall)
                Text(tr("buổi hoàn thành", "sessions completed"), Modifier.testTag("history_completed_label"), color = p.muted)
            }
            val remaining = stats.remaining
            if (remaining != null) {
                val ratio = (completed.toFloat() / target).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                Text(if (remaining == 0) tr("Đã đạt mục tiêu tuần", "Weekly goal reached")
                    else tr("Còn $remaining buổi để đạt mục tiêu", "$remaining more sessions to reach your goal"),
                    Modifier.testTag("progress_weekly_remaining"), color = p.muted)
            }
        }
    }
}

@Composable
internal fun ProgressActivityCard(weeks: List<ProgressWeek>, locale: Locale, zone: TimeZone) {
    val p = LocalProfilePalette.current
    val shortDate = remember(locale, zone) {
        SimpleDateFormat(if (locale.language == "vi") "d/M" else "M/d", locale).apply { timeZone = zone }
    }
    val longDate = remember(locale, zone) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone } }
    val scale = (weeks.mapNotNull { it.completed }.maxOrNull() ?: 0).coerceAtLeast(1)
    ProfileCard {
        Text(tr("Hoạt động ${weeks.size} tuần gần đây", "Activity over ${weeks.size} weeks"), style = MaterialTheme.typography.titleMedium)
        Text(tr("Buổi hoàn thành • tuần bắt đầu thứ Hai", "Completed sessions · weeks start Monday"),
            color = p.muted, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.fillMaxWidth().testTag("progress_activity_chart"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            weeks.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { week ->
                        val count = week.completed
                        val start = longDate.format(Date(week.start))
                        val end = longDate.format(Date(week.endExclusive - 1))
                        val description = if (count == null) tr("Tuần $start đến $end, chưa có số liệu", "Week $start to $end, data unavailable")
                            else tr("Tuần $start đến $end, $count buổi hoàn thành", "Week $start to $end, $count completed sessions")
                        val color = if (week == weeks.last()) p.accent else MaterialTheme.colorScheme.tertiary
                        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(count?.toString() ?: "—", style = MaterialTheme.typography.labelLarge, color = p.text)
                            Box(Modifier.fillMaxWidth(0.62f).height(80.dp).background(p.tint, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.BottomCenter) {
                                if (count != null) {
                                    val height = if (count == 0) 2.dp else (80.dp * (count.toFloat() / scale)).coerceAtLeast(4.dp)
                                    Box(Modifier.fillMaxWidth().height(height).background(color, RoundedCornerShape(8.dp)))
                                }
                            }
                            Text(shortDate.format(Date(week.start)), textAlign = TextAlign.Center,
                                color = if (week == weeks.last()) p.accent else p.muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (weeks.any { it.completed == null }) Text(tr("— Chưa có số liệu của tuần", "— Weekly data unavailable"),
            color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ProgressOverviewCard(stats: ProgressAnalytics) {
    ProfileCard {
        Text(tr("Tổng quan", "Overview"), style = MaterialTheme.typography.titleMedium)
        ProgressMetric(tr("Tổng buổi hoàn thành", "Total completed sessions"), stats.totalSessions?.toString(), "progress_total_sessions")
        ProgressMetric(tr("Số bài đã thực hiện qua các lượt tập", "Exercises done across attempts"),
            stats.performedExercises?.toString(), "progress_exercises_performed")
        val reached = stats.weeksAtCurrentTarget
        if (reached != null) {
            ProgressMetric(tr("Tuần đạt mức mục tiêu hiện tại", "Weeks meeting your current target"),
                "$reached / ${stats.weeks.size}", "progress_weeks_at_target")
            Text(tr("Đối chiếu ${stats.weeks.size} tuần gần đây với mục tiêu tuần hiện tại.",
                "Compares the latest ${stats.weeks.size} weeks to your current weekly target."),
                color = LocalProfilePalette.current.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ProgressPlanCard(
    progress: PlanProgress?,
    version: Int?,
    loading: Boolean,
    unavailable: Boolean,
    hasSavedPlans: Boolean,
    onPlan: () -> Unit,
    onCreatePlan: () -> Unit,
    locale: Locale,
) {
    val p = LocalProfilePalette.current
    ProfileCard {
        Text(tr("Tiến độ kế hoạch", "Plan progress"), style = MaterialTheme.typography.titleMedium)
        when {
            loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(tr("Đang tải kế hoạch…", "Loading your plan…"))
            }
            progress != null -> {
                val plan = progress.plan
                val zone = PlanDates.zone(plan.config.zoneId)
                val date = remember(locale, zone) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone } }
                val start = date.format(Date(PlanDates.millis(plan.config.startDate, plan.config.zoneId)))
                val level = plan.config.level.label.inLanguage(LocalProfileLanguage.current)
                Text(if (version == null) tr("Lịch tập đang dùng", "Active workout plan") else tr("Lịch tập $version", "Workout plan $version"),
                    Modifier.testTag("progress_plan_title"), style = MaterialTheme.typography.titleLarge)
                Text("$level · " + tr("Bắt đầu $start", "Starts $start"), color = p.muted, style = MaterialTheme.typography.bodySmall)
                ProgressMetric(tr("Tổng buổi theo lịch", "Scheduled sessions"), progress.scheduled.toString(), "progress_plan_scheduled")
                ProgressMetric(tr("Đã hoàn thành", "Completed"), progress.completed?.toString(), "progress_plan_completed")
                ProgressMetric(tr("Theo lịch từ hôm nay", "Upcoming sessions"), progress.upcoming.toString(), "progress_plan_upcoming")
                ProgressMetric(tr("Quá ngày, chưa hoàn thành", "Past and incomplete"), progress.pastIncomplete?.toString(), "progress_plan_past")
                if (progress.completed != null && progress.scheduled > 0) {
                    val ratio = (progress.completed.toFloat() / progress.scheduled).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().testTag("progress_plan_bar"))
                }
                if (progress.pastIncomplete == null) Text(tr("Cần tải đủ lịch sử để xác định các buổi quá ngày.",
                    "Past incomplete sessions require a complete history snapshot."), color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            unavailable -> Text(tr("Chưa xác định được lịch đang dùng. Mở Kế hoạch để kiểm tra.", "Your active plan is unavailable. Open Plan to check."),
                color = p.muted, modifier = Modifier.testTag("progress_plan_unavailable"))
            hasSavedPlans -> Text(tr("Chưa chọn lịch đang dùng", "No active plan selected"), color = p.muted)
            else -> {
                Text(tr("Bạn chưa có lịch tập. Tạo lịch để theo dõi các buổi theo kế hoạch.",
                    "Create a workout plan to track your scheduled sessions."), color = p.muted, modifier = Modifier.testTag("progress_plan_empty"))
                Button(onClick = onCreatePlan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress_create_plan")) {
                    Text(tr("Tạo lịch tập", "Create workout plan"))
                }
            }
        }
        OutlinedButton(onClick = onPlan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress_open_plan")) {
            Text(tr("Mở Kế hoạch", "Open Plan"))
        }
    }
}

@Composable
private fun ProgressMetric(label: String, value: String?, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = LocalProfilePalette.current.muted)
        Text(value ?: "—", Modifier.testTag(tag), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun attemptTitle(attempt: WorkoutAttempt, language: String): String =
    (if (language == "vi") attempt.titleVi else attempt.titleEn).ifBlank { tr("Buổi tập", "Workout session") }

@Composable
private fun attemptStatus(status: AttemptState): String = when (status) {
    AttemptState.LocalPending -> tr("Lưu trên thiết bị", "Saved on device")
    AttemptState.SelfReported -> tr("Tự ghi nhận", "Self-reported")
    AttemptState.Confirmed -> tr("Đã xác nhận", "Confirmed")
}

@Composable
internal fun ProgressAttemptCard(attempt: WorkoutAttempt, status: AttemptState, date: DateFormat, language: String, onOpen: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onOpen, color = p.card, contentColor = p.text, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag("history_attempt_${attempt.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(attemptTitle(attempt, language), style = MaterialTheme.typography.titleMedium)
            Text(date.format(Date(attempt.finishedAt)), color = p.muted)
            Text(attemptStatus(status), color = if (status == AttemptState.Confirmed) MaterialTheme.colorScheme.tertiary else p.muted)
            val done = attempt.exercises.count { it.performed }
            Text(tr("$done / ${attempt.exercises.size} bài đã thực hiện", "$done / ${attempt.exercises.size} exercises done"),
                style = MaterialTheme.typography.bodySmall)
            Text(tr("Xem chi tiết ›", "View details ›"), color = p.accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun ProgressAttemptDetails(attempt: WorkoutAttempt, status: AttemptState, date: DateFormat, language: String, onDismiss: () -> Unit) {
    val p = LocalProfilePalette.current
    val exerciseNames = remember { WorkoutReferenceCatalog.snapshot().exercises.associate { it.id to it.title } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.card, contentColor = p.text) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f).testTag("progress_attempt_detail"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(attemptTitle(attempt, language), style = MaterialTheme.typography.titleLarge)
                Text(date.format(Date(attempt.finishedAt)), color = p.muted)
                Text(attemptStatus(status), color = p.accent)
            }
            if (attempt.exercises.isEmpty()) item {
                Text(tr("Lượt tập này không có chi tiết bài tập.", "Exercise details are unavailable for this attempt."), color = p.muted)
            }
            items(attempt.exercises) { exercise ->
                val name = exerciseNames[exercise.id]?.inLanguage(language)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("progress_detail_exercise_${exercise.id}"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name ?: tr("Bài tập không còn trong thư viện", "Exercise no longer in the library"),
                        style = MaterialTheme.typography.titleSmall)
                    Text(if (exercise.performed) tr("Đã thực hiện", "Done") else tr("Chưa thực hiện", "Not done"),
                        color = if (exercise.performed) MaterialTheme.colorScheme.tertiary else p.muted)
                }
                HorizontalDivider(color = p.border)
            }
            item {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("progress_close_detail")) { Text(tr("Đóng", "Close")) }
            }
        }
    }
}
