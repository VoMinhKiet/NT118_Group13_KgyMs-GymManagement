package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import com.kgyms.app.data.repository.AdminMockRepository
import java.util.Locale

data class StaffRowUi(val id: String, val title: String, val subtitle: String, val tone: AdminTone)

data class StaffDetailUi(
    val role: String,
    val name: String,
    val headline: String,
    val fields: List<DetailFieldUi>
)

data class StaffUiState(
    val totalStaff: String,
    val trainerCount: String,
    val rows: List<StaffRowUi>
)

class StaffViewModel(
    repository: AdminMockRepository = AdminMockRepository()
) : ViewModel() {

    private val staff = repository.staff()

    val state = StaffUiState(
        totalStaff = staff.size.toString(),
        trainerCount = staff.count { it.isTrainer }.toString(),
        rows = staff.map {
            StaffRowUi(
                id = it.id,
                title = "${it.name} • ${it.role}",
                subtitle = (if (it.isActive) "Đang hoạt động" else "Tạm nghỉ") + " • Ca ${it.workShift}",
                tone = if (it.isActive) AdminTone.Positive else AdminTone.Neutral
            )
        }
    )

    fun detail(id: String): StaffDetailUi? = staff.firstOrNull { it.id == id }?.let { s ->
        StaffDetailUi(
            role = s.role.uppercase(),
            name = s.name,
            headline = "${s.specialty} • ${s.staffCode}",
            fields = buildList {
                add(DetailFieldUi("Số điện thoại", s.phone))
                add(DetailFieldUi("Ca làm", s.workShift))
                if (s.isTrainer) {
                    add(DetailFieldUi("Khách phụ trách", s.clients.toString()))
                    add(DetailFieldUi("Đánh giá", String.format(Locale.ROOT, "%.1f / 5", s.rating)))
                }
                add(
                    DetailFieldUi(
                        "Trạng thái",
                        if (s.isActive) "Đang hoạt động" else "Tạm nghỉ",
                        if (s.isActive) AdminTone.Positive else AdminTone.Neutral
                    )
                )
            }
        )
    }
}
