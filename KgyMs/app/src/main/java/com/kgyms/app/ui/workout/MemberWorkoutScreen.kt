package com.kgyms.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.profile.LocalProfileLanguage
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.profileText as tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun MemberWorkoutScreen(entry: String, request: Int, onOverview: () -> Unit, onHome: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("plan")) }
    var handledRequest by rememberSaveable { mutableIntStateOf(-1) }
    var marks by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(request) {
        if (handledRequest != request) { stack = listOf(entry); handledRequest = request }
    }
    val back = { if (stack.size > 1) stack = stack.dropLast(1) else if (stack.last() != "plan") stack = listOf("plan") else onOverview() }
    BackHandler { back() }
    val route = stack.last()
    val navigate: (String) -> Unit = { stack = stack + it }
    val language = LocalProfileLanguage.current
    val p = LocalProfilePalette.current
    val locale = if (language == "vi") Locale.forLanguageTag("vi-VN") else Locale.ENGLISH
    val week = workoutMonday()
    fun date(offset: Int) = SimpleDateFormat("EEE, dd MMM", locale).format(Calendar.getInstance().apply {
        timeInMillis = week; add(Calendar.DAY_OF_MONTH, offset)
    }.time)
    val session = WorkoutMockData.sessions.firstOrNull { route == "session:${it.id}" }
    val exercise = WorkoutMockData.exercises.firstOrNull { route == "exercise:${it.id}" }
    val title = when {
        session != null -> session.title.inLanguage(language)
        exercise != null -> exercise.name.inLanguage(language)
        route == "library" -> tr("Thư viện bài tập", "Workout Library")
        route == "assigned" -> tr("Giáo án HLV giao", "Assigned by PT")
        else -> tr("Kế hoạch tập của tôi", "My Workout Plan")
    }
    key(route) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("plan_content_workout"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = back, modifier = Modifier.testTag("workout_back")) { Text(tr("‹ Quay lại", "‹ Back")) }
                TextButton(onClick = onHome) { Text(tr("Trang chủ", "Home")) }
            }
            Text("KGYMS / WORKOUT", color = p.accent, style = MaterialTheme.typography.labelMedium)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(tr("Dữ liệu mẫu • Theo dõi thử trên thiết bị", "Sample data • Local rehearsal tracking"), color = p.muted)
        }
        if (session == null && exercise == null) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("plan" to tr("Của tôi", "My Plan"), "library" to tr("Thư viện", "Library"), "assigned" to tr("HLV giao", "Assigned by PT")).forEach { (key, label) ->
                        FilterChip(selected = route == key, onClick = { stack = listOf(key) }, label = { Text(label) }, modifier = Modifier.testTag("workout_$key"))
                    }
                }
            }
        }
        when {
            exercise != null -> {
                item { WorkoutPanel {
                    Text("${exercise.muscle.inLanguage(language)} • ${exercise.level.inLanguage(language)}", color = p.accent)
                    Text(exercise.equipment.inLanguage(language))
                    Text("${exercise.sets} ${tr("hiệp", "sets")} × ${exercise.reps} • ${tr("Nghỉ", "Rest")} ${exercise.restSeconds} s", style = MaterialTheme.typography.titleMedium)
                } }
                items(exercise.instructions.indices.toList()) { index -> WorkoutPanel {
                    Text("${index + 1}. ${exercise.instructions[index].inLanguage(language)}")
                } }
                item { WorkoutPanel {
                    Text(tr("Lưu ý an toàn", "Safety notes"), color = p.accent)
                    Text(exercise.safety.inLanguage(language))
                    Text(tr("Khởi động trước khi tập. Dừng khi đau hoặc chóng mặt và nhờ HLV kiểm tra kỹ thuật.", "Warm up first. Stop if you feel pain or dizziness and ask your trainer to check your technique."))
                } }
            }
            session != null -> {
                val done = session.exercises.count { trackingKey(week, session.id, it) in marks }
                item { WorkoutPanel {
                    Text("${date(session.dayOffset)} • ${session.minutes} ${tr("phút", "min")}")
                    Text(sourceLabel(session.source), color = p.accent)
                    Text(statusLabel(session.status))
                    Text(tr("Đã đánh dấu thử", "Locally marked") + ": $done / ${session.exercises.size}")
                    LinearProgressIndicator(progress = { done.toFloat() / session.exercises.size }, modifier = Modifier.fillMaxWidth())
                    Text(tr("Đánh dấu không xác nhận hoàn thành buổi tập và không cộng mục tiêu tuần.", "Marking exercises does not confirm a completed session or add to your weekly goal."), color = p.muted)
                } }
                items(session.exercises, key = { it }) { id ->
                    val item = WorkoutMockData.exercises.first { it.id == id }
                    val key = trackingKey(week, session.id, id)
                    WorkoutPanel {
                        TextButton(onClick = { navigate("exercise:$id") }, modifier = Modifier.testTag("exercise_$id")) { Text(item.name.inLanguage(language)) }
                        Text("${item.sets} × ${item.reps} • ${tr("Nghỉ", "Rest")} ${item.restSeconds} s")
                        Row {
                            Checkbox(checked = key in marks, onCheckedChange = { marks = toggleWorkoutMark(marks, key) }, modifier = Modifier.testTag("mark_$id"))
                            Text(tr("Đã thực hiện (thử nghiệm)", "Performed (rehearsal)"), modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                item { OutlinedButton(onClick = { marks = marks.filterNot { it.startsWith("$week:${session.id}:") } }) { Text(tr("Bỏ đánh dấu buổi này", "Clear this rehearsal")) } }
            }
            route == "library" -> {
                item {
                    OutlinedTextField(query, { query = it }, label = { Text(tr("Tìm tên bài / nhóm cơ", "Search exercise / muscle")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workout_search"))
                    WorkoutFilters(tr("Nhóm cơ", "Muscle"), muscle, WorkoutMockData.exercises.map { it.muscle }.distinct(), { muscle = it })
                    WorkoutFilters(tr("Trình độ", "Level"), level, WorkoutMockData.exercises.map { it.level }.distinct(), { level = it })
                    WorkoutFilters(tr("Dụng cụ", "Equipment"), equipment, WorkoutMockData.exercises.map { it.equipment }.distinct(), { equipment = it })
                    TextButton(onClick = { query = ""; muscle = ""; level = ""; equipment = "" }) { Text(tr("Xóa bộ lọc", "Reset filters")) }
                }
                val results = searchExercises(query, muscle, level, equipment)
                if (results.isEmpty()) item { Text(tr("Không tìm thấy bài tập. Hãy thử bỏ bớt bộ lọc.", "No exercises found. Try removing a filter.")) }
                items(results, key = { it.id }) { item -> WorkoutLink(item.name.inLanguage(language),
                    "${item.muscle.inLanguage(language)} • ${item.level.inLanguage(language)} • ${item.equipment.inLanguage(language)}", "library_${item.id}") { navigate("exercise:${item.id}") } }
            }
            else -> {
                item { WorkoutPanel {
                    if (route == "assigned") {
                        Text(WorkoutMockData.trainer, style = MaterialTheme.typography.titleLarge)
                        Text(tr("Giáo án mẫu • Đang áp dụng", "Sample assignment • Active"), color = p.accent)
                        Text(tr("Ngày giao", "Assigned on") + ": ${date(0)}")
                        Text(tr("Mục tiêu: củng cố kỹ thuật và tăng sức mạnh toàn thân. HLV theo dõi hai buổi mỗi tuần.", "Goal: build technique and full-body strength. Two trainer-led sessions each week."))
                    } else {
                        Text("${date(0)} — ${date(6)}", color = p.accent)
                        Text(tr("Mục tiêu thể lực: tăng sức mạnh, duy trì đều đặn", "Fitness focus: strength and consistency"))
                        Text(tr("Mục tiêu đã chọn", "Selected weekly target") + ": ${LocalMemberPreferences.current?.weeklyTarget ?: 6} " + tr("buổi / tuần", "sessions / week"))
                        Text(tr("4 buổi trong giáo án mẫu. Tiến độ thử nghiệm không phải điểm danh.", "4 sessions in this sample plan. Rehearsal progress is not attendance."))
                        val total = WorkoutMockData.sessions.sumOf { it.exercises.size }
                        val done = WorkoutMockData.sessions.sumOf { s -> s.exercises.count { trackingKey(week, s.id, it) in marks } }
                        Text("$done / $total " + tr("động tác đã đánh dấu thử", "exercises marked locally"))
                        LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    }
                } }
                if (route == "plan") {
                    val today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
                    item { Text(tr("Hôm nay", "Today"), style = MaterialTheme.typography.titleLarge) }
                    val todaySessions = WorkoutMockData.sessions.filter { it.dayOffset == today }
                    if (todaySessions.isEmpty()) item { Text(tr("Ngày phục hồi. Bạn có thể xem thư viện và chuẩn bị buổi tiếp theo.", "Recovery day. Browse the library and prepare for your next session.")) }
                    items(todaySessions, key = { "today_${it.id}" }) { s -> WorkoutLink(s.title.inLanguage(language), "${s.minutes} " + tr("phút", "min"), "today_${s.id}") { navigate("session:${s.id}") } }
                    item { Text(tr("Lịch tập trong tuần", "This week's schedule"), style = MaterialTheme.typography.titleLarge) }
                }
                items(WorkoutMockData.sessions.filter { route != "assigned" || it.source == PlanSource.Trainer }, key = { it.id }) { s ->
                    WorkoutLink(s.title.inLanguage(language), "${date(s.dayOffset)} • ${s.minutes} ${tr("phút", "min")}\n${sourceLabel(s.source)} • ${statusLabel(s.status)}", "session_${s.id}") { navigate("session:${s.id}") }
                }
            }
        }
    }
}
}

@Composable
private fun sourceLabel(source: PlanSource) = if (source == PlanSource.Trainer) tr("HLV giao", "Trainer assigned") else tr("Tự tập", "Self directed")
@Composable
private fun statusLabel(status: SessionStatus) = when (status) {
    SessionStatus.Planned -> tr("Dự kiến • mẫu", "Planned • sample")
    SessionStatus.Booked -> tr("Đã đặt lịch • mẫu, chưa hoàn thành", "Booked • sample, not completed")
    SessionStatus.Confirmed -> tr("Hoàn thành, đã xác nhận • bản ghi mẫu", "Completed, confirmed • sample record")
}
@Composable
private fun WorkoutPanel(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProfilePalette.current
    Surface(shape = RoundedCornerShape(22.dp), color = p.card, border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable
private fun WorkoutLink(title: String, detail: String, tag: String, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = p.card, border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.accent)
            Text(detail, color = p.muted)
        }
    }
}
@Composable
private fun WorkoutFilters(title: String, selected: String, values: List<WorkoutText>, onSelect: (String) -> Unit) {
    Text(title, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected.isEmpty(), { onSelect("") }, label = { Text(tr("Tất cả", "All")) })
        values.forEach { value -> FilterChip(selected == value.en, { onSelect(value.en) }, label = { Text(value.inLanguage(LocalProfileLanguage.current)) }) }
    }
}
