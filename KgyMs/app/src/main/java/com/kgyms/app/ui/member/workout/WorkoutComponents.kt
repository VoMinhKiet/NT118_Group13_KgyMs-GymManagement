package com.kgyms.app.ui.member.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kgyms.app.R
import com.kgyms.app.data.model.HomeWeeklyGoal
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.data.model.ExerciseUiModel
import com.kgyms.app.data.model.SessionStatus
import com.kgyms.app.data.model.WorkoutSummaryUiModel
import com.kgyms.app.data.model.WorkoutText

@Composable internal fun WorkoutText.localized() = inLanguage(LocalProfileLanguage.current)

@Composable
internal fun WorkoutPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProfilePalette.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = p.card,
        contentColor = p.text, border = BorderStroke(1.dp, p.border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** A projection of the existing goal calculation, never another goal state/source. */
@Composable
internal fun WorkoutGoalSummary(goal: HomeWeeklyGoal, available: Boolean = true) {
    val p = LocalProfilePalette.current
    if (!available) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("workout_history_unavailable")) {
            Text(tr("Mục tiêu tuần", "Weekly goal"), style = MaterialTheme.typography.titleMedium)
            Text(tr("${goal.target} buổi mỗi tuần", "${goal.target} sessions per week"), color = p.accent)
            Text(tr("Chưa cập nhật • Chưa kết nối lịch sử tập luyện.", "Not updated • Workout history is not connected."), color = p.muted)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Mục tiêu tuần", "Weekly goal"), color = p.muted, style = MaterialTheme.typography.labelMedium)
            Text("${goal.completed} / ${goal.target}", color = p.text,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("workout_weekly_goal_count"))
            Text(tr("buổi đã hoàn thành", "workouts completed"), color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.secondary, trackColor = p.border, strokeWidth = 5.dp)
            Text("${goal.percentage}%", color = p.text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun WorkoutBadge(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = LocalProfilePalette.current.tint, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = LocalProfilePalette.current.accent,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun WorkoutStatusBadge(status: SessionStatus) {
    val c = MaterialTheme.colorScheme
    val background = when (status) {
        SessionStatus.Confirmed -> c.tertiaryContainer
        SessionStatus.Booked -> c.secondaryContainer
        SessionStatus.Planned -> c.primaryContainer
    }
    val foreground = when (status) {
        SessionStatus.Confirmed -> c.onTertiaryContainer
        SessionStatus.Booked -> c.onSecondaryContainer
        SessionStatus.Planned -> c.onPrimaryContainer
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(8.dp)) {
        Text(statusLabel(status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun WorkoutHero(workout: WorkoutSummaryUiModel, eyebrow: String, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = p.card, contentColor = p.text,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag("hero_${workout.id}")) {
        Column {
            Box(Modifier.fillMaxWidth().height(156.dp)) {
                Image(painterResource(workout.thumbnailRes), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Surface(modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color(0xDD111723), contentColor = Color.White, shape = RoundedCornerShape(10.dp)) {
                    Text(eyebrow, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(workout.title.localized(), style = MaterialTheme.typography.titleLarge)
                    Text("${workout.durationMinutes} ${tr("phút", "min")} · ${workout.exerciseCount} ${tr("bài", "exercises")}",
                        color = p.muted, style = MaterialTheme.typography.bodySmall)
                    Text(workout.level.label.localized(), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.home_icon_arrow), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkoutTile(workout: WorkoutSummaryUiModel, subtitle: String? = null,
    tag: String = "session_${workout.id}", status: SessionStatus? = null, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = p.card, contentColor = p.text,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp).testTag(tag)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(workout.thumbnailRes), null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(workout.title.localized(), style = MaterialTheme.typography.titleSmall)
                Text("${workout.durationMinutes} ${tr("phút", "min")} · ${workout.exerciseCount} ${tr("bài", "exercises")}",
                    style = MaterialTheme.typography.bodySmall, color = p.muted)
                Text(subtitle ?: workout.level.label.localized(), style = MaterialTheme.typography.bodySmall, color = p.muted)
                if (status != null) WorkoutStatusBadge(status)
            }
            Icon(painterResource(R.drawable.home_icon_arrow), null, Modifier.size(18.dp), tint = p.accent)
        }
    }
}

@Composable
internal fun ExerciseTile(exercise: ExerciseUiModel, tag: String, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).testTag(tag),
        shape = RoundedCornerShape(20.dp), color = p.card, contentColor = p.text, border = BorderStroke(1.dp, p.border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(exercise.thumbnailRes), null, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(exercise.title.localized(), style = MaterialTheme.typography.titleSmall)
                Text("${exercise.sets} × ${exercise.reps} · ${exercise.muscleGroup.localized()}",
                    style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Icon(painterResource(R.drawable.home_icon_arrow), null, Modifier.size(18.dp), tint = p.accent)
        }
    }
}

@Composable internal fun statusLabel(status: SessionStatus) = when(status) {
    SessionStatus.Planned -> tr("Sắp tới", "Upcoming")
    SessionStatus.Booked -> tr("Đã đặt lịch", "Booked")
    SessionStatus.Confirmed -> tr("Hoàn thành", "Completed")
}
