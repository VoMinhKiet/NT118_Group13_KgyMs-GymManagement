@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kgyms.app.ui.member.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.local.PersonalPlanCodec
import com.kgyms.app.data.model.*
import com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource
import com.kgyms.app.data.repository.PersonalPlanStorage
import com.kgyms.app.data.repository.SyncedPersonalPlanRepository
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.ui.member.progress.LocalWorkoutHistory
import com.kgyms.app.ui.member.workout.WorkoutPanel
import com.kgyms.app.ui.member.workout.localized
import com.kgyms.app.utils.PersonalPlanGenerator
import com.kgyms.app.utils.PersonalPlanEditor
import kotlinx.coroutines.launch
import java.util.TimeZone

@Composable
private fun storageNotice(state: PersonalPlanState): String? = if (state.repository is SyncedPersonalPlanRepository) when (state.syncStatus) {
    PersonalPlanSyncStatus.Device, PersonalPlanSyncStatus.Syncing, PersonalPlanSyncStatus.Synced -> null
    PersonalPlanSyncStatus.Pending -> if(state.archive.plans.isNotEmpty())tr("Lịch đã được giữ trên máy, sẽ lưu vào tài khoản khi có kết nối.", "Your plan is kept on this device and will be saved to your account when connected.") else null
    PersonalPlanSyncStatus.Failed, PersonalPlanSyncStatus.AccessDenied -> tr("Chưa lưu được lịch vào tài khoản. Lịch trên máy vẫn được giữ lại.", "Could not save your plan to your account. Your device copy is safe.")
    PersonalPlanSyncStatus.Conflict -> tr("Có hai lựa chọn lịch khác nhau. Các lịch vẫn được giữ nguyên.", "There are different plan choices. All your plans are retained.")
} else when(state.repository.storage) {
    PersonalPlanStorage.Memory -> tr("Bản xem thử chỉ giữ trong lần mở này.", "This preview is kept for this visit only.")
    PersonalPlanStorage.Device -> null
}

@Composable
private fun PersonalPlanReadNotice(state: PersonalPlanState, showRetry: Boolean = true) {
    val scope=rememberCoroutineScope()
    val loading=state.syncStatus in listOf(PersonalPlanSyncStatus.Device,PersonalPlanSyncStatus.Syncing)
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(if(loading)tr("Đang tải lịch tập…", "Loading your plan…")
        else tr("Chưa tải được lịch tập của bạn. Các lịch đã lưu vẫn được giữ lại.", "Could not load your plan. Your saved plans are safe."),
        Modifier.testTag("personal_remote_unverified"),style=MaterialTheme.typography.bodySmall)
    if(showRetry && !loading)TextButton(onClick={scope.launch{state.synchronize()}},modifier=Modifier.testTag("personal_sync_retry")){Text(tr("Thử lại","Retry"))}
}

@Composable
private fun PersonalPlanSyncControls(state: PersonalPlanState) {
    if (state.repository !is SyncedPersonalPlanRepository) return
    val scope = rememberCoroutineScope()
    if(!state.remoteNotChecked && !state.activeSyncConflict)storageNotice(state)?.let { Text(it, Modifier.testTag("personal_sync_status"), style = MaterialTheme.typography.bodySmall) }
    if (!state.activeSyncConflict && (state.syncStatus in listOf(PersonalPlanSyncStatus.Failed,PersonalPlanSyncStatus.AccessDenied,PersonalPlanSyncStatus.Conflict) ||
            state.remoteNotChecked && state.syncStatus==PersonalPlanSyncStatus.Pending)) TextButton(onClick = { scope.launch { state.synchronize() } }, modifier = Modifier.heightIn(min=48.dp).testTag("personal_sync_retry")) {
        Text(tr("Thử lại", "Retry"))
    }
    if (state.activeSyncConflict) {
        Text(tr("Bạn đã chọn lịch khác trên một thiết bị khác. Hãy chọn lịch muốn tiếp tục sử dụng; các lịch trước vẫn được giữ lại.", "You chose another plan on a different device. Choose which to continue using; previous plans are retained."))
        state.active?.let { plan -> OutlinedButton(onClick = { scope.launch { state.selectActive(plan.id); state.synchronize() } }, modifier = Modifier.heightIn(min=48.dp).testTag("personal_sync_keep_device")) { Text(tr("Giữ lịch đang dùng", "Keep current plan")) } }
        state.remoteActiveId?.let { id -> OutlinedButton(onClick = { scope.launch { state.selectActive(id); state.synchronize() } }, modifier = Modifier.heightIn(min=48.dp).testTag("personal_sync_use_remote")) {
            Text(tr("Dùng lịch mới nhận", "Use the other plan"))
        } }
    }
}

@Composable
internal fun PersonalPlanToday(now: Long, onCreate: () -> Unit, onOpen: (String) -> Unit, onView: () -> Unit = {}) {
    val state = LocalPersonalPlans.current ?: return
    val reference = remember { ReferenceMemberWorkoutDataSource.snapshot(now) }
    val scope = rememberCoroutineScope()
    val palette = LocalProfilePalette.current
    val history = LocalWorkoutHistory.current
    val largeText = LocalDensity.current.fontScale > 1.2f
    WorkoutPanel(Modifier.testTag("personal_today")) {
        Text(tr("Buổi tập hôm nay", "Today's workout"), style = MaterialTheme.typography.titleMedium)
        when {
            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("personal_loading"))
            state.failed -> {
                Text(tr("Không thể tải lịch cá nhân.", "Could not load your plan."), Modifier.testTag("personal_error"), color = palette.muted)
                TextButton(onClick = { scope.launch { state.load() } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Thử lại", "Retry")) }
            }
            state.active == null && state.archive.plans.isNotEmpty() -> {
                Text(tr("Chưa chọn lịch đang dùng", "No active plan selected"))
                TextButton(onClick = onView) { Text(tr("Xem lịch", "View plans")) }
            }
            state.active == null -> {
                if (state.remoteNotChecked) PersonalPlanReadNotice(state)
                else Text(tr("Bạn chưa có lịch tập", "You have no workout plan"), color = palette.muted)
                Button(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp).testTag("personal_today_create")) { Text(tr("Tạo lịch tập", "Create plan")) }
            }
            else -> {
                val plan = checkNotNull(state.active)
                val today = PlanDates.date(now, plan.config.zoneId)
                val session = plan.sessions.firstOrNull { it.date == today }
                val activeDraft = session?.let { history?.archive?.activeDraft(it.id) }
                val completed = session?.let { history?.archive?.completed(it.id, now) }
                val historyReady = history == null || (!history.loading && !history.failed)
                val nextSession = plan.sessions.filter { it.date > today }.minByOrNull { it.date }
                when {
                    today < plan.config.startDate -> Text(tr("Lịch bắt đầu ${PlanDates.display(plan.config.startDate)}", "Plan starts ${PlanDates.display(plan.config.startDate)}"), Modifier.testTag("personal_not_started"))
                    today >= PlanDates.add(plan.config.startDate, 28, plan.config.zoneId) -> {
                        Text(tr("Lịch 4 tuần đã kết thúc", "Your 4-week plan has ended"), Modifier.testTag("personal_expired"))
                        TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Tạo lịch mới", "Create a new plan")) }
                    }
                    session != null -> {
                        Text(personalSessionTitle(session, reference).localized(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("personal_today_exercises"))
                        Text("${PlanDates.display(session.date)} • ${session.exerciseIds.size} " + tr("bài tập", "exercises"), color = palette.muted)
                        if (history?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("personal_today_history_loading"))
                        else if (history?.failed == true) Text(tr("Chưa thể tải kết quả tập luyện.", "Workout results are unavailable."), color = palette.muted)
                        else if (activeDraft != null) Text(tr("Đang tập", "In progress"), Modifier.testTag("personal_today_active"), color = palette.accent)
                        else if (completed != null) Text(tr("Đã hoàn thành", "Completed"), Modifier.testTag("personal_today_completed"), color = palette.accent)
                    }
                    else -> Text(tr("Hôm nay là ngày nghỉ", "Today is a rest day"), Modifier.testTag("personal_rest"))
                }
                if (session == null && nextSession != null) Text(tr("Buổi tiếp theo: ${PlanDates.display(nextSession.date)}", "Next session: ${PlanDates.display(nextSession.date)}"), color = palette.muted)
                val start: @Composable (Modifier) -> Unit = { modifier ->
                    if (session != null) Button(onClick = { onOpen(plan.runtimeSessionId(session)) }, enabled = historyReady,
                        modifier = modifier.heightIn(min = 48.dp).testTag("personal_today_start")) {
                        Text(when { activeDraft != null -> tr("Tiếp tục tập", "Continue"); completed != null -> tr("Tập lại", "Start again"); else -> tr("Bắt đầu tập", "Start workout") })
                    }
                }
                val view: @Composable (Modifier) -> Unit = { modifier ->
                    TextButton(onClick = onView, modifier = modifier.heightIn(min = 48.dp).testTag("personal_today_view")) { Text(tr("Xem lịch", "View plan")) }
                }
                if (largeText) Column { start(Modifier.fillMaxWidth()); view(Modifier.fillMaxWidth()) }
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session != null) start(Modifier.weight(1f))
                    view(if (session != null) Modifier.weight(1f) else Modifier)
                }
            }
        }
    }
}

@Composable
internal fun PersonalPlanOverview(onCreate: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalPersonalPlans.current ?: return
    val catalog = remember { ReferenceMemberWorkoutDataSource.snapshot(System.currentTimeMillis()) }
    var selected by rememberSaveable(state.uid, state.active?.id) { mutableStateOf(state.active?.id) }
    var versions by rememberSaveable { mutableStateOf(false) }
    var editingPlan by rememberSaveable(state.uid) { mutableStateOf<String?>(null) }
    var editingSession by rememberSaveable(state.uid) { mutableStateOf<String?>(null) }
    var expectedActive by rememberSaveable(state.uid) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val palette = LocalProfilePalette.current
    Column(Modifier.fillMaxWidth().testTag("personal_plan_overview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Lịch tập của tôi", "My workout plan"), style = MaterialTheme.typography.titleLarge)
        when {
            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.failed -> {
                Text(tr("Không thể tải lịch. Hãy thử lại.", "Could not load your plans. Please retry."), color = palette.muted)
                TextButton(onClick = { scope.launch { state.load() } }) { Text(tr("Thử lại", "Retry")) }
            }
            else -> {
                if (state.saveNotice) Surface(color = palette.tint, shape = MaterialTheme.shapes.medium) {
                    Text(tr("Đã lưu lịch tập", "Workout plan saved"),
                        Modifier.padding(12.dp).testTag("personal_saved_notice"), color = palette.accent)
                }
                if (state.archive.plans.isEmpty()) {
                    WorkoutPanel {
                        if (state.remoteNotChecked) PersonalPlanReadNotice(state,showRetry=false)
                        Text(tr("Chọn ngày phù hợp với bạn", "Make room for your workouts"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Tạo lịch 4 tuần theo trình độ và ngày bạn muốn tập.", "Build four weeks around your level and preferred days."), color = palette.muted)
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("personal_create")) { Text(tr("Tạo lịch tập", "Create plan")) }
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp).testTag("personal_create")) { Text(tr("Tạo lịch mới", "New plan")) }
                        if (state.archive.plans.size > 1) TextButton(onClick = { versions = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("personal_versions")) { Text(tr("Lịch trước", "Previous plans")) }
                    }
                    val plan = state.archive.plans.firstOrNull { it.id == selected } ?: state.active ?: state.archive.plans.lastOrNull()
                    if (plan != null) {
                        state.versionNumber(plan.id)?.let { number ->
                            Text(tr("Lịch tập $number", "Workout plan $number"), Modifier.testTag("personal_plan_name"), style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${plan.config.level.label.localized()} • ${plan.config.sessionsPerWeek} " + tr("buổi/tuần", "sessions/week"), color = palette.accent)
                        PersonalPlanWeek(plan, catalog, onOpen = onOpen, enabled = !state.saving,
                            onEditExercises = { editingPlan = plan.id; editingSession = it.id; expectedActive = state.archive.activeId })
                        if (state.repository is SyncedPersonalPlanRepository && plan.id != state.active?.id) OutlinedButton(onClick = { scope.launch { state.selectActive(plan.id); state.synchronize() } }, modifier = Modifier.testTag("personal_activate_plan")) {
                            Text(tr("Dùng lịch này", "Use this plan"))
                        }
                    }
                }
                if (state.repository is SyncedPersonalPlanRepository) Box(Modifier.testTag("personal_local_status")) { Column { PersonalPlanSyncControls(state) } }
                else storageNotice(state)?.let { Text(it, Modifier.testTag("personal_local_status"), color = palette.muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    val original = state.archive.plans.firstOrNull { it.id == editingPlan }
    val editedSession = original?.sessions?.firstOrNull { it.id == editingSession }
    if (original != null && editedSession != null) PersonalExerciseEditor(original, editedSession, catalog,
        busy = state.saving, onDismiss = { editingPlan = null; editingSession = null },
        onSave = { ids, editScope -> state.saveExercises(original, editedSession.id, ids, editScope, catalog, expectedActive) })
    if (versions) ModalBottomSheet(onDismissRequest = { versions = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Các phiên bản lịch", "Your plans"), style = MaterialTheme.typography.titleLarge)
            state.archive.plans.forEachIndexed { index, plan ->
                val number = state.versionNumber(plan.id)
                OutlinedButton(onClick = { selected = plan.id; versions = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("personal_version_$index"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (number != null) Text(tr("Lịch tập $number", "Workout plan $number"), Modifier.testTag("personal_version_name_$index"), style = MaterialTheme.typography.titleMedium)
                        Text(PlanDates.display(plan.config.startDate) + if (plan.id == state.active?.id) tr(" • Đang dùng", " • Active") else "",
                            style = MaterialTheme.typography.bodySmall, color = palette.muted)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonalPlanSetupScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val state = LocalPersonalPlans.current ?: return
    val scope = rememberCoroutineScope()
    val catalog = remember { ReferenceMemberWorkoutDataSource.snapshot(System.currentTimeMillis()) }
    val zone = rememberSaveable { TimeZone.getDefault().id }
    var level by rememberSaveable { mutableStateOf(WorkoutLevel.Beginner.name) }
    var days by rememberSaveable { mutableStateOf(listOf(0, 2, 4)) }
    var start by rememberSaveable { mutableStateOf(PlanDates.date(System.currentTimeMillis(), zone)) }
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var completed by rememberSaveable { mutableStateOf(false) }
    val draft = remember(draftJson) { draftJson?.let { PersonalPlanCodec.decode(it, state.uid).active } }
    fun setDraft(value: PersonalPlan?) { completed = false; draftJson = value?.let { PersonalPlanCodec.encode(PersonalPlanArchive(it.id, listOf(it)), state.uid) } }
    var error by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(state.uid) { mutableStateOf<String?>(null) }
    val back = { if (!state.saving) { if (draft != null) { setDraft(null); error = false } else onBack() } }
    BackHandler { back() }
    MemberDashboardAppearance {
        val palette = LocalProfilePalette.current
        val today = PlanDates.date(System.currentTimeMillis(), zone)
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("personal_setup"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    TextButton(onClick = back, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp).testTag("personal_setup_back")) { Text(tr("‹ Quay lại", "‹ Back")) }
                    Text(if (draft == null) tr("Tạo lịch tập của tôi", "Create my workout plan") else tr("Lịch tập 4 tuần", "Your 4-week plan"), style = MaterialTheme.typography.headlineSmall)
                    if (draft == null) Text(tr("Chọn ngày và trình độ, KgyMs sẽ gợi ý bài tập phù hợp.", "Choose your days and level. KgyMs will suggest suitable workouts."), color = palette.muted)
                    if (error) Text(tr("Chưa thể lưu thay đổi. Hãy thử lại hoặc kiểm tra lịch đã chọn.", "Your changes could not be saved. Please retry or check your plan."), Modifier.testTag("personal_validation_error"), color = MaterialTheme.colorScheme.error)
                }
                if (draft == null) {
                    item {
                        Text(tr("Trình độ", "Your level"), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WorkoutLevel.entries.forEach { value -> FilterChip(level == value.name, { level = value.name },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.tint, selectedLabelColor = palette.accent),
                                label = { Text(value.label.localized()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("personal_level_${value.name}")) }
                        }
                    }
                    item {
                        Surface(onClick = { datePicker = true }, shape = MaterialTheme.shapes.large, color = palette.card,
                            modifier = Modifier.fillMaxWidth().testTag("personal_start_date")) {
                            Row(Modifier.padding(16.dp).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(PersonalCalendarIcon, null, Modifier.size(28.dp), tint = palette.accent)
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Ngày bắt đầu", "Start date"), color = palette.muted, style = MaterialTheme.typography.labelLarge)
                                    Text(PlanDates.display(start), Modifier.testTag("personal_start_date_value"), style = MaterialTheme.typography.titleMedium)
                                }
                                Text("›", color = palette.accent)
                            }
                        }
                    }
                    item {
                        Text(tr("Bạn muốn tập những ngày nào?", "Which days work for you?"), style = MaterialTheme.typography.titleMedium)
                        PersonalWeekdayChips(days) { days = it }
                        Text(tr("${days.size} buổi/tuần", "${days.size} sessions/week"), Modifier.testTag("personal_days_count"), color = palette.accent, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { days = PersonalPlanGenerator.weekdays(days.size.takeIf { it in 1..7 } ?: 3) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("personal_suggest")) { Text(tr("Gợi ý lịch tập", "Suggest days")) }
                        if (days.size >= 6) Surface(color = palette.tint, shape = MaterialTheme.shapes.medium) {
                            Text(tr("Bạn chọn tập gần như mỗi ngày. Hãy xen kẽ nhóm cơ và dành thời gian nghỉ ngơi, phục hồi.", "You're training most days. Alternate muscle groups and make time for rest and recovery."),
                                Modifier.padding(12.dp).testTag("personal_recovery_hint"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    item {
                        Text("${draft.sessions.size} " + tr("buổi • 4 tuần", "sessions • 4 weeks"), Modifier.testTag("personal_preview_count"), color = palette.accent)
                        if (draft.repeated) Text(tr("Thư viện còn ít, một số bài tập sẽ lặp lại.", "Some exercises will repeat while the library is small."),
                            Modifier.testTag("personal_repeat_notice"), color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        PersonalPlanWeek(draft, catalog, enabled = !state.saving,
                            onMove = { moving = it.id; error = false }, onReplace = { replacing = it.id; error = false },
                            onEditExercises = { editing = it.id })
                    }
                }
            }
            HorizontalDivider(color = palette.border)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (draft == null) Button(onClick = {
                    runCatching { PersonalPlanGenerator.generate(state.uid, PersonalPlanConfig(WorkoutLevel.valueOf(level), days.size, start, zone, days), catalog, System.currentTimeMillis()) }
                        .onSuccess { setDraft(it); error = false }.onFailure { error = true }
                }, enabled = days.isNotEmpty() && start >= today && !state.loading && !state.failed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("personal_preview")) { Text(tr("Xem lịch 4 tuần", "Preview 4 weeks")) }
                else Button(onClick = { scope.launch { if (state.save(draft, catalog, System.currentTimeMillis())) { completed = true; onSaved() } else error = true } },
                    enabled = !completed && !state.saving && !state.loading && !state.failed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("personal_save")) {
                    Text(if (completed) tr("Đã lưu", "Saved") else if (state.saving) tr("Đang lưu…", "Saving…") else tr("Lưu lịch tập", "Save workout plan"))
                }
                if(state.repository.storage==PersonalPlanStorage.Memory)storageNotice(state)?.let {Text(it,color=palette.muted,style=MaterialTheme.typography.labelSmall)}
            }
        }
        val editingSession = draft?.sessions?.firstOrNull { it.id == editing }
        if (draft != null && editingSession != null) PersonalExerciseEditor(draft, editingSession, catalog,
            busy = state.saving, onDismiss = { editing = null }, previewOnly = true, onSave = { ids, editScope ->
                runCatching { PersonalPlanEditor.apply(draft, editingSession.id, ids, editScope, catalog) }
                    .fold(onSuccess = { setDraft(it); true }, onFailure = { false })
            })
        if (datePicker) PersonalStartDatePicker(start, today, onDismiss = { datePicker = false }, onSelected = { start = it; datePicker = false; error = false })
        val moveSession = draft?.sessions?.firstOrNull { it.id == moving }
        if (draft != null && moveSession != null) ModalBottomSheet(onDismissRequest = { moving = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Chọn ngày trống", "Choose a free day"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Trong cùng tuần, không thay đổi các buổi khác.", "In the same week, keeping your other sessions unchanged."), color = palette.muted)
                PlanDates.freeDates(draft, moveSession, PlanDates.date(System.currentTimeMillis(), draft.config.zoneId)).forEach { date ->
                    OutlinedButton(onClick = {
                        runCatching { PersonalPlanGenerator.move(draft, moveSession.id, date, catalog, System.currentTimeMillis()) }
                            .onSuccess { setDraft(it); moving = null; error = false }.onFailure { error = true }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("personal_move_$date")) {
                        Text(planDayName(PlanDates.weekday(date, draft.config.zoneId)) + " • " + PlanDates.display(date))
                    }
                }
                if (error) Text(tr("Ngày này không còn khả dụng.", "This date is no longer available."), color = MaterialTheme.colorScheme.error)
            }
        }
        val replaceSession = draft?.sessions?.firstOrNull { it.id == replacing }
        if (draft != null && replaceSession != null) ModalBottomSheet(onDismissRequest = { replacing = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Chọn buổi tập thay thế", "Choose a replacement"), style = MaterialTheme.typography.titleLarge)
                PersonalPlanGenerator.candidates(catalog, draft.config.level).forEach { workout ->
                    val option = replaceSession.copy(templateId = workout.id, exerciseIds = workout.exerciseIds)
                    OutlinedButton(onClick = {
                        runCatching { PersonalPlanGenerator.replace(draft, replaceSession.id, workout.id, catalog, System.currentTimeMillis()) }
                            .onSuccess { setDraft(it); replacing = null; error = false }.onFailure { error = true }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("personal_replace_${workout.id}")) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(personalSessionTitle(option, catalog).localized())
                            Text("${draft.config.level.label.localized()} • ${workout.exerciseIds.size} " + tr("bài tập", "exercises"), style = MaterialTheme.typography.bodySmall)
                            val language = com.kgyms.app.ui.member.profile.LocalProfileLanguage.current
                            Text(workout.exerciseIds.joinToString(" • ") { id -> catalog.exercises.firstOrNull { it.id == id }?.title?.inLanguage(language).orEmpty() }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
