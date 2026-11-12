package com.kgyms.app.ui.member.workout

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
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kgyms.app.R
import com.kgyms.app.utils.calculateWeeklyGoal
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.ui.member.profile.profileText as tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import com.kgyms.app.data.model.MemberWorkoutDataSource
import com.kgyms.app.data.mock.MockMemberWorkoutDataSource
import com.kgyms.app.data.model.VideoSourceUiModel
import com.kgyms.app.data.model.WorkoutCatalog
import com.kgyms.app.data.model.WorkoutCategory
import com.kgyms.app.data.model.WorkoutLevel
import com.kgyms.app.utils.confirmedRecords
import com.kgyms.app.utils.filterCatalogExercises
import com.kgyms.app.utils.workoutMonday

@Composable
internal fun MemberWorkoutScreen(entry: String, request: Int, onOverview: () -> Unit, onHome: () -> Unit,
    onNutrition: () -> Unit = {}, dataSource: MemberWorkoutDataSource? = null,
    catalog: WorkoutCatalog? = null, onPlayVideo: ((VideoSourceUiModel) -> Unit)? = null) {
    val owner = com.kgyms.app.ui.member.LocalMemberAccount.current?.uid?.let { "uid:$it" } ?: "demo"
    key(owner) {
        MemberDashboardAppearance {
            CompositionLocalProvider(LocalContentColor provides LocalProfilePalette.current.text) {
                MemberWorkoutContent(entry, request, onOverview, onHome, onNutrition,
                    dataSource ?: if (com.kgyms.app.ui.member.LocalMemberAccount.current == null) MockMemberWorkoutDataSource
                        else com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource,
                    catalog, onPlayVideo, owner)
            }
        }
    }
}

@Composable
private fun MemberWorkoutContent(entry: String, request: Int, onOverview: () -> Unit, onHome: () -> Unit,
    onNutrition: () -> Unit, dataSource: MemberWorkoutDataSource,
    catalog: WorkoutCatalog?, onPlayVideo: ((VideoSourceUiModel) -> Unit)?, owner: String) {
    var stack by rememberSaveable { mutableStateOf(listOf(entry)) }
    var handled by rememberSaveable { mutableIntStateOf(request) }
    val rehearsalSaver = remember(owner) {
        mapSaver(
            save = { state: WorkoutRehearsal -> mapOf("owner" to owner, "active" to ArrayList(state.active),
                "finished" to ArrayList(state.finished), "marks" to ArrayList(state.marks)) },
            restore = { saved ->
                if (saved["owner"] != owner) null else WorkoutRehearsal(
                    (saved["active"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    (saved["finished"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    (saved["marks"] as? List<*>)?.filterIsInstance<String>().orEmpty())
            },
        )
    }
    var rehearsal by rememberSaveable(owner, stateSaver = rehearsalSaver) { mutableStateOf(WorkoutRehearsal()) }
    var level by rememberSaveable { mutableStateOf(WorkoutLevel.Beginner.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterLevel by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scrollHolder = rememberSaveableStateHolder()
    LaunchedEffect(request) { if (handled != request) { stack = listOf(entry); handled = request; dialog = null } }
    val clock by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(60_000) } }
    val snapshot = remember(clock, dataSource, catalog, retry) { runCatching { catalog ?: dataSource.snapshot(clock) } }
    val data = remember(snapshot) { snapshot.getOrNull()?.safeCatalog() ?: WorkoutCatalog(emptyList(), emptyList(), emptyList(), null, null, emptyList()) }
    val language = LocalProfileLanguage.current
    val p = LocalProfilePalette.current
    val week = workoutMonday(clock)
    val goal = calculateWeeklyGoal(LocalMemberPreferences.current?.weeklyTarget ?: 6, data.confirmedRecords(), clock)
    val weeklySessions = data.weekSessions(clock)
    val route = stack.lastOrNull() ?: "plan"
    val navigate: (String) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else if (route != "plan") stack = listOf("plan") else onOverview() }
    BackHandler { back() }
    val destination = data.destination(route)
    val workout = destination.workout
    val exercise = data.exercises.firstOrNull { route == "exercise:${it.id}" }
    val invalidRoute = workout == null && exercise == null && route !in listOf("plan", "hub", "library", "assigned", "chat")
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
    Column(Modifier.fillMaxSize().background(p.background).safeDrawingPadding().imePadding().testTag("plan_content_workout")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp).testTag("workout_back")) { Text(tr("‹ Trở lại", "‹ Back")) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp).testTag("member_entry_home")) { Text(tr("Trang chủ", "Home")) }
        }
        scrollHolder.SaveableStateProvider(route) {
            LazyColumn(Modifier.fillMaxSize().testTag("workout_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(title, color = p.text, style = MaterialTheme.typography.headlineSmall)
                    Text(tr("Danh mục tham khảo • Tiến độ trên màn hình này chưa đồng bộ lịch sử.", "Reference catalog • Session progress here is not synced history."), color = p.muted, style = MaterialTheme.typography.bodySmall)
                    if (route == "plan") TextButton(onClick = { navigate("hub") }, modifier = Modifier.testTag("plan_explore")) {
                        Text(tr("Khám phá bài tập", "Explore workouts"))
                    }
                    if (workout == null && exercise == null && route != "chat") {
                        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("plan" to tr("Lịch của tôi", "My schedule"), "hub" to tr("Tập luyện", "Workouts"), "assigned" to tr("HLV của tôi", "My PT")).forEach { (id, label) ->
                                FilterChip(route == id, { stack = listOf(id) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("workout_$id"))
                            }
                        }
                    }
                }
                when {
                    snapshot.isFailure -> item { WorkoutPanel {
                        Text(tr("Không thể tải danh mục bài tập.", "Could not load the workout catalog."), Modifier.testTag("workout_source_failed"))
                        TextButton(onClick = { retry++ }, modifier = Modifier.testTag("workout_source_retry")) { Text(tr("Thử lại", "Retry")) }
                    } }
                    invalidRoute -> item { WorkoutPanel {
                        Text(tr("Nội dung không khả dụng", "Content unavailable"), Modifier.testTag("workout_unavailable"))
                        Text(tr("Bài tập hoặc buổi tập không tồn tại trong danh mục hiện tại.", "This exercise or session is missing from the current catalog."), color = p.muted)
                        TextButton(onClick = { stack = listOf("plan") }) { Text(tr("Về kế hoạch", "Back to Plan")) }
                    } }
                    workout != null -> {
                        val session = destination.session
                        val trackingId = workoutRunKey(route, week)
                        val active = trackingId in rehearsal.active
                        val finished = trackingId in rehearsal.finished
                        val exercises = workout.exerciseIds.mapNotNull { id -> data.exercises.firstOrNull { it.id == id } }
                        val done = exercises.count { rehearsal.isDone(trackingId, it.id) }
                        item { WorkoutHero(workout, workout.category.label.localized()) { dialog = "intro" } }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WorkoutBadge("≈ ${workout.calories} kcal")
                                WorkoutBadge(workout.level.label.localized())
                            }
                            session?.let { Text("${date(it.scheduledAt)} · ${statusLabel(it.status)}", Modifier.padding(top = 12.dp), color = p.muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        item {
                            Button(onClick = {
                                if (active) { rehearsal = rehearsal.finish(trackingId); dialog = "finish" }
                                else rehearsal = rehearsal.start(trackingId)
                            }, enabled = exercises.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("workout_start"), shape = RoundedCornerShape(18.dp)) {
                                Text(when { active -> tr("Kết thúc buổi tập", "Finish workout"); finished -> tr("Tập lại", "Start again"); else -> tr("Bắt đầu tập", "Start Workout") }, Modifier.padding(6.dp))
                            }
                            if (finished) Text(tr("Đã kết thúc trên thiết bị • Chưa đồng bộ", "Finished on this device • Not synced"), Modifier.padding(top = 12.dp).testTag("workout_finished"), color = p.accent)
                            if (active || finished) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$done / ${exercises.size} ${tr("bài đã tập", "exercises done")}", Modifier.testTag("workout_session_progress"), color = p.muted, style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(progress = { if (exercises.isEmpty()) 0f else done.toFloat() / exercises.size }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary, trackColor = p.border)
                            }
                            if (exercises.size != workout.exerciseCount || exercises.isEmpty()) Text(tr("Một số hướng dẫn chưa có. Chỉ các bài khả dụng được tính vào tiến độ trên thiết bị.", "Some guides are missing. Only available exercises count toward device progress."), Modifier.testTag("workout_missing_exercises"), color = p.muted)
                        }
                        exercises.chunked(2).forEachIndexed { round, group ->
                            item { Text(tr("Nhóm ${round + 1}", "Round ${round + 1}"), color = p.accent, style = MaterialTheme.typography.titleMedium) }
                            items(group, key = { "exercise:${it.id}" }) { ex ->
                                val id = ex.id
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ExerciseTile(ex, "exercise_$id") { navigate("exercise:$id") }
                                    if (active || finished) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(rehearsal.isDone(trackingId, id), { rehearsal = rehearsal.toggle(trackingId, id) }, enabled = active, modifier = Modifier.testTag("mark_$id"))
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
                            Surface(onClick = {
                                if (!exercise.video.sourceUrl.isNullOrBlank() && onPlayVideo != null) {
                                    runCatching { onPlayVideo(exercise.video) }.onFailure { dialog = "video:${exercise.id}" }
                                } else dialog = "video:${exercise.id}"
                            }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().testTag("exercise_play")) {
                                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                                    Image(painterResource(exercise.thumbnailRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Surface(shape = CircleShape, color = p.accent, shadowElevation = 2.dp) {
                                        Icon(painterResource(R.drawable.home_icon_play), tr("Phát hướng dẫn", "Play guide"), Modifier.padding(16.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                        item {
                            Text(exercise.title.localized(), style = MaterialTheme.typography.headlineSmall)
                            Text("${exercise.sets} × ${exercise.reps}  ·  ${exercise.level.label.localized()}", Modifier.padding(top = 8.dp), color = p.accent)
                            Text("${exercise.muscleGroup.localized()} · ${exercise.equipment.localized()}", Modifier.padding(top = 6.dp), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                            Text("${tr("Nghỉ giữa hiệp", "Rest between sets")} · ${exercise.restSeconds}s", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                        item { WorkoutPanel {
                            Text(tr("Cách thực hiện", "How to move"), style = MaterialTheme.typography.titleMedium)
                            if (exercise.instructions.isEmpty()) Text(tr("Hướng dẫn đang được cập nhật.", "Instructions are being updated."), color = p.muted)
                            exercise.instructions.forEachIndexed { index, text -> Text("${index + 1}. ${text.localized()}", style = MaterialTheme.typography.bodyMedium) }
                        } }
                        item { Text(exercise.safety.localized(), color = p.muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    route == "hub" -> {
                        item { WorkoutPanel { WorkoutGoalSummary(goal, data.historyAvailable) } }
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WorkoutLevel.entries.forEach { choice -> FilterChip(level == choice.name, { level = choice.name }, label = { Text(choice.label.localized()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("level_${choice.name}")) }
                            }
                        }
                        val selected = data.workouts.filter { it.level.name == level }
                        selected.firstOrNull()?.let { featured -> item { WorkoutHero(featured, tr("BÀI TẬP THAM KHẢO", "REFERENCE WORKOUT")) { navigate("workout:${featured.id}") } } }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Bài tập tham khảo", "Reference workouts"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { navigate("library") }, modifier = Modifier.heightIn(min = 48.dp).testTag("workout_library")) { Text(tr("Thư viện ›", "Library ›")) }
                            }
                        }
                        if (selected.isEmpty()) item { WorkoutPanel {
                            Text(tr("Chưa có buổi tập phù hợp", "No sessions at this level"), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Chọn trình độ khác hoặc khám phá thư viện.", "Choose another level or explore the library."), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                        } }
                        items(selected, key = { it.id }) { w -> WorkoutTile(w) { navigate("workout:${w.id}") } }
                    }
                    route == "library" -> {
                        item {
                            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(tr("Tìm bài tập", "Search exercises")) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("workout_search"))
                            FilterStrip(tr("Trình độ", "Level"), filterLevel, WorkoutLevel.entries.map { it.name to it.label.localized() }) { filterLevel = it }
                            FilterStrip(tr("Nhóm cơ", "Muscle"), muscle, data.exercises.map { it.muscleGroup }.distinctBy { it.en }.map { it.en to it.localized() }) { muscle = it }
                            FilterStrip(tr("Loại bài", "Type"), category, WorkoutCategory.entries.map { it.name to it.label.localized() }) { category = it }
                            TextButton(onClick = { query = ""; filterLevel = ""; muscle = ""; category = "" }, modifier = Modifier.heightIn(min = 48.dp).testTag("workout_clear_filters")) { Text(tr("Xóa bộ lọc", "Clear filters")) }
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
                                    data.workouts.firstOrNull { it.id == s.workoutId }?.let { w -> WorkoutTile(w, date(s.scheduledAt), tag = "scheduled_${s.id}", status = s.status) { navigate("session:${s.id}") } }
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
                            WorkoutGoalSummary(goal, data.historyAvailable)
                            HorizontalDivider(color = p.border)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (0..6).forEach { offset ->
                                    val day = Calendar.getInstance().apply { timeInMillis = week; add(Calendar.DAY_OF_YEAR, offset) }
                                    val nextDay = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                                    val hasSession = weeklySessions.any { it.scheduledAt >= day.timeInMillis && it.scheduledAt < nextDay }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (language == "vi") listOf("T2","T3","T4","T5","T6","T7","CN")[offset] else listOf("M","T","W","T","F","S","S")[offset], color = p.muted, style = MaterialTheme.typography.labelSmall)
                                        Box(Modifier.padding(top = 6.dp).size(30.dp).background(if (hasSession) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(day.get(Calendar.DAY_OF_MONTH).toString(), color = if (hasSession) MaterialTheme.colorScheme.onSecondaryContainer else p.muted, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        } }
                        item { Text(tr("Lịch tập tuần này", "This week's sessions"), style = MaterialTheme.typography.titleMedium) }
                        if (weeklySessions.isEmpty()) item { WorkoutPanel {
                            Text(tr("Chưa có lịch tập", "No sessions scheduled"), style = MaterialTheme.typography.titleMedium)
                            Text(if (data.historyAvailable) tr("Các buổi tập sẽ xuất hiện tại đây khi có lịch.", "Your sessions will appear here when scheduled.") else tr("Chưa kết nối dữ liệu lịch tập. Bạn có thể tập theo danh mục tham khảo.", "Schedule data is not connected. You can use the reference workouts."), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                        } }
                        items(weeklySessions, key = { it.id }) { s ->
                            val w = data.workouts.firstOrNull { it.id == s.workoutId }
                            if (w != null) WorkoutTile(w, date(s.scheduledAt), tag = "scheduled_${s.id}", status = s.status) { navigate("session:${s.id}") }
                            else WorkoutPanel { Text(tr("Buổi tập không khả dụng", "Session unavailable")); Text(date(s.scheduledAt), color = p.muted) }
                        }
                        item { OutlinedButton(onClick = onNutrition, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan_nutrition")) { Text(tr("Kế hoạch dinh dưỡng ›", "Nutrition Plan ›")) } }
                    }
                }
            }
        }
    }
    dialog?.let { selected ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(when { selected.startsWith("video:") -> tr("Video hướng dẫn", "Video guide"); selected == "finish" -> tr("Bạn đã tập rất tốt!", "Great effort!"); else -> tr("Sẵn sàng cho buổi tập", "Ready to train") }) },
            text = { Text(when { selected.startsWith("video:") -> tr("Video hướng dẫn đang được cập nhật. Bạn có thể tập theo hướng dẫn bằng chữ bên dưới.", "Video guide is being updated. Follow the written steps below for now."); selected == "finish" -> tr("Đã kết thúc buổi tập trên thiết bị. Tiến độ được giữ khi khôi phục màn hình; chưa đồng bộ Firebase và không cộng vào mục tiêu tuần.", "Workout finished on this device. Progress survives screen restoration; it is not synced to Firebase and does not count toward your weekly goal."); selected == "trainer" -> tr("Lễ tân sẽ giúp bạn chọn HLV phù hợp với mục tiêu và lịch tập.", "Reception can help match a trainer to your goals and schedule."); else -> tr("Khởi động nhẹ và chọn mức tạ phù hợp trước khi bắt đầu.", "Warm up gently and choose a comfortable weight before starting.") }) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(tr("Đã hiểu", "Got it")) } })
    }
}

@Composable private fun FilterStrip(title: String, selected: String, values: List<Pair<String,String>>, onSelect: (String) -> Unit) {
    Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected.isEmpty(), { onSelect("") }, label = { Text(tr("Tất cả", "All")) }, modifier = Modifier.heightIn(min = 48.dp))
        values.forEach { (id,label) -> FilterChip(selected == id, { onSelect(id) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable private fun WorkoutDarkPreview() { ProfileAppearance(true,"vi") { MemberWorkoutScreen("hub",0,{},{}) } }
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable private fun WorkoutLightPreview() { ProfileAppearance(false,"en") { MemberWorkoutScreen("workout:upper-body",0,{},{}) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable private fun TrainerEmptyPreview() {
    ProfileAppearance(false, "vi") {
        MemberWorkoutScreen("assigned", 0, {}, {},
            catalog = MockMemberWorkoutDataSource.snapshot(System.currentTimeMillis()).copy(trainer = null, trainerPlan = null))
    }
}

@Preview(name = "Plan compact light", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable private fun PlanCompactPreview() { ProfileAppearance(false, "vi") { MemberWorkoutScreen("plan", 0, {}, {}) } }
@Preview(name = "Library dark English", widthDp = 390, heightDp = 844)
@Composable private fun LibraryDarkPreview() { ProfileAppearance(true, "en") { MemberWorkoutScreen("library", 0, {}, {}) } }
