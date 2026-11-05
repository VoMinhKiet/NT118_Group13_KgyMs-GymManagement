package com.kgyms.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kgyms.app.R
import com.kgyms.app.ui.home.calculateWeeklyGoal
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.profile.LocalProfileLanguage
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.ProfileAppearance
import com.kgyms.app.ui.profile.profileText as tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun MemberWorkoutScreen(entry: String, request: Int, onOverview: () -> Unit, onHome: () -> Unit,
    onNutrition: () -> Unit = {}, dataSource: MemberWorkoutDataSource = MockMemberWorkoutDataSource,
    catalog: WorkoutCatalog? = null, onPlayVideo: ((VideoSourceUiModel) -> Unit)? = null) {
    var stack by rememberSaveable { mutableStateOf(listOf(entry)) }
    var handled by rememberSaveable { mutableIntStateOf(request) }
    var marks by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var activeSessions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var level by rememberSaveable { mutableStateOf(WorkoutLevel.Beginner.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterLevel by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollHolder = rememberSaveableStateHolder()
    LaunchedEffect(request) { if (handled != request) { stack = listOf(entry); handled = request; dialog = null } }
    val clock by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(60_000) } }
    val data = catalog ?: remember(clock, dataSource) { dataSource.snapshot(clock) }
    val language = LocalProfileLanguage.current
    val p = LocalProfilePalette.current
    val week = workoutMonday(clock)
    val goal = calculateWeeklyGoal(LocalMemberPreferences.current?.weeklyTarget ?: 6, data.confirmedRecords(), clock)
    val route = stack.last()
    val navigate: (String) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else if (route != "plan") stack = listOf("plan") else onOverview() }
    BackHandler { back() }
    val workout = data.workouts.firstOrNull { route == "session:${it.id}" }
    val exercise = data.exercises.firstOrNull { route == "exercise:${it.id}" }
    val locale = if (language == "vi") Locale.forLanguageTag("vi-VN") else Locale.ENGLISH
    fun date(time: Long) = SimpleDateFormat("EEE, dd/MM", locale).format(Date(time))
    val title = when {
        workout != null -> tr("Buổi tập", "Your workout")
        exercise != null -> tr("Hướng dẫn", "Exercise guide")
        route == "library" -> tr("Thư viện", "Exercise Library")
        route == "assigned" -> tr("HLV của tôi", "My PT")
        route == "chat" -> tr("Trao đổi với HLV", "Trainer conversation")
        route == "hub" -> tr("Khám phá bài tập", "Find Your Workout")
        else -> tr("Kế hoạch của tôi", "My Plan")
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("plan_content_workout")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.testTag("workout_back")) { Text(tr("‹ Trở lại", "‹ Back")) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHome, modifier = Modifier.testTag("member_entry_home")) { Text(tr("Trang chủ", "Home")) }
        }
        scrollHolder.SaveableStateProvider(route) {
            LazyColumn(Modifier.fillMaxSize().testTag("workout_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    if (workout == null && exercise == null && route != "chat") {
                        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("plan" to tr("Lịch của tôi", "My schedule"), "hub" to tr("Tập luyện", "Workouts"), "assigned" to tr("HLV của tôi", "My PT")).forEach { (id, label) ->
                                FilterChip(route == id, { stack = listOf(id) }, label = { Text(label) }, modifier = Modifier.testTag("workout_$id"))
                            }
                        }
                    }
                }
                when {
                    workout != null -> {
                        val session = data.sessions.firstOrNull { it.workoutId == workout.id }
                        val trackingId = session?.id ?: "practice-$week-${workout.id}"
                        val active = trackingId in activeSessions
                        val done = workout.exerciseIds.count { trackingKey(week, trackingId, it) in marks }
                        item { WorkoutHero(workout, workout.category.label.localized()) { dialog = "intro" } }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WorkoutBadge("≈ ${workout.calories} kcal")
                                WorkoutBadge(workout.level.label.localized())
                            }
                            session?.let { Text("${date(it.scheduledAt)} · ${statusLabel(it.status)}", Modifier.padding(top = 12.dp), color = p.muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        item {
                            Button(onClick = { if (!active) activeSessions = activeSessions + trackingId else dialog = "finish" },
                                modifier = Modifier.fillMaxWidth().testTag("workout_start"), shape = RoundedCornerShape(18.dp)) {
                                Text(if (active) tr("Kết thúc buổi tập", "Finish workout") else tr("Bắt đầu tập", "Start Workout"), Modifier.padding(6.dp))
                            }
                            if (active) Text("$done / ${workout.exerciseCount} ${tr("bài đã tập", "exercises done")}", Modifier.padding(top = 8.dp), color = p.muted)
                        }
                        workout.exerciseIds.chunked(2).forEachIndexed { round, ids ->
                            item { Text(tr("Nhóm ${round + 1}", "Round ${round + 1}"), color = p.accent, style = MaterialTheme.typography.titleMedium) }
                            items(ids, key = { it }) { id ->
                                val ex = data.exercises.first { it.id == id }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ExerciseTile(ex, "exercise_$id") { navigate("exercise:$id") }
                                    if (active) {
                                        val mark = trackingKey(week, trackingId, id)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(mark in marks, { marks = toggleWorkoutMark(marks, mark) }, modifier = Modifier.testTag("mark_$id"))
                                            Text(tr("Đã tập", "Done"), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.weight(1f))
                                            Text("${tr("Nghỉ", "Rest")} ${ex.restSeconds}s", color = p.muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    exercise != null -> {
                        item {
                            Surface(onClick = { if (onPlayVideo != null) onPlayVideo(exercise.video) else dialog = "video:${exercise.id}" }, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().testTag("exercise_play")) {
                                Box(Modifier.height(260.dp), contentAlignment = Alignment.Center) {
                                    Image(painterResource(exercise.thumbnailRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Surface(shape = CircleShape, color = p.accent, shadowElevation = 10.dp) {
                                        Icon(painterResource(R.drawable.home_icon_play), tr("Phát hướng dẫn", "Play guide"), Modifier.padding(23.dp).size(30.dp), tint = Color.White)
                                    }
                                }
                            }
                        }
                        item {
                            Text(exercise.title.localized(), style = MaterialTheme.typography.headlineSmall)
                            Text("${exercise.sets} × ${exercise.reps}  ·  ${exercise.level.label.localized()}", Modifier.padding(top = 8.dp), color = p.accent)
                            Text("${exercise.muscleGroup.localized()} · ${exercise.equipment.localized()}", Modifier.padding(top = 6.dp), color = p.muted)
                        }
                        item { WorkoutPanel {
                            Text(tr("Cách thực hiện", "How to move"), style = MaterialTheme.typography.titleMedium)
                            exercise.instructions.take(3).forEachIndexed { index, text -> Text("${index + 1}. ${text.localized()}", style = MaterialTheme.typography.bodyMedium) }
                        } }
                        item { Text(exercise.safety.localized(), color = p.muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    route == "hub" -> {
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WorkoutLevel.entries.forEach { choice -> FilterChip(level == choice.name, { level = choice.name }, label = { Text(choice.label.localized()) }, modifier = Modifier.testTag("level_${choice.name}")) }
                            }
                        }
                        val selected = data.workouts.filter { it.level.name == level }
                        selected.firstOrNull()?.let { featured -> item { WorkoutHero(featured, tr("BÀI TẬP HÔM NAY", "TRAINING OF THE DAY")) { navigate("session:${featured.id}") } } }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Dành cho bạn", "Made for you"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { navigate("library") }, modifier = Modifier.testTag("workout_library")) { Text(tr("Thư viện ›", "Library ›")) }
                            }
                        }
                        items(selected, key = { it.id }) { w -> WorkoutTile(w) { navigate("session:${w.id}") } }
                    }
                    route == "library" -> {
                        item {
                            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(tr("Tìm bài tập", "Search exercises")) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("workout_search"))
                            FilterStrip(tr("Trình độ", "Level"), filterLevel, WorkoutLevel.entries.map { it.name to it.label.localized() }) { filterLevel = it }
                            FilterStrip(tr("Nhóm cơ", "Muscle"), muscle, data.exercises.map { it.muscleGroup }.distinct().map { it.en to it.localized() }) { muscle = it }
                            FilterStrip(tr("Loại bài", "Type"), category, WorkoutCategory.entries.map { it.name to it.label.localized() }) { category = it }
                            TextButton(onClick = { query = ""; filterLevel = ""; muscle = ""; category = "" }) { Text(tr("Xóa bộ lọc", "Clear filters")) }
                        }
                        val results = filterCatalogExercises(data.exercises, query, filterLevel, muscle, category)
                        if (results.isEmpty()) item { WorkoutPanel { Text(tr("Chưa tìm thấy bài phù hợp", "No matching exercises")); Text(tr("Thử từ khóa khác hoặc bỏ bớt bộ lọc.", "Try another search or remove a filter."), color = p.muted) } }
                        items(results, key = { it.id }) { ex -> ExerciseTile(ex, "library_${ex.id}") { navigate("exercise:${ex.id}") } }
                    }
                    route == "assigned" -> {
                        val trainer = data.trainer
                        if (trainer == null) {
                            item { WorkoutPanel {
                                Icon(painterResource(R.drawable.home_icon_pt), null, Modifier.size(48.dp), tint = p.accent)
                                Text(tr("Một người đồng hành phù hợp", "Find your training partner"), style = MaterialTheme.typography.titleLarge)
                                Text(tr("Chưa có HLV phụ trách. Liên hệ quầy lễ tân để được tư vấn.", "No trainer assigned yet. Ask reception to find your match."), color = p.muted)
                                Button(onClick = { dialog = "trainer" }) { Text(tr("Tìm hiểu thêm", "Learn more")) }
                            } }
                        } else {
                            item { WorkoutPanel {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(Modifier.size(64.dp).background(p.tint, CircleShape), contentAlignment = Alignment.Center) { Text(trainer.initials, color = p.accent, style = MaterialTheme.typography.titleLarge) }
                                    Column(Modifier.weight(1f)) { Text(trainer.name, style = MaterialTheme.typography.titleLarge); Text(trainer.specialty.localized(), color = p.muted, style = MaterialTheme.typography.bodySmall) }
                                }
                                OutlinedButton(onClick = { navigate("chat") }, modifier = Modifier.fillMaxWidth().testTag("pt_chat")) { Text(tr("Trao đổi với HLV", "Message trainer")) }
                            } }
                            val plan = data.trainerPlan
                            if (plan == null) item { Text(tr("Giáo án của bạn sẽ xuất hiện tại đây.", "Your assigned plan will appear here."), color = p.muted) }
                            else {
                                item { Text(plan.title.localized(), style = MaterialTheme.typography.titleLarge); Text("${tr("Ngày giao", "Assigned")} ${date(plan.assignedAt)}", color = p.muted, style = MaterialTheme.typography.bodySmall) }
                                items(data.sessions.filter { it.id in plan.sessionIds }, key = { it.id }) { s ->
                                    data.workouts.firstOrNull { it.id == s.workoutId }?.let { w -> WorkoutTile(w, "${date(s.scheduledAt)} · ${statusLabel(s.status)}") { navigate("session:${w.id}") } }
                                }
                            }
                        }
                    }
                    route == "chat" -> {
                        item { Text(data.trainer?.name ?: tr("HLV của bạn", "Your trainer"), style = MaterialTheme.typography.titleLarge) }
                        items(data.messages, key = { it.id }) { message ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromTrainer) Arrangement.Start else Arrangement.End) {
                                Surface(Modifier.fillMaxWidth(.88f), color = if (message.fromTrainer) p.card else p.tint, shape = RoundedCornerShape(20.dp)) {
                                    Text(message.text.localized(), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        item { OutlinedTextField(value = "", onValueChange = {}, enabled = false, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Nhắn tin sẽ sớm có mặt", "Messaging is coming soon")) }) }
                    }
                    else -> {
                        item { WorkoutPanel {
                            Text(tr("Tuần này của bạn", "Your week"), style = MaterialTheme.typography.titleLarge)
                            Text("${goal.completed} / ${goal.target} ${tr("buổi đã hoàn thành", "workouts completed")}", color = p.accent)
                            LinearProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                (0..6).forEach { offset ->
                                    val day = Calendar.getInstance().apply { timeInMillis = week; add(Calendar.DAY_OF_YEAR, offset) }
                                    val hasSession = data.sessions.any { s -> Calendar.getInstance().apply { timeInMillis = s.scheduledAt }.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR) }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (language == "vi") listOf("T2","T3","T4","T5","T6","T7","CN")[offset] else listOf("M","T","W","T","F","S","S")[offset], color = p.muted, style = MaterialTheme.typography.labelSmall)
                                        Box(Modifier.padding(top = 6.dp).size(28.dp).background(if (hasSession) p.tint else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Text(day.get(Calendar.DAY_OF_MONTH).toString(), color = if (hasSession) p.accent else p.muted, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        } }
                        item { Text(tr("Lịch tập tuần này", "This week's sessions"), style = MaterialTheme.typography.titleLarge) }
                        items(data.sessions, key = { it.id }) { s ->
                            val w = data.workouts.first { it.id == s.workoutId }
                            WorkoutTile(w, "${date(s.scheduledAt)} · ${statusLabel(s.status)}") { navigate("session:${w.id}") }
                        }
                        item { OutlinedButton(onClick = onNutrition, modifier = Modifier.fillMaxWidth().testTag("plan_nutrition")) { Text(tr("Kế hoạch dinh dưỡng ›", "Nutrition Plan ›")) } }
                    }
                }
            }
        }
    }
    dialog?.let { selected ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(when { selected.startsWith("video:") -> tr("Video hướng dẫn", "Video guide"); selected == "finish" -> tr("Bạn đã tập rất tốt!", "Great effort!"); else -> tr("Sẵn sàng cho buổi tập", "Ready to train") }) },
            text = { Text(when { selected.startsWith("video:") -> tr("Video đang được chuẩn bị. Bạn có thể xem các bước hướng dẫn ngay bên dưới.", "The video is being prepared. Follow the written steps below for now."); selected == "finish" -> tr("Các bài đã tập vẫn được giữ. Buổi tập sẽ được tính vào mục tiêu tuần sau khi được xác nhận.", "Your exercise progress is kept. This session counts toward your weekly goal once confirmed."); selected == "trainer" -> tr("Lễ tân sẽ giúp bạn chọn HLV phù hợp với mục tiêu và lịch tập.", "Reception can help match a trainer to your goals and schedule."); else -> tr("Khởi động nhẹ và chọn mức tạ phù hợp trước khi bắt đầu.", "Warm up gently and choose a comfortable weight before starting.") }) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(tr("Đã hiểu", "Got it")) } })
    }
}

@Composable private fun FilterStrip(title: String, selected: String, values: List<Pair<String,String>>, onSelect: (String) -> Unit) {
    Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected.isEmpty(), { onSelect("") }, label = { Text(tr("Tất cả", "All")) })
        values.forEach { (id,label) -> FilterChip(selected == id, { onSelect(id) }, label = { Text(label) }) }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable private fun WorkoutDarkPreview() { ProfileAppearance(true,"vi") { MemberWorkoutScreen("hub",0,{},{}) } }
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable private fun WorkoutLightPreview() { ProfileAppearance(false,"en") { MemberWorkoutScreen("session:upper-body",0,{},{}) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable private fun TrainerEmptyPreview() {
    ProfileAppearance(false, "vi") {
        MemberWorkoutScreen("assigned", 0, {}, {},
            catalog = MockMemberWorkoutDataSource.snapshot(System.currentTimeMillis()).copy(trainer = null, trainerPlan = null))
    }
}
