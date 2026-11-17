package com.kgyms.app.ui.member.progress

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.profileText as tr
import kotlinx.coroutines.launch

@Composable
internal fun WorkoutHistorySyncNotice(state:WorkoutHistoryState) {
    val scope=rememberCoroutineScope()
    if(state.loading || state.failed)return
    val pending=state.archive.pending.isNotEmpty()
    val text=when(state.sync) {
        HistorySyncState.LocalOnly, HistorySyncState.Syncing, HistorySyncState.Synced -> null
        HistorySyncState.Pending -> if(pending)tr("Kết quả đã được giữ trên máy, sẽ lưu vào tài khoản khi có kết nối.", "Your results are kept on this device and will be saved to your account when connected.") else null
        HistorySyncState.AccessDenied, HistorySyncState.Error -> if(pending)
            tr("Chưa lưu được vào tài khoản. Kết quả trên máy vẫn được giữ lại.", "Could not save to your account. Your results are safe on this device.")
            else tr("Chưa tải được lịch sử tập luyện. Hãy thử lại.", "Could not load your workout history. Please retry.")
        HistorySyncState.Conflict -> tr("Một số kết quả chưa khớp. Dữ liệu vẫn được giữ nguyên; hãy thử lại hoặc liên hệ hỗ trợ.", "Some results differ. Your data is retained; retry or contact support.")
    }
    if(text!=null)Text(text,Modifier.testTag("history_sync_status"),style=MaterialTheme.typography.bodySmall,color=LocalProfilePalette.current.muted)
    if(state.sync in listOf(HistorySyncState.AccessDenied,HistorySyncState.Error,HistorySyncState.Conflict))
        TextButton(onClick={scope.launch{state.retry()}},modifier=Modifier.heightIn(min=48.dp).testTag("history_retry")) {Text(tr("Thử lại","Retry"))}
}

@Composable
internal fun WorkoutHistorySummaryContent(state:WorkoutHistoryState,target:Int,now:Long,goalTag:String="history_weekly_target") {
    val summary=remember(state.archive,target,now){workoutHistorySummary(state.archive,target,now)}
    val palette=LocalProfilePalette.current
    Text(tr("Mục tiêu tuần", "Weekly goal"),style=MaterialTheme.typography.titleMedium)
    Text(if(target>0)tr("$target buổi mỗi tuần","$target sessions per week") else tr("Chọn mục tiêu hoặc tạo lịch tập","Choose a goal or create a plan"),Modifier.testTag(goalTag),color=palette.accent)
    if(target>0 && LocalMemberPreferences.current?.weeklyTargetOverridden==true)Text(tr("Mục tiêu riêng của bạn","Your custom goal"),style=MaterialTheme.typography.bodySmall,color=palette.muted)
    when {
        state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        state.failed -> {
            val scope=rememberCoroutineScope()
            Text(tr("Chưa tải được kết quả tập luyện.","Could not load your workout results."),color=palette.danger)
            TextButton(onClick={scope.launch{state.retry()}},modifier=Modifier.heightIn(min=48.dp).testTag("history_retry")){Text(tr("Thử lại","Retry"))}
        }
        else -> {
            Text(tr("Tiến độ tuần này","This week's progress"),style=MaterialTheme.typography.titleSmall)
            if(summary.unique==null)Text(tr("Chưa có số liệu. Kết quả sẽ hiện khi tải được lịch sử.","Results will appear once your history is available."),Modifier.testTag("workout_history_unavailable"),color=palette.muted)
            else Column(Modifier.fillMaxWidth().testTag("progress_completed"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(target>0)"${summary.unique} / $target" else "${summary.unique}",Modifier.fillMaxWidth().testTag("history_reported_unique"),style=MaterialTheme.typography.headlineSmall)
                Text(tr("buổi hoàn thành","sessions completed"),Modifier.fillMaxWidth().testTag("history_completed_label"),style=MaterialTheme.typography.bodyMedium,color=palette.muted)
            }
            if(summary.unique!=null && target>0)LinearProgressIndicator(progress={summary.progress},modifier=Modifier.fillMaxWidth())
        }
    }
    WorkoutHistorySyncNotice(state)
}
