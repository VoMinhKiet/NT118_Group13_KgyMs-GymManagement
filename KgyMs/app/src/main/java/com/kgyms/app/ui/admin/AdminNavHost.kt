package com.kgyms.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kgyms.app.ui.admin.screens.DashboardScreen


@Composable
fun AdminNavHost() {

    var currentRoute by remember {
        mutableStateOf<AdminRoute>(
            AdminRoute.Dashboard
        )
    }

    when (currentRoute) {

        AdminRoute.Dashboard -> {
            DashboardScreen()
        }

        else -> {
            DashboardScreen()
        }
    }
}