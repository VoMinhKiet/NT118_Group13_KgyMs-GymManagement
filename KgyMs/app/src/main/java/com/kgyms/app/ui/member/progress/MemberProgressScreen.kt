package com.kgyms.app.ui.member.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.repository.MemberWorkoutHistorySource
import com.kgyms.app.data.repository.UnconnectedWorkoutHistory
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfileCard
import com.kgyms.app.ui.member.profile.profileText as tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private sealed interface HistoryState {
    data object Unavailable : HistoryState
    data object Loading : HistoryState
    data object Failed : HistoryState
    data class Available(val records: List<WorkoutRecord>) : HistoryState
}

@Composable
internal fun MemberProgressScreen(
    uid: String?,
    weeklyTarget: Int,
    onHome: () -> Unit,
    onPlan: () -> Unit,
    historySource: MemberWorkoutHistorySource = UnconnectedWorkoutHistory,
) {
    key(uid) {
        val shared=LocalWorkoutHistory.current.takeIf { historySource === UnconnectedWorkoutHistory }
        var retry by remember(historySource) { mutableIntStateOf(0) }
        val history = if(shared!=null) {
            when {
                shared.loading -> HistoryState.Loading
                shared.failed -> HistoryState.Failed
                shared.archive.remoteKnown || shared.archive.attempts.isNotEmpty() -> HistoryState.Available(emptyList())
                else -> HistoryState.Unavailable
            }
        } else if (uid.isNullOrBlank() || historySource === UnconnectedWorkoutHistory) {
            HistoryState.Unavailable
        } else key(historySource, retry) {
            val state by produceState<HistoryState>(HistoryState.Loading, uid, historySource) {
                value = try {
                    historySource.load(uid)?.let { HistoryState.Available(it) } ?: HistoryState.Unavailable
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    HistoryState.Failed
                }
            }
            state
        }
        val now by produceState(System.currentTimeMillis()) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000)
            }
        }
        val progress = remember(weeklyTarget, history, now) {
            memberProgressData(weeklyTarget, (history as? HistoryState.Available)?.records, now)
        }

        MemberDashboardAppearance {
            val palette = LocalProfilePalette.current
            val language = LocalProfileLanguage.current
            val date = remember(language) {
                DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag(if (language == "vi") "vi-VN" else "en"))
            }
            Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().safeDrawingPadding()
                        .testTag("member_screen_progress"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item("title") {
                        Text(tr("Tiến độ tập luyện", "Workout progress"), style = MaterialTheme.typography.headlineSmall, color = palette.text)
                    }
                    if(shared==null)item("weekly_goal") {
                        ProfileCard {
                            Text(tr("Mục tiêu tuần", "Weekly goal"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if(progress.week.target>0)tr("${progress.week.target} buổi mỗi tuần", "${progress.week.target} sessions per week") else tr("Chọn mục tiêu hoặc tạo lịch tập","Choose a goal or create a plan"),
                                Modifier.testTag("progress_weekly_target"), color = palette.accent,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "${date.format(Date(progress.week.weekStartMillis))} – ${date.format(Date(progress.week.weekEndMillis))}",
                                color = palette.muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    item("completion") {
                        ProfileCard {
                            if(shared!=null)WorkoutHistorySummaryContent(shared,weeklyTarget,now,goalTag="progress_weekly_target")
                            else {
                            Text(tr("Hoàn thành tuần này", "Completed this week"), style = MaterialTheme.typography.titleMedium)
                            val completed = progress.completed
                            if (completed != null) {
                                Text("$completed / ${progress.week.target}", Modifier.testTag("progress_completed"),
                                    style = MaterialTheme.typography.titleLarge)
                                LinearProgressIndicator(
                                    progress = { progress.week.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(tr("Chưa có số liệu", "Completion unavailable"), color = palette.muted)
                            }
                            }
                        }
                    }
                    item("history") {
                        ProfileCard {
                            Text(tr("Lịch sử buổi tập", "Completed workouts"), style = MaterialTheme.typography.titleMedium)
                            when (history) {
                                HistoryState.Unavailable -> Text(
                                    tr("Chưa tải được lịch sử tập luyện", "Workout history is not available yet"),
                                    Modifier.testTag("progress_history_unavailable"), color = palette.muted,
                                )
                                HistoryState.Loading -> {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text(tr("Đang tải lịch sử…", "Loading history…"))
                                }
                                HistoryState.Failed -> {
                                    Text(tr("Không thể tải lịch sử tập luyện.", "Could not load workout history."), color = palette.danger)
                                    if(shared==null)TextButton(onClick = { retry++ }) { Text(tr("Thử lại", "Retry")) }
                                }
                                is HistoryState.Available -> if(shared?.archive?.attempts?.isNotEmpty()==true) {
                                    Text(tr("Các buổi bạn đã hoàn thành", "Workouts you have completed"), color=palette.muted)
                                } else if (progress.history.orEmpty().isEmpty()) {
                                    Text(tr("Chưa có dữ liệu tập luyện", "No workout history yet"), color = palette.muted)
                                } else {
                                    Text(tr("Các buổi bạn đã hoàn thành", "Workouts you have completed"), color = palette.muted)
                                }
                            }
                        }
                    }
                    items(if(shared==null)progress.history.orEmpty() else emptyList(), key = { "record:${it.sessionId}" }) { record ->
                        ProfileCard {
                            Text(date.format(Date(record.occurredAtMillis)), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Đã hoàn thành", "Completed"), color = palette.muted)
                        }
                    }
                    if(shared!=null) {
                        shared.archive.attempts.sortedByDescending{it.finishedAt}.groupBy { date.format(Date(it.finishedAt)) }.forEach { (day,attempts) ->
                            item("history-day:$day"){Text(day,style=MaterialTheme.typography.titleMedium)}
                            items(attempts,key={"attempt:${it.id}"}){attempt->
                                ProfileCard {
                                    Text(if(language=="vi")attempt.titleVi else attempt.titleEn,style=MaterialTheme.typography.titleMedium)
                                    Text(tr("Đã hoàn thành", "Completed"),Modifier.testTag("history_attempt_${attempt.id}"),color=palette.muted)
                                    Text("${attempt.exercises.count{it.performed}} / ${attempt.exercises.size} "+tr("bài đã tập","exercises done"),style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item("actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress_open_plan")) {
                                Text(tr("Mở Kế hoạch", "Open Plan"))
                            }
                            TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress_open_home")) {
                                Text(tr("Về Trang chủ", "Back to Home"))
                            }
                        }
                    }
                }
            }
        }
    }
}
