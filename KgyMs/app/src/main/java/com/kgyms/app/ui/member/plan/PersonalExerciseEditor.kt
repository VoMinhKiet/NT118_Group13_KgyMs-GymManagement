@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kgyms.app.ui.member.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.DashboardLightPalette
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.ui.member.workout.localized
import com.kgyms.app.utils.ExerciseEditScope
import com.kgyms.app.utils.PersonalPlanEditor
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** One row of circles; narrow layouts scroll instead of shrinking text or touch targets. */
@Composable
internal fun PersonalWeekdayChips(days: List<Int>, onChange: (List<Int>) -> Unit) {
    val palette = LocalProfilePalette.current
    val density = LocalDensity.current
    val labels = (0..6).map { planDayName(it) }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val sizes = labels.map { measurer.measure(it, style, maxLines = 1).size }
    val diameter = with(density) { maxOf(40.dp, sizes.maxOf { it.width }.toDp() + 12.dp, sizes.maxOf { it.height }.toDp() + 12.dp) }
    val target = maxOf(48.dp, diameter)
    val gap = 2.dp
    // Budget rounded spacing in pixels so weight rounding never reduces a target below 48dp.
    val minimumRowWidth = with(density) { (ceil(target.toPx()).toInt() * 7 + gap.roundToPx() * 6).toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("personal_weekdays")) {
        val rowWidth = maxOf(maxWidth, minimumRowWidth)
        Box(Modifier.horizontalScroll(rememberScrollState()).testTag("personal_weekdays_scroll")) {
            Row(Modifier.width(rowWidth), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                (0..6).forEach { day ->
                    val selected = day in days
                    Box(Modifier.weight(1f).aspectRatio(1f)
                        .selectable(selected, role = Role.Checkbox, onClick = { onChange(if (selected) days - day else (days + day).sorted()) })
                        .testTag("personal_day_$day"), contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape, color = if (selected) DashboardLightPalette.accent else palette.card,
                            border = BorderStroke(1.dp, if (selected) DashboardLightPalette.accent else palette.border),
                            modifier = Modifier.size(diameter).testTag("personal_day_circle_$day")) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(labels[day], Modifier.testTag("personal_day_label_$day"), style = style,
                                    color = if (selected) Color.White else palette.text, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonalExerciseEditor(
    plan: PersonalPlan, session: PersonalPlanSession, catalog: WorkoutCatalog,
    busy: Boolean, onDismiss: () -> Unit, previewOnly: Boolean = false, modifier: Modifier = Modifier,
    onSave: suspend (List<String>, ExerciseEditScope) -> Boolean,
) {
    val palette = LocalProfilePalette.current
    val coroutine = rememberCoroutineScope()
    var ids by rememberSaveable(plan.uid, plan.id, session.id) { mutableStateOf(session.exerciseIds) }
    var scopeName by rememberSaveable(plan.uid, plan.id, session.id) { mutableStateOf(ExerciseEditScope.Session.name) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf(false) }
    var inFlight by remember { mutableStateOf(false) }
    val locked = busy || inFlight
    val suggested = remember(session, catalog, plan.config.level) { PersonalPlanEditor.suggestions(session, catalog, plan.config.level) }
    val canSave = remember(ids, catalog, plan.config.level) { runCatching { PersonalPlanEditor.validate(ids, catalog, plan.config.level) }.isSuccess }
    val choices = remember(ids, replacing, catalog, plan.config.level) { PersonalPlanEditor.choices(ids, replacing, catalog, plan.config.level) }
    val canShare = PersonalPlanEditor.canApplyToTemplate(session, catalog)
    val sameCount = plan.sessions.count { it.templateId == session.templateId }
    fun dismiss() { if (!locked) onDismiss() }
    ModalBottomSheet(onDismissRequest = { dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BackHandler { if (!locked) { if (picking) picking = false else onDismiss() } }
        Column(modifier.fillMaxWidth().fillMaxHeight(0.94f).testTag("personal_exercise_editor")) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("personal_editor_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (picking) tr("Chọn bài tập", "Choose an exercise") else tr("Chỉnh sửa bài tập", "Edit exercises"), style = MaterialTheme.typography.titleLarge)
                    Text(personalSessionTitle(session, catalog).localized() + " • " + PlanDates.display(session.date), color = palette.muted)
                    Text(tr("${ids.size} bài tập", "${ids.size} exercises"), Modifier.testTag("personal_editor_count"), color = palette.accent)
                    if (previewOnly) Text(tr("Chỉnh sửa bản xem trước. Nhấn Lưu lịch tập sau đó để lưu trên thiết bị.", "Editing the preview. Save the workout plan afterwards to keep it on this device."),
                        color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    if (error) Text(tr("Chưa thể lưu. Lịch có thể đã thay đổi; hãy thử lại hoặc mở lại buổi tập.", "Could not save. Your plan may have changed; retry or reopen the session."),
                        Modifier.testTag("personal_editor_error"), color = MaterialTheme.colorScheme.error)
                } }
                if (picking) {
                    if (choices.isEmpty()) item {
                        Text(tr("Không còn bài tập phù hợp để chọn. Bạn có thể giữ danh sách hiện tại.", "No eligible exercises remain. You can keep your current list."),
                            Modifier.testTag("personal_editor_no_choices"), color = palette.muted)
                    }
                    items(choices, key = { it.id }) { exercise ->
                        OutlinedButton(onClick = {
                            ids = if (replacing == null) PersonalPlanEditor.add(ids, exercise.id, catalog, plan.config.level)
                                else PersonalPlanEditor.replace(ids, checkNotNull(replacing), exercise.id, catalog, plan.config.level)
                            picking = false; error = false
                        }, enabled = !locked, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("personal_choose_${exercise.id}")) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(exercise.title.localized())
                                Text("${exercise.level.label.localized()} • ${exercise.muscleGroup.localized()}", style = MaterialTheme.typography.bodySmall)
                                Text("${exercise.sets} × ${exercise.reps}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    items(ids, key = { it }) { id ->
                        val exercise = catalog.exercises.firstOrNull { it.id == id }
                        PersonalExerciseRow(id, exercise, locked,
                            onSwap = { replacing = id; picking = true },
                            onRemove = { ids = PersonalPlanEditor.remove(ids, id); error = false })
                    }
                    item {
                        if (ids.isEmpty()) Text(tr("Cần ít nhất một bài để lưu buổi tập. Muốn nghỉ ngày này, hãy dùng phần quản lý ngày tập.", "Keep at least one exercise to save. To rest on this day, use the schedule controls."),
                            Modifier.testTag("personal_editor_empty"), color = palette.muted)
                        OutlinedButton(onClick = { replacing = null; picking = true }, enabled = !locked,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("personal_add_exercise")) {
                            Text("+", Modifier.padding(end = 8.dp), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Thêm bài tập", "Add exercise"))
                        }
                        TextButton(onClick = { suggested?.let { ids = it; error = false } }, enabled = !locked && suggested != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("personal_restore_exercises")) { Text(tr("Khôi phục đề xuất", "Restore suggestions")) }
                        if (suggested == null) Text(tr("Không còn đề xuất gốc đáng tin cậy để khôi phục.", "The original suggestions are no longer available."), color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("personal_scope_section"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = palette.border)
                            Text(tr("Áp dụng thay đổi", "Apply changes to"), style = MaterialTheme.typography.titleMedium)
                            PersonalScopeOption(scopeName == ExerciseEditScope.Session.name, !locked,
                                tr("Chỉ buổi tập này", "This session only"), "personal_scope_session") { scopeName = ExerciseEditScope.Session.name }
                            PersonalScopeOption(scopeName == ExerciseEditScope.SameTemplate.name, !locked && canShare,
                                tr("Các buổi cùng mẫu ($sameCount)", "Same-template sessions ($sameCount)"), "personal_scope_template") { scopeName = ExerciseEditScope.SameTemplate.name }
                            Text(if (canShare) tr("Chỉ trong phiên bản lịch này. Lịch đã lưu trước đó được giữ lại.", "Only within this plan version. Previous saved plans are retained.")
                                else tr("Không xác định được mẫu gốc. Chỉ có thể sửa buổi này.", "The original template is unknown. Only this session can be edited."),
                                Modifier.padding(horizontal = 4.dp, vertical = 8.dp).testTag("personal_scope_description"), color = palette.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.border)
            BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (picking) OutlinedButton(onClick = { picking = false }, enabled = !locked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("personal_picker_back")) { Text(tr("Quay lại danh sách", "Back to your list")) }
                else {
                    val save: @Composable (Modifier) -> Unit = { modifier -> Button(onClick = {
                        if (!locked) {
                            inFlight = true; error = false
                            coroutine.launch {
                                try { if (onSave(ids.toList(), ExerciseEditScope.valueOf(scopeName))) onDismiss() else error = true }
                                finally { inFlight = false }
                            }
                        }
                    }, enabled = !locked && canSave, modifier = modifier.heightIn(min = 52.dp).testTag("personal_editor_save")) {
                        Text(if (locked) tr("Đang lưu…", "Saving…") else tr("Lưu thay đổi", "Save changes"))
                    } }
                    val cancel: @Composable (Modifier) -> Unit = { modifier -> TextButton(onClick = { dismiss() }, enabled = !locked,
                        modifier = modifier.heightIn(min = 48.dp).testTag("personal_editor_cancel")) { Text(tr("Hủy", "Cancel")) } }
                    if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        save(Modifier.fillMaxWidth()); cancel(Modifier.fillMaxWidth())
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cancel(Modifier.weight(1f)); save(Modifier.weight(2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalExerciseRow(id: String, exercise: ExerciseUiModel?, locked: Boolean, onSwap: () -> Unit, onRemove: () -> Unit) {
    val palette = LocalProfilePalette.current
    Surface(shape = MaterialTheme.shapes.medium, color = palette.card, border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth().testTag("personal_editor_exercise_$id")) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            val details: @Composable (Modifier) -> Unit = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise?.title?.localized() ?: tr("Bài tập không còn khả dụng", "Exercise unavailable"), Modifier.testTag("personal_editor_title_$id"), style = MaterialTheme.typography.titleSmall)
                exercise?.let { Text("${it.sets} × ${it.reps}", Modifier.testTag("personal_editor_prescription_$id"), color = palette.muted, style = MaterialTheme.typography.bodySmall) }
            } }
            val actions: @Composable (Modifier) -> Unit = { modifier -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSwap, enabled = !locked, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("personal_swap_$id")) { Text(tr("Đổi", "Replace")) }
                OutlinedButton(onClick = onRemove, enabled = !locked, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.danger), border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("personal_remove_$id")) { Text(tr("Bỏ", "Remove")) }
            } }
            if (maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.2f) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                details(Modifier.weight(1f)); actions(Modifier.width(152.dp))
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details(Modifier.fillMaxWidth()); actions(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PersonalScopeOption(selected: Boolean, enabled: Boolean, label: String, tag: String, onClick: () -> Unit) {
    val palette = LocalProfilePalette.current
    Surface(shape = MaterialTheme.shapes.medium, color = if (selected) palette.tint else palette.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) palette.accent else palette.border),
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).testTag(tag)) {
        Row(Modifier.padding(12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected, onClick = null, enabled = enabled, modifier = Modifier.size(24.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (enabled) palette.text else palette.muted)
        }
    }
}
