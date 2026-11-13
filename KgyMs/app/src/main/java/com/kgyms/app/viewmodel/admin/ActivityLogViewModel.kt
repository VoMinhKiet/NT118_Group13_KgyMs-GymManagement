package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.ActivityLog
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
    val action: String,
    /** Vd "10:24 • hôm nay", "21:05 • hôm qua", "09:00 • 05/11/2026". */
    val timeLabel: String,
    /** Chuỗi gốc "HH:mm dd/MM/yyyy" để tìm kiếm theo ngày. */
    val createdAt: String
)

data class ActivityLogUiState(
    val logs: List<ActivityLogRow> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class ActivityLogViewModel(
    private val repository: ActivityLogRepository = ActivityLogRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityLogUiState())
    val state: StateFlow<ActivityLogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLogs()
                .catch { error ->
                    val raw = error.message.orEmpty()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (raw.contains("permission", ignoreCase = true)) {
                                "Bạn không có quyền xem nhật ký (cần tài khoản admin)."
                            } else {
                                raw.ifBlank { "Không tải được nhật ký." }
                            }
                        )
                    }
                }
                .collect { logs ->
                    _state.update {
                        it.copy(logs = logs.toRows(), isLoading = false, errorMessage = null)
                    }
                }
        }
    }

    /** Mới nhất lên trước. createdAt là chuỗi nên phải đổi sang thời gian rồi mới sắp. */
    private fun List<ActivityLog>.toRows(): List<ActivityLogRow> {
        val parser = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }
        val dayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

        val today = dayFormat.format(Calendar.getInstance().time)
        val yesterday = dayFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
        )

        return map { log -> log to runCatching { parser.parse(log.createdAt)?.time }.getOrNull() }
            .sortedByDescending { (_, millis) -> millis ?: Long.MIN_VALUE }
            .map { (log, _) ->
                val hour = log.createdAt.substringBefore(" ")
                val day = log.createdAt.substringAfter(" ", "")
                val dayLabel = when (day) {
                    today -> "hôm nay"
                    yesterday -> "hôm qua"
                    else -> day
                }

                ActivityLogRow(
                    id = log.id,
                    title = log.title,
                    action = log.action,
                    timeLabel = if (day.isBlank()) log.createdAt else "$hour • $dayLabel",
                    createdAt = log.createdAt
                )
            }
    }
}
