package com.kgyms.app.ui.admin

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdminDashboardUiState(
    val revenue: String = "128.5M",
    val members: String = "1,248",
    val checkIn: String = "892",
    val retention: String = "87%"
)

class AdminViewModel : ViewModel() {

    private val _dashboardState = MutableStateFlow(
        AdminDashboardUiState()
    )

    val dashboardState: StateFlow<AdminDashboardUiState> =
        _dashboardState.asStateFlow()
}