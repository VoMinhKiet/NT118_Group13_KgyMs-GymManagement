package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.ActivityLog
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.repository.ActivityLogRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Một dòng nhật ký đã sẵn sàng hiển thị. */
data class ActivityLogRow(
    val id: String,
    val title: String,
    /** Vd "10:24 • hôm nay • Tài sản". */
    val subtitle: String,
    val tone: AdminTone
)

data class ActivityLogUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val query: String = "",
    val selectedFilter: String = FILTER_ALL,
    /** Chip lọc kèm số lượng. */
    val filters: List<OptionUi> = emptyList(),
    /** Đã lọc theo chip + từ khóa. */
    val rows: List<ActivityLogRow> = emptyList(),
    val totalCount: Int = 0
) {
    val emptyMessage: String?
        get() = when {
            isLoading || errorMessage != null -> null
            totalCount == 0 -> "Chưa có hoạt động nào được ghi lại."
            rows.isEmpty() -> "Không có hoạt động phù hợp bộ lọc."
            else -> null
        }

    companion object {
        const val FILTER_ALL = ""
    }
}

class ActivityLogViewModel(
    private val repository: ActivityLogRepository = ActivityLogRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityLogUiState())
    val state: StateFlow<ActivityLogUiState> = _state.asStateFlow()

    /** Toàn bộ nhật ký đã sắp xếp, kèm chuỗi để tìm kiếm. */
    private var allLogs: List<Pair<ActivityLog, String>> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeLogs()
                .catch { error ->
                    val raw = error.message.orEmpty()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (raw.contains("permission", ignoreCase = true))
                                "Bạn không có quyền xem nhật ký (cần tài khoản admin)."
                            else raw.ifBlank { "Không tải được nhật ký." }
                        )
                    }
                }
                .collect { logs ->
                    allLogs = logs.sortedNewestFirst().map { it to searchText(it) }
                    _state.update { recompute(it.copy(isLoading = false, errorMessage = null)) }
                }
        }
    }

    fun onQueryChange(query: String) = _state.update { recompute(it.copy(query = query)) }

    fun onFilterSelected(key: String) = _state.update { recompute(it.copy(selectedFilter = key)) }

    private fun recompute(state: ActivityLogUiState): ActivityLogUiState {
        val keyword = state.query.trim()
        val rows = allLogs
            .filter { (log, _) -> state.selectedFilter == ActivityLogUiState.FILTER_ALL || log.action == state.selectedFilter }
            .filter { (_, text) -> keyword.isEmpty() || text.contains(keyword, ignoreCase = true) }
            .map { (log, _) -> log.toRow() }

        val filters = listOf(OptionUi(ActivityLogUiState.FILTER_ALL, "Tất cả (${allLogs.size})")) +
            ActivityLogAction.all.map { action ->
                OptionUi(action, "${actionLabel(action)} (${allLogs.count { it.first.action == action }})")
            }

        return state.copy(rows = rows, filters = filters, totalCount = allLogs.size)
    }

    private fun ActivityLog.toRow() = ActivityLogRow(
        id = id,
        title = title,
        subtitle = "${timeLabel(createdAt)} • ${actionLabel(action)}",
        tone = actionTone(action)
    )

    private fun searchText(log: ActivityLog) =
        "${log.title} ${log.createdAt} ${timeLabel(log.createdAt)} ${actionLabel(log.action)}"

    private fun List<ActivityLog>.sortedNewestFirst(): List<ActivityLog> {
        val parser = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }
        return sortedByDescending { runCatching { parser.parse(it.createdAt)?.time }.getOrNull() ?: Long.MIN_VALUE }
    }

    /** "10:24 05/11/2026" → "10:24 • hôm nay" / "hôm qua" / ngày cụ thể. */
    private fun timeLabel(createdAt: String): String {
        val hour = createdAt.substringBefore(" ")
        val day = createdAt.substringAfter(" ", "")
        if (day.isBlank()) return createdAt

        val format = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        val today = format.format(Calendar.getInstance().time)
        val yesterday = format.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        val dayLabel = when (day) {
            today -> "hôm nay"
            yesterday -> "hôm qua"
            else -> day
        }
        return "$hour • $dayLabel"
    }

    private fun actionLabel(action: String) = when (action) {
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

    private fun actionTone(action: String) = when (action) {
        ActivityLogAction.ACCOUNT -> AdminTone.Info
        ActivityLogAction.ASSET -> AdminTone.Warning
        ActivityLogAction.MEMBERSHIP, ActivityLogAction.PACKAGE -> AdminTone.Brand
        ActivityLogAction.PAYMENT, ActivityLogAction.NOTIFICATION -> AdminTone.Positive
        ActivityLogAction.CHECK_IN, ActivityLogAction.WORKOUT -> AdminTone.Critical
        else -> AdminTone.Neutral
    }
}
