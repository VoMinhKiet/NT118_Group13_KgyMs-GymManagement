@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kgyms.app.ui.member.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.DashboardLightPalette
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.ui.member.workout.WorkoutPanel
import com.kgyms.app.ui.member.workout.localized
import java.util.Locale
import kotlin.math.ceil

internal val PersonalCalendarIcon = ImageVector.Builder("PersonalCalendar", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
        moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 21f); lineTo(5f, 21f); close()
        moveTo(8f, 2f); lineTo(8f, 8f); moveTo(16f, 2f); lineTo(16f, 8f)
        moveTo(5f, 11f); lineTo(19f, 11f); moveTo(8f, 15f); lineTo(10f, 15f)
    }
}.build()

@Composable
internal fun planDayName(day: Int): String = tr(
    listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")[day],
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[day],
)

internal fun personalSessionTitle(session: PersonalPlanSession, catalog: WorkoutCatalog): WorkoutText {
    val muscles = catalog.exercises.filter { it.id in session.exerciseIds }.map { it.muscleGroup.en }.toSet()
    return when {
        "Legs" in muscles && muscles.any { it in listOf("Chest", "Back", "Arms") } -> WorkoutText("Toàn thân cân bằng", "Balanced full body")
        muscles.isNotEmpty() && muscles.all { it in listOf("Chest", "Back", "Arms") } -> WorkoutText("Sức mạnh thân trên", "Upper body strength")
        "Legs" in muscles -> WorkoutText("Thân dưới & cơ trung tâm", "Lower body & core")
        else -> WorkoutText("Cơ trung tâm & linh hoạt", "Core & mobility")
    }
}

/** Four rolling weeks preserve the exact start/end dates of existing phase-1 plans. */
@Composable
internal fun PersonalPlanWeek(
    plan: PersonalPlan, catalog: WorkoutCatalog,
    onOpen: ((String) -> Unit)? = null,
    onMove: ((PersonalPlanSession) -> Unit)? = null,
    onReplace: ((PersonalPlanSession) -> Unit)? = null,
    onEditExercises: ((PersonalPlanSession) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val palette = LocalProfilePalette.current
    val previousLabel = tr("Tuần trước", "Previous week")
    val nextLabel = tr("Tuần sau", "Next week")
    val today = PlanDates.date(System.currentTimeMillis(), plan.config.zoneId)
    var week by rememberSaveable(plan.id) {
        mutableIntStateOf((0..3).lastOrNull { PlanDates.add(plan.config.startDate, it * 7, plan.config.zoneId) <= today } ?: 0)
    }
    val dates = remember(plan.config, week) { PlanDates.weekDates(plan, week) }
    var selected by rememberSaveable(plan.id, week) {
        mutableStateOf(if (today in dates) today else dates.first())
    }
    var selectedId by rememberSaveable(plan.id, week) { mutableStateOf(plan.sessions.firstOrNull { it.date == selected }?.id) }
    LaunchedEffect(plan.sessions, week) {
        plan.sessions.firstOrNull { it.id == selectedId }?.let { if (it.date in dates) selected = it.date }
    }
    val session = plan.sessions.firstOrNull { it.date == selected }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { week-- }, enabled = week > 0 && enabled, modifier = Modifier.testTag("personal_week_previous")) {
                Text("‹", fontSize = 28.sp, modifier = Modifier.semantics { contentDescription = previousLabel })
            }
            Text("${PlanDates.display(dates.first())} – ${PlanDates.display(dates.last())}", Modifier.weight(1f),
                color = palette.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            IconButton(onClick = { week++ }, enabled = week < 3 && enabled, modifier = Modifier.testTag("personal_week_next")) {
                Text("›", fontSize = 28.sp, modifier = Modifier.semantics { contentDescription = nextLabel })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { index -> FilterChip(week == index, { week = index }, enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.tint, selectedLabelColor = palette.accent),
                label = { Text(tr("Tuần ${index + 1}", "Week ${index + 1}")) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("personal_week_$index")) }
        }
        PersonalPlanDays(dates, plan.config.zoneId, selected, today, plan.sessions.map { it.date }.toSet(), enabled) { date ->
            selected = date; selectedId = plan.sessions.firstOrNull { it.date == date }?.id
        }
        if (session == null) WorkoutPanel(Modifier.testTag("personal_selected_rest")) {
            Text(tr("Nghỉ ngơi & phục hồi", "Rest & recover"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Chọn một ngày có buổi tập để xem chi tiết.", "Choose a workout day to see your session."), color = palette.muted)
        } else WorkoutPanel(Modifier.testTag("personal_selected_session")) {
            Text(personalSessionTitle(session, catalog).localized(), style = MaterialTheme.typography.titleLarge)
            Text(PlanDates.display(session.date), Modifier.testTag("personal_selected_date"), color = palette.muted)
            Text("${plan.config.level.label.localized()} • ${session.exerciseIds.size} " + tr("bài tập", "exercises"), color = palette.accent)
            session.exerciseIds.forEachIndexed { index, id ->
                val exercise = catalog.exercises.firstOrNull { it.id == id }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = palette.accent, style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.weight(1f)) {
                        Text(exercise?.title?.localized() ?: tr("Bài tập chưa khả dụng", "Exercise unavailable"), style = MaterialTheme.typography.bodyMedium)
                        exercise?.let { Text("${it.sets} × ${it.reps}", color = palette.muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            onOpen?.let { open -> Button(onClick = { open(plan.runtimeSessionId(session)) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("personal_session_${session.id}")) {
                Text(tr("Bắt đầu tập", "Start workout"))
            } }
            onEditExercises?.let { edit ->
                OutlinedButton(onClick = { edit(session) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("personal_edit_exercises")) {
                    Text(tr("Chỉnh sửa bài tập", "Edit exercises"))
                }
            }
            if (onMove != null && onReplace != null) {
                val free = PlanDates.freeDates(plan, session, today)
                val largeText = LocalDensity.current.fontScale > 1.2f
                val changeDay: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedButton(onClick = { onMove(session) }, enabled = enabled && free.isNotEmpty(),
                        modifier = modifier.heightIn(min = 48.dp).testTag("personal_change_day")) { Text(tr("Đổi ngày", "Change day")) }
                }
                val replace: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedButton(onClick = { onReplace(session) }, enabled = enabled,
                        modifier = modifier.heightIn(min = 48.dp).testTag("personal_replace_workout")) { Text(tr("Thay buổi", "Replace workout")) }
                }
                BoxWithConstraints {
                    if (maxWidth < 300.dp || largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        changeDay(Modifier.fillMaxWidth()); replace(Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        changeDay(Modifier.weight(1f)); replace(Modifier.weight(1f))
                    }
                }
                if (free.isEmpty()) Text(tr("Tuần này không còn ngày trống để đổi.", "There are no free days in this week."), color = palette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Shared by preview and saved schedules. Week dates are supplied unchanged by PersonalPlanWeek. */
@Composable
internal fun PersonalPlanDays(dates: List<String>, zoneId: String, selected: String, today: String,
    scheduledDates: Set<String>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    val palette = LocalProfilePalette.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val dayStyle = MaterialTheme.typography.labelSmall
    val dateStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val labels = dates.map { planDayName(PlanDates.weekday(it, zoneId)) }
    val daySizes = labels.map { measurer.measure(it, dayStyle, maxLines = 1).size }
    val dateSizes = dates.map { measurer.measure(it.takeLast(2), dateStyle, maxLines = 1).size }
    val diameter = with(density) { maxOf(40.dp, dateSizes.maxOf { it.width }.toDp() + 12.dp, dateSizes.maxOf { it.height }.toDp() + 12.dp) }
    val target = with(density) { maxOf(48.dp, diameter, daySizes.maxOf { it.width }.toDp() + 4.dp) }
    val gap = 2.dp
    val minimumWidth = with(density) { (ceil(target.toPx()).toInt() * dates.size + gap.roundToPx() * (dates.size - 1)).toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("personal_calendar_strip")) {
        val rowWidth = maxOf(maxWidth, minimumWidth)
        Box(Modifier.horizontalScroll(rememberScrollState()).testTag("personal_calendar_scroll")) {
            Row(Modifier.width(rowWidth).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                dates.forEachIndexed { index, date ->
                    val active = date == selected
                    val scheduled = date in scheduledDates
                    val isToday = date == today
                    val description = "${labels[index]}, ${PlanDates.display(date)}, " +
                        (if (scheduled) tr("Có buổi tập", "Workout") else tr("Ngày nghỉ", "Rest")) +
                        if (isToday) tr(", Hôm nay", ", Today") else ""
                    Column(Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(date) })
                        .semantics { contentDescription = description }.testTag("personal_calendar_$date"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(labels[index], Modifier.testTag("personal_calendar_day_$date"), style = dayStyle, color = if (isToday) palette.accent else palette.muted, maxLines = 1, softWrap = false)
                        Surface(shape = CircleShape, color = if (active) DashboardLightPalette.accent else palette.card,
                            border = BorderStroke(if (isToday) 2.dp else 1.dp, if (isToday) MaterialTheme.colorScheme.secondary else if (active) DashboardLightPalette.accent else palette.border),
                            modifier = Modifier.size(diameter).testTag("personal_calendar_circle_$date")) {
                            Box(Modifier.fillMaxSize().testTag(if (isToday) "personal_calendar_today_$date" else "personal_calendar_content_$date"), contentAlignment = Alignment.Center) {
                                Text(date.takeLast(2), Modifier.testTag("personal_calendar_number_$date"), style = dateStyle,
                                    color = if (active) Color.White else palette.text, maxLines = 1, softWrap = false)
                            }
                        }
                        Box(Modifier.size(4.dp).then(if (scheduled) Modifier.background(palette.accent, CircleShape).testTag("personal_calendar_dot_$date") else Modifier))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonalStartDatePicker(initial: String, today: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val language = LocalProfileLanguage.current
    var selection by rememberSaveable { mutableStateOf<Long?>(PlanDates.pickerMillis(initial)) }
    val selectable = remember(today) { object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= PlanDates.pickerMillis(today)
        override fun isSelectableYear(year: Int) = year >= today.take(4).toInt()
    } }
    val state = remember(language, today) { DatePickerState(
        locale = Locale.forLanguageTag(if (language == "vi") "vi-VN" else "en"),
        initialSelectedDateMillis = selection, selectableDates = selectable,
    ) }
    LaunchedEffect(state.selectedDateMillis) { selection = state.selectedDateMillis }
    DatePickerDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("personal_date_picker"),
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onSelected(PlanDates.pickerDate(it)) } },
            enabled = state.selectedDateMillis?.let { selectable.isSelectableDate(it) } == true,
            modifier = Modifier.heightIn(min = 48.dp).testTag("personal_date_confirm")) { Text(tr("Chọn ngày", "Choose date")) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Hủy", "Cancel")) } }) {
        Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState()).testTag("personal_date_picker_scroll")) {
            // Bound the internal horizontal month pager inside the small-screen scroll container.
            DatePicker(state, modifier = Modifier.width(360.dp), showModeToggle = false,
                title = { Text(tr("Chọn ngày bắt đầu", "Choose start date"), Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.labelLarge) })
        }
    }
}
