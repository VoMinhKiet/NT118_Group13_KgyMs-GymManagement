package com.kgyms.app.ui.member.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.model.HistorySyncState
import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.repository.MemberWorkoutHistorySource
import com.kgyms.app.data.repository.UnconnectedWorkoutHistory
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.plan.LocalPersonalPlans
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfileCard
import com.kgyms.app.ui.member.profile.profileText as tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class LegacyRead(val load: ProgressLoad, val records: List<WorkoutRecord>? = null)

@Composable
internal fun MemberProgressScreen(
    uid: String?,
    weeklyTarget: Int,
    onHome: () -> Unit,
    onPlan: () -> Unit,
    historySource: MemberWorkoutHistorySource = UnconnectedWorkoutHistory,
    onCreatePlan: () -> Unit = onPlan,
) {
    key(uid) {
        val shared = LocalWorkoutHistory.current?.takeIf { it.uid == uid && historySource === UnconnectedWorkoutHistory }
        val plans = LocalPersonalPlans.current?.takeIf { it.uid == uid }
        val scope = rememberCoroutineScope()
        var retry by remember(historySource) { mutableIntStateOf(0) }
        val legacy = if (!uid.isNullOrBlank() && historySource !== UnconnectedWorkoutHistory) {
            key(historySource, retry) {
                val result by produceState(LegacyRead(ProgressLoad.Loading), uid, historySource) {
                    value = try {
                        historySource.load(uid)?.let { LegacyRead(ProgressLoad.Ready, it) } ?: LegacyRead(ProgressLoad.Unavailable)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        LegacyRead(ProgressLoad.Failed)
                    }
                }
                result
            }
        } else null
        val now by produceState(System.currentTimeMillis()) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000)
            }
        }
        val zone = TimeZone.getDefault()
        val archive = shared?.archive
        val history = remember(uid, shared, archive, shared?.loading, shared?.failed, legacy, now) {
            if (legacy != null) ProgressHistory(legacy.load)
            else progressHistory(uid, shared?.uid, archive, now, shared?.loading == true, shared?.failed == true)
        }
        val legacyRecords = remember(legacy, now) { memberProgressData(weeklyTarget, legacy?.records, now).history }
        val stats = remember(history, weeklyTarget, now, zone, legacyRecords) {
            progressAnalytics(history, weeklyTarget, now, zone, legacyRecords = legacyRecords)
        }
        val planArchive = plans?.archive
        val planReady = plans != null && !plans.loading && !plans.failed
        val plan = remember(uid, planArchive, planReady, history, now) {
            planArchive?.takeIf { planReady }?.let { activePlanProgress(uid, it, history, now) }
        }
        var selectedAttempt by rememberSaveable { mutableStateOf<String?>(null) }
        val ownedArchive = history.archive
        val attempts = ownedArchive?.attempts.orEmpty()
        val connected = shared?.repository?.remote?.available?.collectAsState()?.value

        MemberDashboardAppearance {
            val palette = LocalProfilePalette.current
            val language = LocalProfileLanguage.current
            val locale = if (language == "vi") Locale.forLanguageTag("vi-VN") else Locale.ENGLISH
            val date = remember(locale, zone) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone } }
            Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().safeDrawingPadding().testTag("member_screen_progress"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item("title") {
                        Text(tr("Tiến độ tập luyện", "Workout progress"), style = MaterialTheme.typography.headlineSmall, color = palette.text)
                    }
                    item("weekly_goal") { ProgressWeeklyCard(stats, date, history.load) }
                    item("activity") { ProgressActivityCard(stats.weeks, locale, zone) }
                    item("overview") { ProgressOverviewCard(stats) }
                    item("plan") {
                        ProgressPlanCard(
                            progress = plan,
                            version = plan?.let { plans?.versionNumber(it.plan.id) },
                            loading = plans?.loading == true,
                            unavailable = uid != null && (plans == null || plans.failed || plans.remoteNotChecked),
                            hasSavedPlans = planArchive?.plans?.any { it.uid == uid } == true,
                            onPlan = onPlan,
                            onCreatePlan = onCreatePlan,
                            locale = locale,
                        )
                    }
                    item("history") {
                        ProfileCard {
                            Text(tr("Lịch sử tập luyện", "Workout history"), style = MaterialTheme.typography.titleMedium)
                            when (history.load) {
                                ProgressLoad.Unavailable -> Text(
                                    tr("Chưa tải được lịch sử tập luyện", "Workout history is not available yet"),
                                    Modifier.testTag("progress_history_unavailable"), color = palette.muted,
                                )
                                ProgressLoad.Loading -> {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text(tr("Đang tải lịch sử…", "Loading history…"))
                                }
                                ProgressLoad.Failed -> {
                                    Text(tr("Không thể tải lịch sử tập luyện.", "Could not load workout history."), color = palette.danger)
                                    TextButton(onClick = { if (shared != null) scope.launch { shared.retry() } else retry++ },
                                        modifier = Modifier.testTag("history_retry")) { Text(tr("Thử lại", "Retry")) }
                                }
                                ProgressLoad.Ready -> if (attempts.isEmpty() && legacyRecords.orEmpty().isEmpty()) {
                                    Text(tr("Chưa có dữ liệu tập luyện", "No workout history yet"), Modifier.testTag("progress_history_empty"), color = palette.muted)
                                }
                            }
                            if (history.archive?.remoteKnown == false) {
                                Text(tr("Số liệu dựa trên kết quả hiện có trên thiết bị.", "Counts reflect the results available on this device."),
                                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (shared != null && !shared.loading && !shared.failed) {
                                if (connected == false) Text(tr("Ngoại tuyến", "Offline"), Modifier.testTag("progress_offline"), color = palette.muted)
                                if (shared?.sync == HistorySyncState.Syncing) Text(tr("Đang cập nhật lịch sử…", "Updating history…"), color = palette.muted)
                            }
                            if (shared != null) WorkoutHistorySyncNotice(shared)
                        }
                    }
                    items(attempts, key = { "attempt:${it.id}" }) { attempt ->
                        ProgressAttemptCard(attempt, checkNotNull(ownedArchive).state(attempt), date, language) {
                            selectedAttempt = attempt.id
                        }
                    }
                    items(legacyRecords.orEmpty(), key = { "record:${it.sessionId}" }) { record ->
                        ProfileCard {
                            Text(date.format(Date(record.occurredAtMillis)), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Đã hoàn thành", "Completed"), color = palette.muted)
                        }
                    }
                    item("home") {
                        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress_open_home")) {
                            Text(tr("Về Trang chủ", "Back to Home"))
                        }
                    }
                }
            }
            val attempt = attempts.firstOrNull { it.id == selectedAttempt }
            if (attempt != null && ownedArchive != null) {
                ProgressAttemptDetails(attempt, ownedArchive.state(attempt), date, language) { selectedAttempt = null }
            }
        }
    }
}
