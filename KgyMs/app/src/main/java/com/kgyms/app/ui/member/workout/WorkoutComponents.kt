package com.kgyms.app.ui.member.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kgyms.app.R
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.data.model.ExerciseUiModel
import com.kgyms.app.data.model.SessionStatus
import com.kgyms.app.data.model.WorkoutSummaryUiModel
import com.kgyms.app.data.model.WorkoutText

@Composable internal fun WorkoutText.localized() = inLanguage(LocalProfileLanguage.current)
@Composable internal fun WorkoutPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProfilePalette.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = p.card, border = BorderStroke(1.dp, p.border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable internal fun WorkoutBadge(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = LocalProfilePalette.current.tint, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = LocalProfilePalette.current.accent, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable internal fun WorkoutHero(workout: WorkoutSummaryUiModel, eyebrow: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().testTag("hero_${workout.id}")) {
        Box(Modifier.heightIn(min = 246.dp)) {
            Image(painterResource(workout.thumbnailRes), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .15f), Color(0xFF111320).copy(alpha = .98f)))))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFFE2EBAD), shape = RoundedCornerShape(10.dp)) {
                    Text(eyebrow, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color(0xFF292D16), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(40.dp))
                Text(workout.title.localized(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${workout.durationMinutes} ${tr("phút", "min")}  ·  ${workout.exerciseCount} ${tr("bài", "exercises")}", color = Color(0xFFDFDBE9))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(workout.level.label.localized(), color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Surface(shape = CircleShape, color = LocalProfilePalette.current.accent) {
                        Icon(painterResource(R.drawable.home_icon_play), tr("Xem buổi tập", "View workout"), Modifier.padding(12.dp).size(22.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}
@Composable internal fun WorkoutTile(workout: WorkoutSummaryUiModel, subtitle: String? = null, tag: String = "session_${workout.id}", onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = p.card, border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(workout.thumbnailRes), null, Modifier.size(82.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(workout.title.localized(), style = MaterialTheme.typography.titleSmall)
                Text("${workout.durationMinutes} ${tr("phút", "min")} · ${workout.exerciseCount} ${tr("bài", "exercises")}", style = MaterialTheme.typography.bodySmall, color = p.muted)
                Text(subtitle ?: workout.level.label.localized(), style = MaterialTheme.typography.labelSmall, color = p.accent)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = p.muted)
        }
    }
}
@Composable internal fun ExerciseTile(exercise: ExerciseUiModel, tag: String, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(20.dp), color = p.card, border = BorderStroke(1.dp, p.border)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(exercise.thumbnailRes), null, Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(exercise.title.localized(), style = MaterialTheme.typography.titleSmall)
                Text("${exercise.sets} × ${exercise.reps} · ${exercise.muscleGroup.localized()}", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Icon(painterResource(R.drawable.home_icon_play), tr("Xem hướng dẫn", "View guide"), Modifier.size(20.dp), tint = p.accent)
        }
    }
}
@Composable internal fun statusLabel(status: SessionStatus) = when(status) {
    SessionStatus.Planned -> tr("Sắp tới", "Upcoming")
    SessionStatus.Booked -> tr("Đã đặt lịch", "Booked")
    SessionStatus.Confirmed -> tr("Hoàn thành", "Completed")
}
