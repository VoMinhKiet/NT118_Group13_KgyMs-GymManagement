package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminSearchBox
import com.kgyms.app.viewmodel.admin.ActivityLogViewModel
import com.kgyms.app.viewmodel.admin.AdminTone

@Composable
fun ActivityLogsScreen(
    viewModel: ActivityLogViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    AdminPage {
        AdminLabel("Tìm kiếm")
        Spacer(modifier = Modifier.height(6.dp))
        AdminSearchBox(state.query, viewModel::onQueryChange, "Người dùng, hành động, ngày", "admin_log_search")

        Spacer(modifier = Modifier.height(10.dp))
        AdminChipGroup(
            options = state.filters,
            isSelected = { it == state.selectedFilter },
            onSelect = viewModel::onFilterSelected,
            tagPrefix = "admin_log_filter"
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.emptyMessage != null -> AdminMessage(state.emptyMessage.orEmpty())
            else -> state.rows.forEach { row ->
                AdminListRow(title = row.title, subtitle = row.subtitle, tone = row.tone)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
