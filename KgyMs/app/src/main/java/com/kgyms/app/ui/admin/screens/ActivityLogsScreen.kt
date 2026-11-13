package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminSearchInput
import com.kgyms.app.viewmodel.admin.ActivityLogRow
import com.kgyms.app.viewmodel.admin.ActivityLogViewModel

/** Chip "Tất cả" dùng key rỗng. */
private const val FILTER_ALL = ""

@Composable
fun ActivityLogsScreen(
    activityLogViewModel: ActivityLogViewModel = viewModel()
) {
    val state by activityLogViewModel.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var actionFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val visibleLogs = state.logs.filter { log ->
        val matchesAction = actionFilter == FILTER_ALL || log.action == actionFilter
        val keyword = query.trim()
        val matchesQuery = keyword.isEmpty() ||
            "${log.title} ${log.timeLabel} ${log.createdAt} ${actionLabel(log.action)}"
                .contains(keyword, ignoreCase = true)
        matchesAction && matchesQuery
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

            /*
             * ====================================================
             * TÌM KIẾM
             * ====================================================
             */

            Text(text = "Bộ lọc", color = AdminColors.TextSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(5.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AdminColors.Surface)
                    .border(1.dp, AdminColors.Border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AdminSearchInput(query, { query = it }, "Người dùng, hành động, ngày", "admin_log_search")
            }

            Spacer(modifier = Modifier.height(8.dp))

            /*
             * ====================================================
             * CHIP LỌC THEO LOẠI
             * ====================================================
             */

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (listOf(FILTER_ALL) + ActivityLogAction.all).forEach { action ->
                    val count = if (action == FILTER_ALL) state.logs.size
                    else state.logs.count { it.action == action }

                    LogFilterChip(
                        label = "${if (action == FILTER_ALL) "Tất cả" else actionLabel(action)} ($count)",
                        selected = actionFilter == action,
                        onClick = { actionFilter = action },
                        tag = "admin_log_filter_${action.ifBlank { "all" }}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            /*
             * ====================================================
             * DANH SÁCH NHẬT KÝ
             * ====================================================
             */

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AdminColors.Primary)
                    }
                }

                state.errorMessage != null -> LogInfoText(state.errorMessage.orEmpty(), AdminColors.Pink)

                state.logs.isEmpty() -> LogInfoText("Chưa có hoạt động nào được ghi lại.", AdminColors.TextSecondary)

                visibleLogs.isEmpty() -> LogInfoText("Không có hoạt động phù hợp bộ lọc.", AdminColors.TextSecondary)

                else -> visibleLogs.forEach { log ->
                    ActivityLogItemView(log = log)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/*
 * ================================================================
 * NHÃN / MÀU / ICON THEO LOẠI HÀNH ĐỘNG
 * ================================================================
 */

private fun actionLabel(action: String): String = when (action) {
    ActivityLogAction.ACCOUNT -> "Tài khoản"
    ActivityLogAction.ASSET -> "Tài sản"
    ActivityLogAction.MEMBERSHIP -> "Hội viên"
    ActivityLogAction.PACKAGE -> "Gói tập"
    ActivityLogAction.PAYMENT -> "Thanh toán"
    ActivityLogAction.CHECK_IN -> "Check-in"
    ActivityLogAction.WORKOUT -> "Buổi tập"
    ActivityLogAction.NOTIFICATION -> "Thông báo"
    else -> "Khác"
}

private fun actionIcon(action: String): String = when (action) {
    ActivityLogAction.ACCOUNT -> "👤"
    ActivityLogAction.ASSET -> "🛠"
    ActivityLogAction.MEMBERSHIP -> "🪪"
    ActivityLogAction.PACKAGE -> "📦"
    ActivityLogAction.PAYMENT -> "💳"
    ActivityLogAction.CHECK_IN -> "📍"
    ActivityLogAction.WORKOUT -> "🏋"
    ActivityLogAction.NOTIFICATION -> "🔔"
    else -> "•"
}

private fun actionColor(action: String): Color = when (action) {
    ActivityLogAction.ACCOUNT -> AdminColors.Cyan
    ActivityLogAction.ASSET -> AdminColors.Yellow
    ActivityLogAction.MEMBERSHIP, ActivityLogAction.PACKAGE -> AdminColors.PrimaryLight
    ActivityLogAction.PAYMENT, ActivityLogAction.NOTIFICATION -> AdminColors.Green
    ActivityLogAction.CHECK_IN, ActivityLogAction.WORKOUT -> AdminColors.Pink
    else -> AdminColors.TextSecondary
}

/*
 * ================================================================
 * COMPONENTS
 * ================================================================
 */

@Composable
private fun LogFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AdminColors.Primary else AdminColors.Surface)
            .border(
                1.dp,
                if (selected) AdminColors.Primary else AdminColors.Border,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) AdminColors.OnPrimary else AdminColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun LogInfoText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 8.dp)
    )
}

/** Bấm vào dòng để xem đầy đủ nội dung nếu bị cắt. */
@Composable
private fun ActivityLogItemView(log: ActivityLogRow) {
    var expanded by remember { mutableStateOf(false) }
    val color = actionColor(log.action)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = actionIcon(log.action), fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                color = AdminColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${log.timeLabel} • ${actionLabel(log.action)}",
                color = AdminColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
