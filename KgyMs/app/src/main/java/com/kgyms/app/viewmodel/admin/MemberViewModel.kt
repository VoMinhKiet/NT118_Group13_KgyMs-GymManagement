package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import com.kgyms.app.data.model.AdminMember
import com.kgyms.app.data.model.MemberStatus
import com.kgyms.app.data.repository.AdminMockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MemberRowUi(val id: String, val name: String, val subtitle: String, val tone: AdminTone)

data class MemberDetailUi(
    val planLabel: String,
    val name: String,
    val codeLine: String,
    val fields: List<DetailFieldUi>
)

/** Một dòng "nhãn — giá trị" trên màn chi tiết. */
data class DetailFieldUi(val label: String, val value: String, val tone: AdminTone? = null)

data class MemberUiState(
    val query: String = "",
    val filter: String = MemberViewModel.FILTER_ALL,
    val filters: List<OptionUi> = listOf(
        OptionUi(MemberViewModel.FILTER_ALL, "Tất cả"),
        OptionUi(MemberViewModel.FILTER_ACTIVE, "Hoạt động"),
        OptionUi(MemberViewModel.FILTER_EXPIRING, "Sắp hết hạn")
    ),
    val rows: List<MemberRowUi> = emptyList()
)

class MemberViewModel(
    private val repository: AdminMockRepository = AdminMockRepository()
) : ViewModel() {

    private val members = repository.members()

    private val _state = MutableStateFlow(MemberUiState().recompute())
    val state: StateFlow<MemberUiState> = _state.asStateFlow()

    fun onQueryChange(query: String) = _state.update { it.copy(query = query).recompute() }

    fun onFilterSelected(filter: String) = _state.update { it.copy(filter = filter).recompute() }

    fun detail(id: String): MemberDetailUi? = members.firstOrNull { it.id == id }?.let { m ->
        MemberDetailUi(
            planLabel = m.planName.uppercase(),
            name = m.name,
            codeLine = "${m.memberCode} • ${if (m.status == MemberStatus.Active) "Hợp lệ" else "Sắp hết hạn"}",
            fields = listOf(
                DetailFieldUi("Hết hạn", m.expiresOn, if (m.status == MemberStatus.Expiring) AdminTone.Critical else null),
                DetailFieldUi("Check-in tháng này", m.checkInsThisMonth.toString()),
                DetailFieldUi("PT phụ trách", m.trainerName),
                DetailFieldUi("InBody gần nhất", m.lastInBody),
                DetailFieldUi("Công nợ", AdminFormat.vnd(m.debt), if (m.debt > 0) AdminTone.Warning else null)
            )
        )
    }

    private fun MemberUiState.recompute(): MemberUiState {
        val keyword = query.trim()
        return copy(
            rows = members
                .filter {
                    when (filter) {
                        FILTER_ACTIVE -> it.status == MemberStatus.Active
                        FILTER_EXPIRING -> it.status == MemberStatus.Expiring
                        else -> true
                    }
                }
                .map { it.toRow() }
                .filter { keyword.isEmpty() || "${it.name} ${it.subtitle}".contains(keyword, ignoreCase = true) }
        )
    }

    private fun AdminMember.toRow() = MemberRowUi(
        id = id,
        name = name,
        subtitle = "$planName • $memberCode • " +
            if (status == MemberStatus.Active) "Hoạt động" else "Còn $daysLeft ngày",
        tone = if (status == MemberStatus.Active) AdminTone.Positive else AdminTone.Warning
    )

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_ACTIVE = "active"
        const val FILTER_EXPIRING = "expiring"
    }
}
