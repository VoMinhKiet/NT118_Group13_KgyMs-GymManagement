package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import com.kgyms.app.data.repository.AdminMockRepository

data class PackageRowUi(val id: String, val title: String, val statusLabel: String, val tone: AdminTone)

data class PackageUiState(
    val headline: String,
    val rows: List<PackageRowUi>
)

class PackageViewModel(
    repository: AdminMockRepository = AdminMockRepository()
) : ViewModel() {

    private val packages = repository.packages()

    val state = PackageUiState(
        headline = "${packages.count { it.isActive }} gói đang hoạt động",
        rows = packages.map {
            PackageRowUi(
                id = it.id,
                title = "${it.name} • ${AdminFormat.vnd(it.price)}",
                statusLabel = if (it.isActive) "Đang bán" else "Ngừng bán",
                tone = if (it.isActive) AdminTone.Positive else AdminTone.Neutral
            )
        }
    )
}
